package net.minecraft.client.model.geom.builders;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CubeListBuilder {
    private static final Set<Direction> ALL_VISIBLE = EnumSet.allOf(Direction.class);
    private final List<CubeDefinition> cubes = Lists.newArrayList();
    private int xTexOffs;
    private int yTexOffs;
    private boolean mirror;

    public CubeListBuilder texOffs(int xTexOffs, int yTexOffs) {
        this.xTexOffs = xTexOffs;
        this.yTexOffs = yTexOffs;
        return this;
    }

    public CubeListBuilder mirror() {
        return this.mirror(true);
    }

    public CubeListBuilder mirror(boolean mirror) {
        this.mirror = mirror;
        return this;
    }

    public CubeListBuilder addBox(
        String comment,
        float originX,
        float originY,
        float originZ,
        int dimensionX,
        int dimensionY,
        int dimensionZ,
        CubeDeformation cubeDeformation,
        int xTexOffs,
        int yTexOffs
    ) {
        this.texOffs(xTexOffs, yTexOffs);
        this.cubes
            .add(
                new CubeDefinition(
                    comment,
                    (float)this.xTexOffs,
                    (float)this.yTexOffs,
                    originX,
                    originY,
                    originZ,
                    (float)dimensionX,
                    (float)dimensionY,
                    (float)dimensionZ,
                    cubeDeformation,
                    this.mirror,
                    1.0F,
                    1.0F,
                    ALL_VISIBLE
                )
            );
        return this;
    }

    public CubeListBuilder addBox(
        String comment, float originX, float originY, float originZ, int dimensionX, int dimensionY, int dimensionZ, int xTexOffs, int yTexOffs
    ) {
        this.texOffs(xTexOffs, yTexOffs);
        this.cubes
            .add(
                new CubeDefinition(
                    comment,
                    (float)this.xTexOffs,
                    (float)this.yTexOffs,
                    originX,
                    originY,
                    originZ,
                    (float)dimensionX,
                    (float)dimensionY,
                    (float)dimensionZ,
                    CubeDeformation.NONE,
                    this.mirror,
                    1.0F,
                    1.0F,
                    ALL_VISIBLE
                )
            );
        return this;
    }

    public CubeListBuilder addBox(float originX, float originY, float originZ, float dimensionX, float dimensionY, float dimensionZ) {
        this.cubes
            .add(
                new CubeDefinition(
                    null,
                    (float)this.xTexOffs,
                    (float)this.yTexOffs,
                    originX,
                    originY,
                    originZ,
                    dimensionX,
                    dimensionY,
                    dimensionZ,
                    CubeDeformation.NONE,
                    this.mirror,
                    1.0F,
                    1.0F,
                    ALL_VISIBLE
                )
            );
        return this;
    }

    public CubeListBuilder addBox(
        float originX, float originY, float originZ, float dimensionX, float dimensionY, float dimensionZ, Set<Direction> visibleFaces
    ) {
        this.cubes
            .add(
                new CubeDefinition(
                    null,
                    (float)this.xTexOffs,
                    (float)this.yTexOffs,
                    originX,
                    originY,
                    originZ,
                    dimensionX,
                    dimensionY,
                    dimensionZ,
                    CubeDeformation.NONE,
                    this.mirror,
                    1.0F,
                    1.0F,
                    visibleFaces
                )
            );
        return this;
    }

    public CubeListBuilder addBox(String comment, float originX, float originY, float originZ, float dimensionX, float dimensionY, float dimensionZ) {
        this.cubes
            .add(
                new CubeDefinition(
                    comment,
                    (float)this.xTexOffs,
                    (float)this.yTexOffs,
                    originX,
                    originY,
                    originZ,
                    dimensionX,
                    dimensionY,
                    dimensionZ,
                    CubeDeformation.NONE,
                    this.mirror,
                    1.0F,
                    1.0F,
                    ALL_VISIBLE
                )
            );
        return this;
    }

    public CubeListBuilder addBox(
        String comment, float originX, float originY, float originZ, float dimensionX, float dimensionY, float dimensionZ, CubeDeformation cubeDeformation
    ) {
        this.cubes
            .add(
                new CubeDefinition(
                    comment,
                    (float)this.xTexOffs,
                    (float)this.yTexOffs,
                    originX,
                    originY,
                    originZ,
                    dimensionX,
                    dimensionY,
                    dimensionZ,
                    cubeDeformation,
                    this.mirror,
                    1.0F,
                    1.0F,
                    ALL_VISIBLE
                )
            );
        return this;
    }

    public CubeListBuilder addBox(float originX, float originY, float originZ, float dimensionX, float dimensionY, float dimensionZ, boolean mirror) {
        this.cubes
            .add(
                new CubeDefinition(
                    null,
                    (float)this.xTexOffs,
                    (float)this.yTexOffs,
                    originX,
                    originY,
                    originZ,
                    dimensionX,
                    dimensionY,
                    dimensionZ,
                    CubeDeformation.NONE,
                    mirror,
                    1.0F,
                    1.0F,
                    ALL_VISIBLE
                )
            );
        return this;
    }

    public CubeListBuilder addBox(
        float originX,
        float originY,
        float originZ,
        float dimensionX,
        float dimensionY,
        float dimensionZ,
        CubeDeformation cubeDeformation,
        float texScaleU,
        float texScaleV
    ) {
        this.cubes
            .add(
                new CubeDefinition(
                    null,
                    (float)this.xTexOffs,
                    (float)this.yTexOffs,
                    originX,
                    originY,
                    originZ,
                    dimensionX,
                    dimensionY,
                    dimensionZ,
                    cubeDeformation,
                    this.mirror,
                    texScaleU,
                    texScaleV,
                    ALL_VISIBLE
                )
            );
        return this;
    }

    public CubeListBuilder addBox(
        float originX, float originY, float originZ, float dimensionX, float dimensionY, float dimensionZ, CubeDeformation cubeDeformation
    ) {
        this.cubes
            .add(
                new CubeDefinition(
                    null,
                    (float)this.xTexOffs,
                    (float)this.yTexOffs,
                    originX,
                    originY,
                    originZ,
                    dimensionX,
                    dimensionY,
                    dimensionZ,
                    cubeDeformation,
                    this.mirror,
                    1.0F,
                    1.0F,
                    ALL_VISIBLE
                )
            );
        return this;
    }

    public List<CubeDefinition> getCubes() {
        return ImmutableList.copyOf(this.cubes);
    }

    public static CubeListBuilder create() {
        return new CubeListBuilder();
    }
}
