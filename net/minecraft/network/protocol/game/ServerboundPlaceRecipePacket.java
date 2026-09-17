package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public class ServerboundPlaceRecipePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPlaceRecipePacket> STREAM_CODEC = Packet.codec(
        ServerboundPlaceRecipePacket::write, ServerboundPlaceRecipePacket::new
    );
    private final int containerId;
    private final ResourceLocation recipe;
    private final boolean shiftDown;

    public ServerboundPlaceRecipePacket(int containerId, RecipeHolder<?> recipe, boolean shiftDown) {
        this.containerId = containerId;
        this.recipe = recipe.id();
        this.shiftDown = shiftDown;
    }

    private ServerboundPlaceRecipePacket(FriendlyByteBuf buffer) {
        this.containerId = buffer.readByte();
        this.recipe = buffer.readResourceLocation();
        this.shiftDown = buffer.readBoolean();
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeByte(this.containerId);
        buffer.writeResourceLocation(this.recipe);
        buffer.writeBoolean(this.shiftDown);
    }

    @Override
    public PacketType<ServerboundPlaceRecipePacket> type() {
        return GamePacketTypes.SERVERBOUND_PLACE_RECIPE;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ServerGamePacketListener handler) {
        handler.handlePlaceRecipe(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public ResourceLocation getRecipe() {
        return this.recipe;
    }

    public boolean isShiftDown() {
        return this.shiftDown;
    }
}
