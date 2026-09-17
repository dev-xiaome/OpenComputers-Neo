package li.cil.oc.util;

import net.neoforged.fml.util.thread.EffectiveSide;

/**
 * 判断当前代码跑在逻辑服务端还是客户端。
 *
 * <p>NeoForge 1.21.1 没有 Forge 1.20 的 {@code forgespi.Environment}，因此改用
 * {@link EffectiveSide}：它给出的是**当前线程所属的逻辑侧**。
 *
 * <p>这里刻意**不**看物理侧（{@code FMLEnvironment.dist}）：单人游戏里物理侧是客户端，
 * 但集成服务端同样在跑，方块实体与机器 tick 都在逻辑服务端上执行。按物理侧判断会把
 * 单人游戏的服务端逻辑判成客户端，直接后果是网络节点建不出来、机器 tick 空指针崩服。
 */
public final class SideTracker {
    public static boolean isServer() {
        return EffectiveSide.get().isServer();
    }

    public static boolean isClient() {
        return !isServer();
    }

    private SideTracker() {
    }
}
