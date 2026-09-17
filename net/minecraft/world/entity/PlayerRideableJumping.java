package net.minecraft.world.entity;

public interface PlayerRideableJumping extends PlayerRideable {
    void onPlayerJump(int jumpPower);

    boolean canJump();

    void handleStartJump(int jumpPower);

    void handleStopJump();

    default int getJumpCooldown() {
        return 0;
    }
}
