package net.minecraft.world.level.gameevent;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class DynamicGameEventListener<T extends GameEventListener> {
    private final T listener;
    @Nullable
    private SectionPos lastSection;

    public DynamicGameEventListener(T listener) {
        this.listener = listener;
    }

    public void add(ServerLevel level) {
        this.move(level);
    }

    public T getListener() {
        return this.listener;
    }

    public void remove(ServerLevel level) {
        ifChunkExists(level, this.lastSection, p_248453_ -> p_248453_.unregister(this.listener));
    }

    public void move(ServerLevel level) {
        this.listener.getListenerSource().getPosition(level).map(SectionPos::of).ifPresent(p_223621_ -> {
            if (this.lastSection == null || !this.lastSection.equals(p_223621_)) {
                ifChunkExists(level, this.lastSection, p_248452_ -> p_248452_.unregister(this.listener));
                this.lastSection = p_223621_;
                ifChunkExists(level, this.lastSection, p_248451_ -> p_248451_.register(this.listener));
            }
        });
    }

    private static void ifChunkExists(LevelReader level, @Nullable SectionPos sectionPos, Consumer<GameEventListenerRegistry> dispatcherConsumer) {
        if (sectionPos != null) {
            ChunkAccess chunkaccess = level.getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.FULL, false);
            if (chunkaccess != null) {
                dispatcherConsumer.accept(chunkaccess.getListenerRegistry(sectionPos.y()));
            }
        }
    }
}
