package net.minecraft.commands;

@FunctionalInterface
public interface CommandResultCallback {
    CommandResultCallback EMPTY = new CommandResultCallback() {
        @Override
        public void onResult(boolean p_309581_, int p_309698_) {
        }

        @Override
        public String toString() {
            return "<empty>";
        }
    };

    void onResult(boolean success, int result);

    default void onSuccess(int result) {
        this.onResult(true, result);
    }

    default void onFailure() {
        this.onResult(false, 0);
    }

    static CommandResultCallback chain(CommandResultCallback first, CommandResultCallback second) {
        if (first == EMPTY) {
            return second;
        } else {
            return second == EMPTY ? first : (p_309648_, p_309546_) -> {
                first.onResult(p_309648_, p_309546_);
                second.onResult(p_309648_, p_309546_);
            };
        }
    }
}
