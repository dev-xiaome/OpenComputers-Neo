package li.cil.oc.util;

import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;

/**
 * 判断「当前代码正运行在哪一侧」。
 *
 * <p>1.7.10 的实现是 {@code FMLCommonHandler.instance().getEffectiveSide()}，也就是
 * <b>逻辑侧</b>：同一个 JVM 里跑服务端逻辑时返回 SERVER，即使物理端是客户端
 * （单人游戏 / 集成服务器就是这么跑的）。
 *
 * <p>1.21.1 的等价物是 {@link EffectiveSide#get()}（FML 按线程跟踪的
 * {@link LogicalSide}），<b>不能</b>用 {@code FMLEnvironment.dist}：那是
 * <b>物理侧</b>，单人游戏里恒为 CLIENT。用错会让所有「服务端才建立」的对象
 * 在单人游戏里被跳过 —— 例如 {@code server.network.Network.newNode(...).create()}
 * 返回 {@code null}，于是组件拿不到节点、方块实体 tick 时抛
 * {@code NullPointerException: ... Machine.node() is null} 并直接崩档。
 */
public final class SideTracker {

    public static boolean isServer() {
        return EffectiveSide.get() == LogicalSide.SERVER;
    }

    public static boolean isClient() {
        return !isServer();
    }

}
