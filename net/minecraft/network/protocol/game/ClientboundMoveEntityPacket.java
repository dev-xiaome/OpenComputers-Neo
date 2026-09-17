package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public abstract class ClientboundMoveEntityPacket implements Packet<ClientGamePacketListener> {
    protected final int entityId;
    protected final short xa;
    protected final short ya;
    protected final short za;
    protected final byte yRot;
    protected final byte xRot;
    protected final boolean onGround;
    protected final boolean hasRot;
    protected final boolean hasPos;

    protected ClientboundMoveEntityPacket(
        int entityId,
        short xa,
        short ya,
        short za,
        byte yRot,
        byte xRot,
        boolean onGround,
        boolean hasRot,
        boolean hasPos
    ) {
        this.entityId = entityId;
        this.xa = xa;
        this.ya = ya;
        this.za = za;
        this.yRot = yRot;
        this.xRot = xRot;
        this.onGround = onGround;
        this.hasRot = hasRot;
        this.hasPos = hasPos;
    }

    @Override
    public abstract PacketType<? extends ClientboundMoveEntityPacket> type();

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleMoveEntity(this);
    }

    @Override
    public String toString() {
        return "Entity_" + super.toString();
    }

    @Nullable
    public Entity getEntity(Level level) {
        return level.getEntity(this.entityId);
    }

    public short getXa() {
        return this.xa;
    }

    public short getYa() {
        return this.ya;
    }

    public short getZa() {
        return this.za;
    }

    public byte getyRot() {
        return this.yRot;
    }

    public byte getxRot() {
        return this.xRot;
    }

    public boolean hasRotation() {
        return this.hasRot;
    }

    public boolean hasPosition() {
        return this.hasPos;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public static class Pos extends ClientboundMoveEntityPacket {
        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.Pos> STREAM_CODEC = Packet.codec(
            ClientboundMoveEntityPacket.Pos::write, ClientboundMoveEntityPacket.Pos::read
        );

        public Pos(int entityId, short xa, short ya, short za, boolean onGround) {
            super(entityId, xa, ya, za, (byte)0, (byte)0, onGround, false, true);
        }

        private static ClientboundMoveEntityPacket.Pos read(FriendlyByteBuf buffer) {
            int i = buffer.readVarInt();
            short short1 = buffer.readShort();
            short short2 = buffer.readShort();
            short short3 = buffer.readShort();
            boolean flag = buffer.readBoolean();
            return new ClientboundMoveEntityPacket.Pos(i, short1, short2, short3, flag);
        }

        /**
         * Writes the raw packet data to the data stream.
         */
        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.entityId);
            buffer.writeShort(this.xa);
            buffer.writeShort(this.ya);
            buffer.writeShort(this.za);
            buffer.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.Pos> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS;
        }
    }

    public static class PosRot extends ClientboundMoveEntityPacket {
        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.PosRot> STREAM_CODEC = Packet.codec(
            ClientboundMoveEntityPacket.PosRot::write, ClientboundMoveEntityPacket.PosRot::read
        );

        public PosRot(int entityId, short xa, short ya, short za, byte yRot, byte xRot, boolean onGround) {
            super(entityId, xa, ya, za, yRot, xRot, onGround, true, true);
        }

        private static ClientboundMoveEntityPacket.PosRot read(FriendlyByteBuf buffer) {
            int i = buffer.readVarInt();
            short short1 = buffer.readShort();
            short short2 = buffer.readShort();
            short short3 = buffer.readShort();
            byte b0 = buffer.readByte();
            byte b1 = buffer.readByte();
            boolean flag = buffer.readBoolean();
            return new ClientboundMoveEntityPacket.PosRot(i, short1, short2, short3, b0, b1, flag);
        }

        /**
         * Writes the raw packet data to the data stream.
         */
        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.entityId);
            buffer.writeShort(this.xa);
            buffer.writeShort(this.ya);
            buffer.writeShort(this.za);
            buffer.writeByte(this.yRot);
            buffer.writeByte(this.xRot);
            buffer.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.PosRot> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS_ROT;
        }
    }

    public static class Rot extends ClientboundMoveEntityPacket {
        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.Rot> STREAM_CODEC = Packet.codec(
            ClientboundMoveEntityPacket.Rot::write, ClientboundMoveEntityPacket.Rot::read
        );

        public Rot(int entityId, byte yRot, byte xRot, boolean onGround) {
            super(entityId, (short)0, (short)0, (short)0, yRot, xRot, onGround, true, false);
        }

        private static ClientboundMoveEntityPacket.Rot read(FriendlyByteBuf buffer) {
            int i = buffer.readVarInt();
            byte b0 = buffer.readByte();
            byte b1 = buffer.readByte();
            boolean flag = buffer.readBoolean();
            return new ClientboundMoveEntityPacket.Rot(i, b0, b1, flag);
        }

        /**
         * Writes the raw packet data to the data stream.
         */
        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.entityId);
            buffer.writeByte(this.yRot);
            buffer.writeByte(this.xRot);
            buffer.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.Rot> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_ROT;
        }
    }
}
