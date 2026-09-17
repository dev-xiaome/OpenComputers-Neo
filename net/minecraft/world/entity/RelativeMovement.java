package net.minecraft.world.entity;

import java.util.EnumSet;
import java.util.Set;

public enum RelativeMovement {
    X(0),
    Y(1),
    Z(2),
    Y_ROT(3),
    X_ROT(4);

    public static final Set<RelativeMovement> ALL = Set.of(values());
    public static final Set<RelativeMovement> ROTATION = Set.of(X_ROT, Y_ROT);
    private final int bit;

    private RelativeMovement(int bit) {
        this.bit = bit;
    }

    private int getMask() {
        return 1 << this.bit;
    }

    private boolean isSet(int packedMovements) {
        return (packedMovements & this.getMask()) == this.getMask();
    }

    public static Set<RelativeMovement> unpack(int packedMovements) {
        Set<RelativeMovement> set = EnumSet.noneOf(RelativeMovement.class);

        for (RelativeMovement relativemovement : values()) {
            if (relativemovement.isSet(packedMovements)) {
                set.add(relativemovement);
            }
        }

        return set;
    }

    public static int pack(Set<RelativeMovement> movements) {
        int i = 0;

        for (RelativeMovement relativemovement : movements) {
            i |= relativemovement.getMask();
        }

        return i;
    }
}
