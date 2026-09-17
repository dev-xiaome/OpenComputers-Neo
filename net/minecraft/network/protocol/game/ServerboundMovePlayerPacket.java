package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public abstract class ServerboundMovePlayerPacket implements Packet<ServerGamePacketListener> {
    protected final double x;
    protected final double y;
    protected final double z;
    protected final float yRot;
    protected final float xRot;
    protected final boolean onGround;
    protected final boolean hasPos;
    protected final boolean hasRot;

    protected ServerboundMovePlayerPacket(
        double x, double y, double z, float yRot, float xRot, boolean onGround, boolean hasPos, boolean hasRot
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
        this.onGround = onGround;
        this.hasPos = hasPos;
        this.hasRot = hasRot;
    }

    @Override
    public abstract PacketType<? extends ServerboundMovePlayerPacket> type();

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ServerGamePacketListener handler) {
        handler.handleMovePlayer(this);
    }

    public double getX(double defaultValue) {
        return this.hasPos ? this.x : defaultValue;
    }

    public double getY(double defaultValue) {
        return this.hasPos ? this.y : defaultValue;
    }

    public double getZ(double defaultValue) {
        return this.hasPos ? this.z : defaultValue;
    }

    public float getYRot(float defaultValue) {
        return this.hasRot ? this.yRot : defaultValue;
    }

    public float getXRot(float defaultValue) {
        return this.hasRot ? this.xRot : defaultValue;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public boolean hasPosition() {
        return this.hasPos;
    }

    public boolean hasRotation() {
        return this.hasRot;
    }

    public static class Pos extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.Pos> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.Pos::write, ServerboundMovePlayerPacket.Pos::read
        );

        public Pos(double x, double y, double z, boolean onGround) {
            super(x, y, z, 0.0F, 0.0F, onGround, true, false);
        }

        private static ServerboundMovePlayerPacket.Pos read(FriendlyByteBuf buffer) {
            double d0 = buffer.readDouble();
            double d1 = buffer.readDouble();
            double d2 = buffer.readDouble();
            boolean flag = buffer.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.Pos(d0, d1, d2, flag);
        }

        /**
         * Writes the raw packet data to the data stream.
         */
        private void write(FriendlyByteBuf buffer) {
            buffer.writeDouble(this.x);
            buffer.writeDouble(this.y);
            buffer.writeDouble(this.z);
            buffer.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.Pos> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS;
        }
    }

    public static class PosRot extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.PosRot> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.PosRot::write, ServerboundMovePlayerPacket.PosRot::read
        );

        public PosRot(double x, double y, double z, float yRot, float xRot, boolean onGround) {
            super(x, y, z, yRot, xRot, onGround, true, true);
        }

        private static ServerboundMovePlayerPacket.PosRot read(FriendlyByteBuf buffer) {
            double d0 = buffer.readDouble();
            double d1 = buffer.readDouble();
            double d2 = buffer.readDouble();
            float f = buffer.readFloat();
            float f1 = buffer.readFloat();
            boolean flag = buffer.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.PosRot(d0, d1, d2, f, f1, flag);
        }

        /**
         * Writes the raw packet data to the data stream.
         */
        private void write(FriendlyByteBuf buffer) {
            buffer.writeDouble(this.x);
            buffer.writeDouble(this.y);
            buffer.writeDouble(this.z);
            buffer.writeFloat(this.yRot);
            buffer.writeFloat(this.xRot);
            buffer.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.PosRot> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS_ROT;
        }
    }

    public static class Rot extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.Rot> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.Rot::write, ServerboundMovePlayerPacket.Rot::read
        );

        public Rot(float yRot, float xRot, boolean onGround) {
            super(0.0, 0.0, 0.0, yRot, xRot, onGround, false, true);
        }

        private static ServerboundMovePlayerPacket.Rot read(FriendlyByteBuf buffer) {
            float f = buffer.readFloat();
            float f1 = buffer.readFloat();
            boolean flag = buffer.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.Rot(f, f1, flag);
        }

        /**
         * Writes the raw packet data to the data stream.
         */
        private void write(FriendlyByteBuf buffer) {
            buffer.writeFloat(this.yRot);
            buffer.writeFloat(this.xRot);
            buffer.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.Rot> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_ROT;
        }
    }

    public static class StatusOnly extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.StatusOnly> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.StatusOnly::write, ServerboundMovePlayerPacket.StatusOnly::read
        );

        public StatusOnly(boolean onGround) {
            super(0.0, 0.0, 0.0, 0.0F, 0.0F, onGround, false, false);
        }

        private static ServerboundMovePlayerPacket.StatusOnly read(FriendlyByteBuf buffer) {
            boolean flag = buffer.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.StatusOnly(flag);
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.StatusOnly> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_STATUS_ONLY;
        }
    }
}
