package net.minecraft.network.protocol.game;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBookSettings;

public class ClientboundRecipePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundRecipePacket> STREAM_CODEC = Packet.codec(
        ClientboundRecipePacket::write, ClientboundRecipePacket::new
    );
    private final ClientboundRecipePacket.State state;
    private final List<ResourceLocation> recipes;
    private final List<ResourceLocation> toHighlight;
    private final RecipeBookSettings bookSettings;

    public ClientboundRecipePacket(
        ClientboundRecipePacket.State state, Collection<ResourceLocation> recipes, Collection<ResourceLocation> toHighlight, RecipeBookSettings bookSettings
    ) {
        this.state = state;
        this.recipes = ImmutableList.copyOf(recipes);
        this.toHighlight = ImmutableList.copyOf(toHighlight);
        this.bookSettings = bookSettings;
    }

    private ClientboundRecipePacket(FriendlyByteBuf buffer) {
        this.state = buffer.readEnum(ClientboundRecipePacket.State.class);
        this.bookSettings = RecipeBookSettings.read(buffer);
        this.recipes = buffer.readList(FriendlyByteBuf::readResourceLocation);
        if (this.state == ClientboundRecipePacket.State.INIT) {
            this.toHighlight = buffer.readList(FriendlyByteBuf::readResourceLocation);
        } else {
            this.toHighlight = ImmutableList.of();
        }
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.state);
        this.bookSettings.write(buffer);
        buffer.writeCollection(this.recipes, FriendlyByteBuf::writeResourceLocation);
        if (this.state == ClientboundRecipePacket.State.INIT) {
            buffer.writeCollection(this.toHighlight, FriendlyByteBuf::writeResourceLocation);
        }
    }

    @Override
    public PacketType<ClientboundRecipePacket> type() {
        return GamePacketTypes.CLIENTBOUND_RECIPE;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleAddOrRemoveRecipes(this);
    }

    public List<ResourceLocation> getRecipes() {
        return this.recipes;
    }

    public List<ResourceLocation> getHighlights() {
        return this.toHighlight;
    }

    public RecipeBookSettings getBookSettings() {
        return this.bookSettings;
    }

    public ClientboundRecipePacket.State getState() {
        return this.state;
    }

    public static enum State {
        INIT,
        ADD,
        REMOVE;
    }
}
