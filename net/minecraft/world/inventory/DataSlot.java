package net.minecraft.world.inventory;

public abstract class DataSlot {
    private int prevValue;

    public static DataSlot forContainer(final ContainerData data, final int idx) {
        return new DataSlot() {
            @Override
            public int get() {
                return data.get(idx);
            }

            @Override
            public void set(int p_39416_) {
                data.set(idx, p_39416_);
            }
        };
    }

    public static DataSlot shared(final int[] data, final int idx) {
        return new DataSlot() {
            @Override
            public int get() {
                return data[idx];
            }

            @Override
            public void set(int p_39424_) {
                data[idx] = p_39424_;
            }
        };
    }

    public static DataSlot standalone() {
        return new DataSlot() {
            private int value;

            @Override
            public int get() {
                return this.value;
            }

            @Override
            public void set(int p_39429_) {
                this.value = p_39429_;
            }
        };
    }

    public abstract int get();

    public abstract void set(int value);

    public boolean checkAndClearUpdateFlag() {
        int i = this.get();
        boolean flag = i != this.prevValue;
        this.prevValue = i;
        return flag;
    }
}
