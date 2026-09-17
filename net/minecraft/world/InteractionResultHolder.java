package net.minecraft.world;

public class InteractionResultHolder<T> {
    private final InteractionResult result;
    private final T object;

    public InteractionResultHolder(InteractionResult result, T object) {
        this.result = result;
        this.object = object;
    }

    public InteractionResult getResult() {
        return this.result;
    }

    public T getObject() {
        return this.object;
    }

    public static <T> InteractionResultHolder<T> success(T type) {
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, type);
    }

    public static <T> InteractionResultHolder<T> consume(T type) {
        return new InteractionResultHolder<>(InteractionResult.CONSUME, type);
    }

    public static <T> InteractionResultHolder<T> pass(T type) {
        return new InteractionResultHolder<>(InteractionResult.PASS, type);
    }

    public static <T> InteractionResultHolder<T> fail(T type) {
        return new InteractionResultHolder<>(InteractionResult.FAIL, type);
    }

    public static <T> InteractionResultHolder<T> sidedSuccess(T object, boolean isClientSide) {
        return isClientSide ? success(object) : consume(object);
    }
}
