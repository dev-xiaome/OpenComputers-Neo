package net.minecraft.network.protocol.game;

import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class ClientboundSectionBlocksUpdatePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSectionBlocksUpdatePacket> STREAM_CODEC = Packet.codec(
        ClientboundSectionBlocksUpdatePacket::write, ClientboundSectionBlocksUpdatePacket::new
    );
    private static final int POS_IN_SECTION_BITS = 12;
    private final SectionPos sectionPos;
    private final short[] positions;
    private final BlockState[] states;

    public ClientboundSectionBlocksUpdatePacket(SectionPos sectionPos, ShortSet positions, LevelChunkSection section) {
        this.sectionPos = sectionPos;
        int i = positions.size();
        this.positions = new short[i];
        this.states = new BlockState[i];
        int j = 0;

        for (short short1 : positions) {
            this.positions[j] = short1;
            this.states[j] = section.getBlockState(
                SectionPos.sectionRelativeX(short1), SectionPos.sectionRelativeY(short1), SectionPos.sectionRelativeZ(short1)
            );
            j++;
        }
    }

    private ClientboundSectionBlocksUpdatePacket(FriendlyByteBuf buffer) {
        this.sectionPos = SectionPos.of(buffer.readLong());
        int i = buffer.readVarInt();
        this.positions = new short[i];
        this.states = new BlockState[i];

        for (int j = 0; j < i; j++) {
            long k = buffer.readVarLong();
            this.positions[j] = (short)((int)(k & 4095L));
            this.states[j] = Block.BLOCK_STATE_REGISTRY.byId((int)(k >>> 12));
        }
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeLong(this.sectionPos.asLong());
        buffer.writeVarInt(this.positions.length);

        for (int i = 0; i < this.positions.length; i++) {
            buffer.writeVarLong((long)Block.getId(this.states[i]) << 12 | (long)this.positions[i]);
        }
    }

    @Override
    public PacketType<ClientboundSectionBlocksUpdatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SECTION_BLOCKS_UPDATE;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleChunkBlocksUpdate(this);
    }

    public void runUpdates(BiConsumer<BlockPos, BlockState> consumer) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < this.positions.length; i++) {
            short short1 = this.positions[i];
            blockpos$mutableblockpos.set(
                this.sectionPos.relativeToBlockX(short1), this.sectionPos.relativeToBlockY(short1), this.sectionPos.relativeToBlockZ(short1)
            );
            consumer.accept(blockpos$mutableblockpos, this.states[i]);
        }
    }
}
