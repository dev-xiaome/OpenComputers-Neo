package net.minecraft.client.model.geom.builders;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class UVPair {
    private final float u;
    private final float v;

    public UVPair(float u, float v) {
        this.u = u;
        this.v = v;
    }

    public float u() {
        return this.u;
    }

    public float v() {
        return this.v;
    }

    @Override
    public String toString() {
        return "(" + this.u + "," + this.v + ")";
    }
}
