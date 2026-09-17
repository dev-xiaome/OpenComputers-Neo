package net.minecraft.network.protocol.game;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ServerboundInteractPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundInteractPacket> STREAM_CODEC = Packet.codec(
        ServerboundInteractPacket::write, ServerboundInteractPacket::new
    );
    private final int entityId;
    private final ServerboundInteractPacket.Action action;
    private final boolean usingSecondaryAction;
    static final ServerboundInteractPacket.Action ATTACK_ACTION = new ServerboundInteractPacket.Action() {
        @Override
        public ServerboundInteractPacket.ActionType getType() {
            return ServerboundInteractPacket.ActionType.ATTACK;
        }

        @Override
        public void dispatch(ServerboundInteractPacket.Handler p_179624_) {
            p_179624_.onAttack();
        }

        @Override
        public void write(FriendlyByteBuf p_179622_) {
        }
    };

    private ServerboundInteractPacket(int entityId, boolean usingSecondaryAction, ServerboundInteractPacket.Action action) {
        this.entityId = entityId;
        this.action = action;
        this.usingSecondaryAction = usingSecondaryAction;
    }

    public static ServerboundInteractPacket createAttackPacket(Entity entity, boolean usingSecondaryAction) {
        return new ServerboundInteractPacket(entity.getId(), usingSecondaryAction, ATTACK_ACTION);
    }

    public static ServerboundInteractPacket createInteractionPacket(Entity entity, boolean usingSecondaryAction, InteractionHand hand) {
        return new ServerboundInteractPacket(entity.getId(), usingSecondaryAction, new ServerboundInteractPacket.InteractionAction(hand));
    }

    public static ServerboundInteractPacket createInteractionPacket(Entity entity, boolean usingSecondaryAction, InteractionHand hand, Vec3 interactionLocation) {
        return new ServerboundInteractPacket(entity.getId(), usingSecondaryAction, new ServerboundInteractPacket.InteractionAtLocationAction(hand, interactionLocation));
    }

    private ServerboundInteractPacket(FriendlyByteBuf buffer) {
        this.entityId = buffer.readVarInt();
        ServerboundInteractPacket.ActionType serverboundinteractpacket$actiontype = buffer.readEnum(ServerboundInteractPacket.ActionType.class);
        this.action = serverboundinteractpacket$actiontype.reader.apply(buffer);
        this.usingSecondaryAction = buffer.readBoolean();
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeEnum(this.action.getType());
        this.action.write(buffer);
        buffer.writeBoolean(this.usingSecondaryAction);
    }

    @Override
    public PacketType<ServerboundInteractPacket> type() {
        return GamePacketTypes.SERVERBOUND_INTERACT;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ServerGamePacketListener handler) {
        handler.handleInteract(this);
    }

    @Nullable
    public Entity getTarget(ServerLevel level) {
        return level.getEntityOrPart(this.entityId);
    }

    public boolean isUsingSecondaryAction() {
        return this.usingSecondaryAction;
    }

    public void dispatch(ServerboundInteractPacket.Handler handler) {
        this.action.dispatch(handler);
    }

    interface Action {
        ServerboundInteractPacket.ActionType getType();

        void dispatch(ServerboundInteractPacket.Handler handler);

        void write(FriendlyByteBuf buffer);
    }

    static enum ActionType {
        INTERACT(ServerboundInteractPacket.InteractionAction::new),
        ATTACK(p_179639_ -> ServerboundInteractPacket.ATTACK_ACTION),
        INTERACT_AT(ServerboundInteractPacket.InteractionAtLocationAction::new);

        final Function<FriendlyByteBuf, ServerboundInteractPacket.Action> reader;

        private ActionType(Function<FriendlyByteBuf, ServerboundInteractPacket.Action> reader) {
            this.reader = reader;
        }
    }

    public interface Handler {
        void onInteraction(InteractionHand hand);

        void onInteraction(InteractionHand hand, Vec3 interactionLocation);

        void onAttack();
    }

    static class InteractionAction implements ServerboundInteractPacket.Action {
        private final InteractionHand hand;

        InteractionAction(InteractionHand hand) {
            this.hand = hand;
        }

        private InteractionAction(FriendlyByteBuf buffer) {
            this.hand = buffer.readEnum(InteractionHand.class);
        }

        @Override
        public ServerboundInteractPacket.ActionType getType() {
            return ServerboundInteractPacket.ActionType.INTERACT;
        }

        @Override
        public void dispatch(ServerboundInteractPacket.Handler handler) {
            handler.onInteraction(this.hand);
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeEnum(this.hand);
        }
    }

    static class InteractionAtLocationAction implements ServerboundInteractPacket.Action {
        private final InteractionHand hand;
        private final Vec3 location;

        InteractionAtLocationAction(InteractionHand hand, Vec3 location) {
            this.hand = hand;
            this.location = location;
        }

        private InteractionAtLocationAction(FriendlyByteBuf buffer) {
            this.location = new Vec3((double)buffer.readFloat(), (double)buffer.readFloat(), (double)buffer.readFloat());
            this.hand = buffer.readEnum(InteractionHand.class);
        }

        @Override
        public ServerboundInteractPacket.ActionType getType() {
            return ServerboundInteractPacket.ActionType.INTERACT_AT;
        }

        @Override
        public void dispatch(ServerboundInteractPacket.Handler handler) {
            handler.onInteraction(this.hand, this.location);
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeFloat((float)this.location.x);
            buffer.writeFloat((float)this.location.y);
            buffer.writeFloat((float)this.location.z);
            buffer.writeEnum(this.hand);
        }
    }
}
