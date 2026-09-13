package li.cil.oc.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * OpenComputers 的网络负载（NeoForge 1.21.1 的 {@link CustomPacketPayload}）。
 * <p>
 * 这里<b>只</b>承载一块原始字节数据（{@code byte[]}），也就是原 1.7.10 版
 * {@code FMLProxyPacket} 里那个 {@code ByteBuf} 的内容：包类型首字节 + 可选 GZIP 流。
 * 具体的读写逻辑仍然由 Scala 侧 {@code li.cil.oc.common.PacketBuilder} /
 * {@code li.cil.oc.common.PacketParser} 通过 {@code DataOutputStream} /
 * {@code DataInputStream} 完成，因此这个类不需要知道任何 OC 的数据格式，
 * 也就不需要随包类型变化而修改。
 * <p>
 * 编解码使用 {@link ByteBufCodecs#BYTE_ARRAY}（写入 VarInt 长度 + 原始字节），
 * 由 {@link OpenComputersNetwork} 在 {@code RegisterPayloadHandlersEvent} 中注册。
 */
public record OpenComputersPayload(byte[] data) implements CustomPacketPayload {
    /** 负载类型 id：{@code opencomputers_neo:packet}。命名空间与 mod id 一致（Java 侧不能引用 Scala 常量）。 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("opencomputers_neo", "packet");

    /**
     * 负载类型。客户端与服务端双向共用同一个 type（NeoForge 不允许同一个 id 在同一
     * protocol 下注册两次，因此双向注册必须走 {@code playBidirectional}）。
     */
    public static final CustomPacketPayload.Type<OpenComputersPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    /** 编解码器：{@code byte[]} ⇄ payload。 */
    public static final StreamCodec<ByteBuf, OpenComputersPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, OpenComputersPayload::data, OpenComputersPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
