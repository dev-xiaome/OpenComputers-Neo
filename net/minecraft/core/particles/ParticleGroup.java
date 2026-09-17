package net.minecraft.core.particles;

public class ParticleGroup {
    private final int limit;
    public static final ParticleGroup SPORE_BLOSSOM = new ParticleGroup(1000);

    public ParticleGroup(int limit) {
        this.limit = limit;
    }

    public int getLimit() {
        return this.limit;
    }
}
