package net.minecraft.world.inventory;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface ContainerLevelAccess {
    ContainerLevelAccess NULL = new ContainerLevelAccess() {
        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> p_39304_) {
            return Optional.empty();
        }
    };

    static ContainerLevelAccess create(final Level level, final BlockPos pos) {
        return new ContainerLevelAccess() {
            @Override
            public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> p_39311_) {
                return Optional.of(p_39311_.apply(level, pos));
            }
        };
    }

    <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> levelPosConsumer);

    default <T> T evaluate(BiFunction<Level, BlockPos, T> levelPosConsumer, T defaultValue) {
        return this.evaluate(levelPosConsumer).orElse(defaultValue);
    }

    default void execute(BiConsumer<Level, BlockPos> levelPosConsumer) {
        this.evaluate((p_39296_, p_39297_) -> {
            levelPosConsumer.accept(p_39296_, p_39297_);
            return Optional.empty();
        });
    }
}
