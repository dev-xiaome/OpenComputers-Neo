package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.Hash.Strategy;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;

public record SavedTick<T>(T type, BlockPos pos, int delay, TickPriority priority) {
    private static final String TAG_ID = "i";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_DELAY = "t";
    private static final String TAG_PRIORITY = "p";
    public static final Strategy<SavedTick<?>> UNIQUE_TICK_HASH = new Strategy<SavedTick<?>>() {
        public int hashCode(SavedTick<?> savedTick) {
            return 31 * savedTick.pos().hashCode() + savedTick.type().hashCode();
        }

        public boolean equals(@Nullable SavedTick<?> first, @Nullable SavedTick<?> second) {
            if (first == second) {
                return true;
            } else {
                return first != null && second != null ? first.type() == second.type() && first.pos().equals(second.pos()) : false;
            }
        }
    };

    public static <T> void loadTickList(ListTag tag, Function<String, Optional<T>> idParser, ChunkPos chunkPos, Consumer<SavedTick<T>> output) {
        long i = chunkPos.toLong();

        for (int j = 0; j < tag.size(); j++) {
            CompoundTag compoundtag = tag.getCompound(j);
            loadTick(compoundtag, idParser).ifPresent(p_210665_ -> {
                if (ChunkPos.asLong(p_210665_.pos()) == i) {
                    output.accept((SavedTick<T>)p_210665_);
                }
            });
        }
    }

    public static <T> Optional<SavedTick<T>> loadTick(CompoundTag tag, Function<String, Optional<T>> idParser) {
        return idParser.apply(tag.getString("i")).map(p_210668_ -> {
            BlockPos blockpos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            return new SavedTick<>((T)p_210668_, blockpos, tag.getInt("t"), TickPriority.byValue(tag.getInt("p")));
        });
    }

    private static CompoundTag saveTick(String id, BlockPos pos, int delay, TickPriority priority) {
        CompoundTag compoundtag = new CompoundTag();
        compoundtag.putString("i", id);
        compoundtag.putInt("x", pos.getX());
        compoundtag.putInt("y", pos.getY());
        compoundtag.putInt("z", pos.getZ());
        compoundtag.putInt("t", delay);
        compoundtag.putInt("p", priority.getValue());
        return compoundtag;
    }

    public static <T> CompoundTag saveTick(ScheduledTick<T> tick, Function<T, String> idGetter, long gameTime) {
        return saveTick(idGetter.apply(tick.type()), tick.pos(), (int)(tick.triggerTick() - gameTime), tick.priority());
    }

    public CompoundTag save(Function<T, String> idGetter) {
        return saveTick(idGetter.apply(this.type), this.pos, this.delay, this.priority);
    }

    public ScheduledTick<T> unpack(long gameTime, long subTickOrder) {
        return new ScheduledTick<T>(this.type, this.pos, gameTime + (long)this.delay, this.priority, subTickOrder);
    }

    public static <T> SavedTick<T> probe(T type, BlockPos pos) {
        return new SavedTick<>(type, pos, 0, TickPriority.NORMAL);
    }
}
