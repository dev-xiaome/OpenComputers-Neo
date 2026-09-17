package net.minecraft.world.level.border;

public interface BorderChangeListener {
    void onBorderSizeSet(WorldBorder border, double size);

    void onBorderSizeLerping(WorldBorder border, double oldSize, double newSize, long time);

    void onBorderCenterSet(WorldBorder border, double x, double z);

    void onBorderSetWarningTime(WorldBorder border, int warningTime);

    void onBorderSetWarningBlocks(WorldBorder border, int warningBlocks);

    void onBorderSetDamagePerBlock(WorldBorder border, double damagePerBlock);

    void onBorderSetDamageSafeZOne(WorldBorder border, double damageSafeZone);

    public static class DelegateBorderChangeListener implements BorderChangeListener {
        private final WorldBorder worldBorder;

        public DelegateBorderChangeListener(WorldBorder worldBorder) {
            this.worldBorder = worldBorder;
        }

        @Override
        public void onBorderSizeSet(WorldBorder border, double newSize) {
            this.worldBorder.setSize(newSize);
        }

        @Override
        public void onBorderSizeLerping(WorldBorder border, double oldSize, double newSize, long time) {
            this.worldBorder.lerpSizeBetween(oldSize, newSize, time);
        }

        @Override
        public void onBorderCenterSet(WorldBorder border, double x, double z) {
            this.worldBorder.setCenter(x, z);
        }

        @Override
        public void onBorderSetWarningTime(WorldBorder border, int newTime) {
            this.worldBorder.setWarningTime(newTime);
        }

        @Override
        public void onBorderSetWarningBlocks(WorldBorder border, int newDistance) {
            this.worldBorder.setWarningBlocks(newDistance);
        }

        @Override
        public void onBorderSetDamagePerBlock(WorldBorder border, double newAmount) {
            this.worldBorder.setDamagePerBlock(newAmount);
        }

        @Override
        public void onBorderSetDamageSafeZOne(WorldBorder border, double newSize) {
            this.worldBorder.setDamageSafeZone(newSize);
        }
    }
}
