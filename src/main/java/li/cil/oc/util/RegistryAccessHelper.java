package li.cil.oc.util;

import net.minecraft.core.RegistryAccess;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 为「没有注册表上下文」的物品编解码提供真实的注册表访问器。
 *
 * <p>NeoForge 1.21.1 的 {@code ItemStack#save(HolderLookup.Provider, ...)} 与
 * {@code ItemStack#parseOptional(HolderLookup.Provider, ...)} 都要先
 * {@code registries.getOrThrow(Registries.ITEM)} 才能把物品写成 {@code id} 字段。
 * 如果传 {@link RegistryAccess#EMPTY}，这一步拿不到注册表，结果是<b>每个物品都被静默写成
 * 空标签，读档时全部变成空气</b>（表现为「机箱里的物品全没了」）。
 *
 * <p>取值顺序：当前服务端注册表 → 客户端当前世界的注册表 → 上一次成功拿到的（缓存）。
 * 三条路都取不到时 {@link #getOrEmpty()} 退回空访问器并<b>只告警一次</b>。
 *
 * <p><b>为什么放在 Java 侧</b>：{@code li.cil.oc.util} 是 Scala 增量编译集，不能出现对
 * {@code net.minecraft.client} 的编译期引用。Java 侧独立编译没有这个限制；专服上
 * {@code net.minecraft.client.Minecraft} 根本不存在，但本类只在 {@link Dist#CLIENT} 分支上
 * 才会静态引用它（见内部类 {@code ClientRegistryAccess}），因此专服加载本类是安全的。
 */
public final class RegistryAccessHelper {

    private static final Logger LOGGER = LogManager.getLogger("OpenComputers Neo");

    /** 上一次成功拿到的访问器；用于「服务端尚未启动、客户端也还没有世界」的短暂窗口。 */
    private static volatile RegistryAccess cached;

    private static boolean warned;

    private RegistryAccessHelper() {
    }

    /** 取一个可用于物品编解码的真实注册表访问器；取不到时返回 {@code null}。 */
    public static RegistryAccess getOrNull() {
        // 1) 当前服务端（存档读写、命令、服务端 tick 的绝大多数场景都走这条）。
        final var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            final RegistryAccess access = server.registryAccess();
            if (access != null) {
                cached = access;
                return access;
            }
        }

        // 2) 客户端当前世界（单人游戏 / 刚进入世界的窗口期）。
        if (FMLEnvironment.dist == Dist.CLIENT) {
            final RegistryAccess client = ClientRegistryAccess.getOrNull();
            if (client != null) {
                cached = client;
                return client;
            }
        }

        // 3) 上一次成功的缓存。
        return cached;
    }

    /**
     * 取注册表访问器；取不到时退回 {@link RegistryAccess#EMPTY} 并只告警一次，
     * 避免今后再出现「无声无息地丢物品」。
     */
    public static RegistryAccess getOrEmpty() {
        final RegistryAccess resolved = getOrNull();
        if (resolved != null) {
            return resolved;
        }
        if (!warned) {
            warned = true;
            // 注意：这里不能用 Scala 侧的日志对象 —— Java 不能引用 Scala（compileJava 在
            // compileScala 之前）。直接用 log4j，日志名与模组保持一致。
            LOGGER.warn("No registry access is available; item serialization will degrade to empty tags "
                + "(items will be lost). This usually means item NBT is being read or written "
                + "when neither a server nor a client level exists.");
        }
        return RegistryAccess.EMPTY;
    }

    /** 单独一层，隔离对 {@code net.minecraft.client} 的静态引用（见类注释）。 */
    private static final class ClientRegistryAccess {
        static RegistryAccess getOrNull() {
            final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null) {
                return null;
            }
            return minecraft.level.registryAccess();
        }
    }
}
