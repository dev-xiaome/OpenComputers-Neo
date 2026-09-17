package net.minecraft.client.resources.model;

import com.mojang.math.OctahedralGroup;
import com.mojang.math.Transformation;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
public enum BlockModelRotation implements ModelState {
    X0_Y0(0, 0),
    X0_Y90(0, 90),
    X0_Y180(0, 180),
    X0_Y270(0, 270),
    X90_Y0(90, 0),
    X90_Y90(90, 90),
    X90_Y180(90, 180),
    X90_Y270(90, 270),
    X180_Y0(180, 0),
    X180_Y90(180, 90),
    X180_Y180(180, 180),
    X180_Y270(180, 270),
    X270_Y0(270, 0),
    X270_Y90(270, 90),
    X270_Y180(270, 180),
    X270_Y270(270, 270);

    private static final int DEGREES = 360;
    private static final Map<Integer, BlockModelRotation> BY_INDEX = Arrays.stream(values())
        .collect(Collectors.toMap(p_119163_ -> p_119163_.index, p_119157_ -> (BlockModelRotation)p_119157_));
    private final Transformation transformation;
    private final OctahedralGroup actualRotation;
    private final int index;

    private static int getIndex(int x, int y) {
        return x * 360 + y;
    }

    private BlockModelRotation(int x, int y) {
        this.index = getIndex(x, y);
        Quaternionf quaternionf = new Quaternionf()
            .rotateYXZ((float)(-y) * (float) (Math.PI / 180.0), (float)(-x) * (float) (Math.PI / 180.0), 0.0F);
        OctahedralGroup octahedralgroup = OctahedralGroup.IDENTITY;

        for (int i = 0; i < y; i += 90) {
            octahedralgroup = octahedralgroup.compose(OctahedralGroup.ROT_90_Y_NEG);
        }

        for (int j = 0; j < x; j += 90) {
            octahedralgroup = octahedralgroup.compose(OctahedralGroup.ROT_90_X_NEG);
        }

        this.transformation = new Transformation(null, quaternionf, null, null);
        this.actualRotation = octahedralgroup;
    }

    @Override
    public Transformation getRotation() {
        return this.transformation;
    }

    public static BlockModelRotation by(int x, int y) {
        return BY_INDEX.get(getIndex(Mth.positiveModulo(x, 360), Mth.positiveModulo(y, 360)));
    }

    public OctahedralGroup actualRotation() {
        return this.actualRotation;
    }
}
