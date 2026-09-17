package net.minecraft.client.renderer.chunk;

import java.util.BitSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VisibilitySet {
    private static final int FACINGS = Direction.values().length;
    private final BitSet data = new BitSet(FACINGS * FACINGS);

    public void add(Set<Direction> faces) {
        for (Direction direction : faces) {
            for (Direction direction1 : faces) {
                this.set(direction, direction1, true);
            }
        }
    }

    public void set(Direction face, Direction otherFace, boolean visible) {
        this.data.set(face.ordinal() + otherFace.ordinal() * FACINGS, visible);
        this.data.set(otherFace.ordinal() + face.ordinal() * FACINGS, visible);
    }

    public void setAll(boolean visible) {
        this.data.set(0, this.data.size(), visible);
    }

    public boolean visibilityBetween(Direction face, Direction otherFace) {
        return this.data.get(face.ordinal() + otherFace.ordinal() * FACINGS);
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append(' ');

        for (Direction direction : Direction.values()) {
            stringbuilder.append(' ').append(direction.toString().toUpperCase().charAt(0));
        }

        stringbuilder.append('\n');

        for (Direction direction2 : Direction.values()) {
            stringbuilder.append(direction2.toString().toUpperCase().charAt(0));

            for (Direction direction1 : Direction.values()) {
                if (direction2 == direction1) {
                    stringbuilder.append("  ");
                } else {
                    boolean flag = this.visibilityBetween(direction2, direction1);
                    stringbuilder.append(' ').append((char)(flag ? 'Y' : 'n'));
                }
            }

            stringbuilder.append('\n');
        }

        return stringbuilder.toString();
    }
}
