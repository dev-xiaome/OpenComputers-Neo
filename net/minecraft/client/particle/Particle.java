package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class Particle {
    private static final AABB INITIAL_AABB = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    private static final double MAXIMUM_COLLISION_VELOCITY_SQUARED = Mth.square(100.0);
    protected final ClientLevel level;
    protected double xo;
    protected double yo;
    protected double zo;
    protected double x;
    protected double y;
    protected double z;
    protected double xd;
    protected double yd;
    protected double zd;
    private AABB bb = INITIAL_AABB;
    protected boolean onGround;
    protected boolean hasPhysics = true;
    private boolean stoppedByCollision;
    protected boolean removed;
    protected float bbWidth = 0.6F;
    protected float bbHeight = 1.8F;
    protected final RandomSource random = RandomSource.create();
    protected int age;
    protected int lifetime;
    protected float gravity;
    protected float rCol = 1.0F;
    protected float gCol = 1.0F;
    protected float bCol = 1.0F;
    protected float alpha = 1.0F;
    protected float roll;
    protected float oRoll;
    protected float friction = 0.98F;
    protected boolean speedUpWhenYMotionIsBlocked = false;

    protected Particle(ClientLevel level, double x, double y, double z) {
        this.level = level;
        this.setSize(0.2F, 0.2F);
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.lifetime = (int)(4.0F / (this.random.nextFloat() * 0.9F + 0.1F));
    }

    public Particle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        this(level, x, y, z);
        this.xd = xSpeed + (Math.random() * 2.0 - 1.0) * 0.4F;
        this.yd = ySpeed + (Math.random() * 2.0 - 1.0) * 0.4F;
        this.zd = zSpeed + (Math.random() * 2.0 - 1.0) * 0.4F;
        double d0 = (Math.random() + Math.random() + 1.0) * 0.15F;
        double d1 = Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        this.xd = this.xd / d1 * d0 * 0.4F;
        this.yd = this.yd / d1 * d0 * 0.4F + 0.1F;
        this.zd = this.zd / d1 * d0 * 0.4F;
    }

    public Particle setPower(float multiplier) {
        this.xd *= (double)multiplier;
        this.yd = (this.yd - 0.1F) * (double)multiplier + 0.1F;
        this.zd *= (double)multiplier;
        return this;
    }

    public void setParticleSpeed(double xd, double yd, double zd) {
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
    }

    public Particle scale(float scale) {
        this.setSize(0.2F * scale, 0.2F * scale);
        return this;
    }

    public void setColor(float particleRed, float particleGreen, float particleBlue) {
        this.rCol = particleRed;
        this.gCol = particleGreen;
        this.bCol = particleBlue;
    }

    /**
     * Sets the particle alpha (float)
     */
    protected void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public void setLifetime(int particleLifeTime) {
        this.lifetime = particleLifeTime;
    }

    public int getLifetime() {
        return this.lifetime;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.yd = this.yd - 0.04 * (double)this.gravity;
            this.move(this.xd, this.yd, this.zd);
            if (this.speedUpWhenYMotionIsBlocked && this.y == this.yo) {
                this.xd *= 1.1;
                this.zd *= 1.1;
            }

            this.xd = this.xd * (double)this.friction;
            this.yd = this.yd * (double)this.friction;
            this.zd = this.zd * (double)this.friction;
            if (this.onGround) {
                this.xd *= 0.7F;
                this.zd *= 0.7F;
            }
        }
    }

    public abstract void render(VertexConsumer buffer, Camera camera, float partialTicks);

    public abstract ParticleRenderType getRenderType();

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
            + ", Pos ("
            + this.x
            + ","
            + this.y
            + ","
            + this.z
            + "), RGBA ("
            + this.rCol
            + ","
            + this.gCol
            + ","
            + this.bCol
            + ","
            + this.alpha
            + "), Age "
            + this.age;
    }

    public void remove() {
        this.removed = true;
    }

    protected void setSize(float width, float height) {
        if (width != this.bbWidth || height != this.bbHeight) {
            this.bbWidth = width;
            this.bbHeight = height;
            AABB aabb = this.getBoundingBox();
            double d0 = (aabb.minX + aabb.maxX - (double)width) / 2.0;
            double d1 = (aabb.minZ + aabb.maxZ - (double)width) / 2.0;
            this.setBoundingBox(new AABB(d0, aabb.minY, d1, d0 + (double)this.bbWidth, aabb.minY + (double)this.bbHeight, d1 + (double)this.bbWidth));
        }
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        float f = this.bbWidth / 2.0F;
        float f1 = this.bbHeight;
        this.setBoundingBox(
            new AABB(x - (double)f, y, z - (double)f, x + (double)f, y + (double)f1, z + (double)f)
        );
    }

    public void move(double x, double y, double z) {
        if (!this.stoppedByCollision) {
            double d0 = x;
            double d1 = y;
            double d2 = z;
            if (this.hasPhysics
                && (x != 0.0 || y != 0.0 || z != 0.0)
                && x * x + y * y + z * z < MAXIMUM_COLLISION_VELOCITY_SQUARED) {
                Vec3 vec3 = Entity.collideBoundingBox(null, new Vec3(x, y, z), this.getBoundingBox(), this.level, List.of());
                x = vec3.x;
                y = vec3.y;
                z = vec3.z;
            }

            if (x != 0.0 || y != 0.0 || z != 0.0) {
                this.setBoundingBox(this.getBoundingBox().move(x, y, z));
                this.setLocationFromBoundingbox();
            }

            if (Math.abs(d1) >= 1.0E-5F && Math.abs(y) < 1.0E-5F) {
                this.stoppedByCollision = true;
            }

            this.onGround = d1 != y && d1 < 0.0;
            if (d0 != x) {
                this.xd = 0.0;
            }

            if (d2 != z) {
                this.zd = 0.0;
            }
        }
    }

    protected void setLocationFromBoundingbox() {
        AABB aabb = this.getBoundingBox();
        this.x = (aabb.minX + aabb.maxX) / 2.0;
        this.y = aabb.minY;
        this.z = (aabb.minZ + aabb.maxZ) / 2.0;
    }

    protected int getLightColor(float partialTick) {
        BlockPos blockpos = BlockPos.containing(this.x, this.y, this.z);
        return this.level.hasChunkAt(blockpos) ? LevelRenderer.getLightColor(this.level, blockpos) : 0;
    }

    public boolean isAlive() {
        return !this.removed;
    }

    public AABB getBoundingBox() {
        return this.bb;
    }

    public void setBoundingBox(AABB bb) {
        this.bb = bb;
    }

    public Optional<ParticleGroup> getParticleGroup() {
        return Optional.empty();
    }

    /**
     * Returns the bounding box that should be used for particle culling. {@link AABB#INFINITE} can be
     * returned for particles that should not be culled.
     */
    public AABB getRenderBoundingBox(float partialTicks) {
        return getBoundingBox().inflate(1.0);
    }

    public Vec3 getPos() {
        return new Vec3(this.x, this.y, this.z);
    }

    @OnlyIn(Dist.CLIENT)
    public static record LifetimeAlpha(float startAlpha, float endAlpha, float startAtNormalizedAge, float endAtNormalizedAge) {
        public static final Particle.LifetimeAlpha ALWAYS_OPAQUE = new Particle.LifetimeAlpha(1.0F, 1.0F, 0.0F, 1.0F);

        public boolean isOpaque() {
            return this.startAlpha >= 1.0F && this.endAlpha >= 1.0F;
        }

        public float currentAlphaForAge(int age, int lifetime, float partialTick) {
            if (Mth.equal(this.startAlpha, this.endAlpha)) {
                return this.startAlpha;
            } else {
                float f = Mth.inverseLerp(((float)age + partialTick) / (float)lifetime, this.startAtNormalizedAge, this.endAtNormalizedAge);
                return Mth.clampedLerp(this.startAlpha, this.endAlpha, f);
            }
        }
    }
}
