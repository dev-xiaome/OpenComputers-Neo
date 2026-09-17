package net.minecraft.world.entity;

import net.minecraft.util.Mth;

public class WalkAnimationState {
    private float speedOld;
    private float speed;
    private float position;

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void update(float newSpeed, float partialTick) {
        this.speedOld = this.speed;
        this.speed = this.speed + (newSpeed - this.speed) * partialTick;
        this.position = this.position + this.speed;
    }

    public float speed() {
        return this.speed;
    }

    public float speed(float partialTick) {
        return Mth.lerp(partialTick, this.speedOld, this.speed);
    }

    public float position() {
        return this.position;
    }

    public float position(float partialTick) {
        return this.position - this.speed * (1.0F - partialTick);
    }

    public boolean isMoving() {
        return this.speed > 1.0E-5F;
    }
}
