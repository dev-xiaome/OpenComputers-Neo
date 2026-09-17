package net.minecraft.client;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DebugQueryHandler {
    private final ClientPacketListener connection;
    private int transactionId = -1;
    @Nullable
    private Consumer<CompoundTag> callback;

    public DebugQueryHandler(ClientPacketListener connection) {
        this.connection = connection;
    }

    public boolean handleResponse(int transactionId, @Nullable CompoundTag tag) {
        if (this.transactionId == transactionId && this.callback != null) {
            this.callback.accept(tag);
            this.callback = null;
            return true;
        } else {
            return false;
        }
    }

    private int startTransaction(Consumer<CompoundTag> callback) {
        this.callback = callback;
        return ++this.transactionId;
    }

    public void queryEntityTag(int entId, Consumer<CompoundTag> tag) {
        int i = this.startTransaction(tag);
        this.connection.send(new ServerboundEntityTagQueryPacket(i, entId));
    }

    public void queryBlockEntityTag(BlockPos pos, Consumer<CompoundTag> tag) {
        int i = this.startTransaction(tag);
        this.connection.send(new ServerboundBlockEntityTagQueryPacket(i, pos));
    }
}
