package li.cil.oc.common.network;

import net.minecraft.world.entity.player.Player;

/**
 * 原始网络负载的分发入口。
 * <p>
 * 由 Java 网络层（{@link OpenComputersNetwork}）在收到 {@link OpenComputersPayload}
 * 后调用，实际实现注册在 Scala 侧（{@code li.cil.oc.common.PacketHandler.initialize}），
 * 这样 Java 侧就完全不需要引用 Scala 代码（构建顺序要求 Java 先于 Scala 编译）。
 * <p>
 * NeoForge 侧使用默认的 {@code HandlerThread.MAIN} 注册（{@code PayloadRegistrar}
 * 会用 {@code MainThreadPayloadHandler} 包装），因此本方法<b>始终在主线程被调用</b>，
 * 可以直接访问世界 / 方块实体 / 玩家状态。
 */
@FunctionalInterface
public interface NetworkDispatcher {
    /**
     * @param data   负载原始字节（包类型首字节 + 可选 GZIP 流）
     * @param player 发送方（服务端侧为 {@code ServerPlayer}）/ 接收方（客户端侧为本机玩家）
     */
    void dispatch(byte[] data, Player player);
}
