package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ClientboundSetEquipmentPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSetEquipmentPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEquipmentPacket::write, ClientboundSetEquipmentPacket::new
    );
    private static final byte CONTINUE_MASK = -128;
    private final int entity;
    private final List<Pair<EquipmentSlot, ItemStack>> slots;

    public ClientboundSetEquipmentPacket(int entity, List<Pair<EquipmentSlot, ItemStack>> slots) {
        this.entity = entity;
        this.slots = slots;
    }

    private ClientboundSetEquipmentPacket(RegistryFriendlyByteBuf buffer) {
        this.entity = buffer.readVarInt();
        EquipmentSlot[] aequipmentslot = EquipmentSlot.values();
        this.slots = Lists.newArrayList();

        int i;
        do {
            i = buffer.readByte();
            EquipmentSlot equipmentslot = aequipmentslot[i & 127];
            ItemStack itemstack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            this.slots.add(Pair.of(equipmentslot, itemstack));
        } while ((i & -128) != 0);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entity);
        int i = this.slots.size();

        for (int j = 0; j < i; j++) {
            Pair<EquipmentSlot, ItemStack> pair = this.slots.get(j);
            EquipmentSlot equipmentslot = pair.getFirst();
            boolean flag = j != i - 1;
            int k = equipmentslot.ordinal();
            buffer.writeByte(flag ? k | -128 : k);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, pair.getSecond());
        }
    }

    @Override
    public PacketType<ClientboundSetEquipmentPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_EQUIPMENT;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetEquipment(this);
    }

    public int getEntity() {
        return this.entity;
    }

    public List<Pair<EquipmentSlot, ItemStack>> getSlots() {
        return this.slots;
    }
}
