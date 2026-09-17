package net.minecraft.network.protocol.game;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.syncher.SynchedEntityData;

public record ClientboundSetEntityDataPacket(int id, List<SynchedEntityData.DataValue<?>> packedItems) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSetEntityDataPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEntityDataPacket::write, ClientboundSetEntityDataPacket::new
    );
    public static final int EOF_MARKER = 255;

    private ClientboundSetEntityDataPacket(RegistryFriendlyByteBuf p_319996_) {
        this(p_319996_.readVarInt(), unpack(p_319996_));
    }

    private static void pack(List<SynchedEntityData.DataValue<?>> dataValues, RegistryFriendlyByteBuf buffer) {
        for (SynchedEntityData.DataValue<?> datavalue : dataValues) {
            datavalue.write(buffer);
        }

        buffer.writeByte(255);
    }

    private static List<SynchedEntityData.DataValue<?>> unpack(RegistryFriendlyByteBuf buffer) {
        List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();

        int i;
        while ((i = buffer.readUnsignedByte()) != 255) {
            list.add(SynchedEntityData.DataValue.read(buffer, i));
        }

        return list;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.id);
        pack(this.packedItems, buffer);
    }

    @Override
    public PacketType<ClientboundSetEntityDataPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetEntityData(this);
    }
}
