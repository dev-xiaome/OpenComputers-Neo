package net.minecraft.world;

public enum InteractionResult {
    SUCCESS,
    SUCCESS_NO_ITEM_USED,
    CONSUME,
    CONSUME_PARTIAL,
    PASS,
    FAIL;

    public boolean consumesAction() {
        return this == SUCCESS || this == CONSUME || this == CONSUME_PARTIAL || this == SUCCESS_NO_ITEM_USED;
    }

    public boolean shouldSwing() {
        return this == SUCCESS || this == SUCCESS_NO_ITEM_USED;
    }

    public boolean indicateItemUse() {
        return this == SUCCESS || this == CONSUME;
    }

    public static InteractionResult sidedSuccess(boolean isClientSide) {
        return isClientSide ? SUCCESS : CONSUME;
    }
}
