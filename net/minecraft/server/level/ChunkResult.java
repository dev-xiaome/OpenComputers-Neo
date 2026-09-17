package net.minecraft.server.level;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public interface ChunkResult<T> {
    static <T> ChunkResult<T> of(T value) {
        return new ChunkResult.Success<>(value);
    }

    static <T> ChunkResult<T> error(String p_error) {
        return error(() -> p_error);
    }

    static <T> ChunkResult<T> error(Supplier<String> errorSupplier) {
        return new ChunkResult.Fail<>(errorSupplier);
    }

    boolean isSuccess();

    @Nullable
    T orElse(@Nullable T value);

    @Nullable
    static <R> R orElse(ChunkResult<? extends R> chunkResult, @Nullable R orElse) {
        R r = (R)chunkResult.orElse(null);
        return r != null ? r : orElse;
    }

    @Nullable
    String getError();

    ChunkResult<T> ifSuccess(Consumer<T> action);

    <R> ChunkResult<R> map(Function<T, R> mappingFunction);

    <E extends Throwable> T orElseThrow(Supplier<E> exceptionSupplier) throws E;

    public static record Fail<T>(Supplier<String> error) implements ChunkResult<T> {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Nullable
        @Override
        public T orElse(@Nullable T p_332121_) {
            return p_332121_;
        }

        @Override
        public String getError() {
            return this.error.get();
        }

        @Override
        public ChunkResult<T> ifSuccess(Consumer<T> p_332025_) {
            return this;
        }

        @Override
        public <R> ChunkResult<R> map(Function<T, R> p_331060_) {
            return new ChunkResult.Fail(this.error);
        }

        @Override
        public <E extends Throwable> T orElseThrow(Supplier<E> p_331373_) throws E {
            throw p_331373_.get();
        }
    }

    public static record Success<T>(T value) implements ChunkResult<T> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T orElse(@Nullable T p_331661_) {
            return this.value;
        }

        @Nullable
        @Override
        public String getError() {
            return null;
        }

        @Override
        public ChunkResult<T> ifSuccess(Consumer<T> p_330331_) {
            p_330331_.accept(this.value);
            return this;
        }

        @Override
        public <R> ChunkResult<R> map(Function<T, R> p_330743_) {
            return new ChunkResult.Success<>(p_330743_.apply(this.value));
        }

        @Override
        public <E extends Throwable> T orElseThrow(Supplier<E> p_332180_) throws E {
            return this.value;
        }
    }
}
