package net.minecraft.world.level.border;

import com.google.common.collect.Lists;
import com.mojang.serialization.DynamicLike;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WorldBorder {
    public static final double MAX_SIZE = 5.999997E7F;
    public static final double MAX_CENTER_COORDINATE = 2.9999984E7;
    private final List<BorderChangeListener> listeners = Lists.newArrayList();
    private double damagePerBlock = 0.2;
    private double damageSafeZone = 5.0;
    private int warningTime = 15;
    private int warningBlocks = 5;
    private double centerX;
    private double centerZ;
    int absoluteMaxSize = 29999984;
    private WorldBorder.BorderExtent extent = new WorldBorder.StaticBorderExtent(5.999997E7F);
    public static final WorldBorder.Settings DEFAULT_SETTINGS = new WorldBorder.Settings(0.0, 0.0, 0.2, 5.0, 5, 15, 5.999997E7F, 0L, 0.0);

    public boolean isWithinBounds(BlockPos pos) {
        return this.isWithinBounds((double)pos.getX(), (double)pos.getZ());
    }

    public boolean isWithinBounds(Vec3 pos) {
        return this.isWithinBounds(pos.x, pos.z);
    }

    public boolean isWithinBounds(ChunkPos chunkPos) {
        return this.isWithinBounds((double)chunkPos.getMinBlockX(), (double)chunkPos.getMinBlockZ())
            && this.isWithinBounds((double)chunkPos.getMaxBlockX(), (double)chunkPos.getMaxBlockZ());
    }

    public boolean isWithinBounds(AABB box) {
        return this.isWithinBounds(box.minX, box.minZ, box.maxX - 1.0E-5F, box.maxZ - 1.0E-5F);
    }

    private boolean isWithinBounds(double x1, double z1, double x2, double z2) {
        return this.isWithinBounds(x1, z1) && this.isWithinBounds(x2, z2);
    }

    public boolean isWithinBounds(double x, double z) {
        return this.isWithinBounds(x, z, 0.0);
    }

    public boolean isWithinBounds(double x, double z, double offset) {
        return x >= this.getMinX() - offset
            && x < this.getMaxX() + offset
            && z >= this.getMinZ() - offset
            && z < this.getMaxZ() + offset;
    }

    public BlockPos clampToBounds(BlockPos pos) {
        return this.clampToBounds((double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
    }

    public BlockPos clampToBounds(Vec3 pos) {
        return this.clampToBounds(pos.x(), pos.y(), pos.z());
    }

    public BlockPos clampToBounds(double x, double y, double z) {
        return BlockPos.containing(
            Mth.clamp(x, this.getMinX(), this.getMaxX() - 1.0), y, Mth.clamp(z, this.getMinZ(), this.getMaxZ() - 1.0)
        );
    }

    public double getDistanceToBorder(Entity entity) {
        return this.getDistanceToBorder(entity.getX(), entity.getZ());
    }

    public VoxelShape getCollisionShape() {
        return this.extent.getCollisionShape();
    }

    public double getDistanceToBorder(double x, double z) {
        double d0 = z - this.getMinZ();
        double d1 = this.getMaxZ() - z;
        double d2 = x - this.getMinX();
        double d3 = this.getMaxX() - x;
        double d4 = Math.min(d2, d3);
        d4 = Math.min(d4, d0);
        return Math.min(d4, d1);
    }

    public boolean isInsideCloseToBorder(Entity entity, AABB bounds) {
        double d0 = Math.max(Mth.absMax(bounds.getXsize(), bounds.getZsize()), 1.0);
        return this.getDistanceToBorder(entity) < d0 * 2.0 && this.isWithinBounds(entity.getX(), entity.getZ(), d0);
    }

    public BorderStatus getStatus() {
        return this.extent.getStatus();
    }

    public double getMinX() {
        return this.extent.getMinX();
    }

    public double getMinZ() {
        return this.extent.getMinZ();
    }

    public double getMaxX() {
        return this.extent.getMaxX();
    }

    public double getMaxZ() {
        return this.extent.getMaxZ();
    }

    public double getCenterX() {
        return this.centerX;
    }

    public double getCenterZ() {
        return this.centerZ;
    }

    public void setCenter(double x, double z) {
        this.centerX = x;
        this.centerZ = z;
        this.extent.onCenterChange();

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderCenterSet(this, x, z);
        }
    }

    public double getSize() {
        return this.extent.getSize();
    }

    public long getLerpRemainingTime() {
        return this.extent.getLerpRemainingTime();
    }

    public double getLerpTarget() {
        return this.extent.getLerpTarget();
    }

    public void setSize(double size) {
        this.extent = new WorldBorder.StaticBorderExtent(size);

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSizeSet(this, size);
        }
    }

    public void lerpSizeBetween(double oldSize, double newSize, long time) {
        this.extent = (WorldBorder.BorderExtent)(oldSize == newSize
            ? new WorldBorder.StaticBorderExtent(newSize)
            : new WorldBorder.MovingBorderExtent(oldSize, newSize, time));

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSizeLerping(this, oldSize, newSize, time);
        }
    }

    protected List<BorderChangeListener> getListeners() {
        return Lists.newArrayList(this.listeners);
    }

    public void addListener(BorderChangeListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(BorderChangeListener listener) {
        this.listeners.remove(listener);
    }

    public void setAbsoluteMaxSize(int size) {
        this.absoluteMaxSize = size;
        this.extent.onAbsoluteMaxSizeChange();
    }

    public int getAbsoluteMaxSize() {
        return this.absoluteMaxSize;
    }

    public double getDamageSafeZone() {
        return this.damageSafeZone;
    }

    public void setDamageSafeZone(double damageSafeZone) {
        this.damageSafeZone = damageSafeZone;

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSetDamageSafeZOne(this, damageSafeZone);
        }
    }

    public double getDamagePerBlock() {
        return this.damagePerBlock;
    }

    public void setDamagePerBlock(double damagePerBlock) {
        this.damagePerBlock = damagePerBlock;

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSetDamagePerBlock(this, damagePerBlock);
        }
    }

    public double getLerpSpeed() {
        return this.extent.getLerpSpeed();
    }

    public int getWarningTime() {
        return this.warningTime;
    }

    public void setWarningTime(int warningTime) {
        this.warningTime = warningTime;

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSetWarningTime(this, warningTime);
        }
    }

    public int getWarningBlocks() {
        return this.warningBlocks;
    }

    public void setWarningBlocks(int warningDistance) {
        this.warningBlocks = warningDistance;

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSetWarningBlocks(this, warningDistance);
        }
    }

    public void tick() {
        this.extent = this.extent.update();
    }

    public WorldBorder.Settings createSettings() {
        return new WorldBorder.Settings(this);
    }

    public void applySettings(WorldBorder.Settings serializer) {
        this.setCenter(serializer.getCenterX(), serializer.getCenterZ());
        this.setDamagePerBlock(serializer.getDamagePerBlock());
        this.setDamageSafeZone(serializer.getSafeZone());
        this.setWarningBlocks(serializer.getWarningBlocks());
        this.setWarningTime(serializer.getWarningTime());
        if (serializer.getSizeLerpTime() > 0L) {
            this.lerpSizeBetween(serializer.getSize(), serializer.getSizeLerpTarget(), serializer.getSizeLerpTime());
        } else {
            this.setSize(serializer.getSize());
        }
    }

    interface BorderExtent {
        double getMinX();

        double getMaxX();

        double getMinZ();

        double getMaxZ();

        double getSize();

        double getLerpSpeed();

        long getLerpRemainingTime();

        double getLerpTarget();

        BorderStatus getStatus();

        void onAbsoluteMaxSizeChange();

        void onCenterChange();

        WorldBorder.BorderExtent update();

        VoxelShape getCollisionShape();
    }

    class MovingBorderExtent implements WorldBorder.BorderExtent {
        private final double from;
        private final double to;
        private final long lerpEnd;
        private final long lerpBegin;
        private final double lerpDuration;

        MovingBorderExtent(double from, double to, long lerpDuration) {
            this.from = from;
            this.to = to;
            this.lerpDuration = (double)lerpDuration;
            this.lerpBegin = Util.getMillis();
            this.lerpEnd = this.lerpBegin + lerpDuration;
        }

        @Override
        public double getMinX() {
            return Mth.clamp(
                WorldBorder.this.getCenterX() - this.getSize() / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize
            );
        }

        @Override
        public double getMinZ() {
            return Mth.clamp(
                WorldBorder.this.getCenterZ() - this.getSize() / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize
            );
        }

        @Override
        public double getMaxX() {
            return Mth.clamp(
                WorldBorder.this.getCenterX() + this.getSize() / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize
            );
        }

        @Override
        public double getMaxZ() {
            return Mth.clamp(
                WorldBorder.this.getCenterZ() + this.getSize() / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize
            );
        }

        @Override
        public double getSize() {
            double d0 = (double)(Util.getMillis() - this.lerpBegin) / this.lerpDuration;
            return d0 < 1.0 ? Mth.lerp(d0, this.from, this.to) : this.to;
        }

        @Override
        public double getLerpSpeed() {
            return Math.abs(this.from - this.to) / (double)(this.lerpEnd - this.lerpBegin);
        }

        @Override
        public long getLerpRemainingTime() {
            return this.lerpEnd - Util.getMillis();
        }

        @Override
        public double getLerpTarget() {
            return this.to;
        }

        @Override
        public BorderStatus getStatus() {
            return this.to < this.from ? BorderStatus.SHRINKING : BorderStatus.GROWING;
        }

        @Override
        public void onCenterChange() {
        }

        @Override
        public void onAbsoluteMaxSizeChange() {
        }

        @Override
        public WorldBorder.BorderExtent update() {
            return (WorldBorder.BorderExtent)(this.getLerpRemainingTime() <= 0L ? WorldBorder.this.new StaticBorderExtent(this.to) : this);
        }

        @Override
        public VoxelShape getCollisionShape() {
            return Shapes.join(
                Shapes.INFINITY,
                Shapes.box(
                    Math.floor(this.getMinX()),
                    Double.NEGATIVE_INFINITY,
                    Math.floor(this.getMinZ()),
                    Math.ceil(this.getMaxX()),
                    Double.POSITIVE_INFINITY,
                    Math.ceil(this.getMaxZ())
                ),
                BooleanOp.ONLY_FIRST
            );
        }
    }

    public static class Settings {
        private final double centerX;
        private final double centerZ;
        private final double damagePerBlock;
        private final double safeZone;
        private final int warningBlocks;
        private final int warningTime;
        private final double size;
        private final long sizeLerpTime;
        private final double sizeLerpTarget;

        Settings(
            double centerX, double centerZ, double damagePerBlock, double safeZone, int warningBlocks, int warningTime, double size, long sizeLerpTime, double sizeLerpTarget
        ) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.damagePerBlock = damagePerBlock;
            this.safeZone = safeZone;
            this.warningBlocks = warningBlocks;
            this.warningTime = warningTime;
            this.size = size;
            this.sizeLerpTime = sizeLerpTime;
            this.sizeLerpTarget = sizeLerpTarget;
        }

        Settings(WorldBorder border) {
            this.centerX = border.getCenterX();
            this.centerZ = border.getCenterZ();
            this.damagePerBlock = border.getDamagePerBlock();
            this.safeZone = border.getDamageSafeZone();
            this.warningBlocks = border.getWarningBlocks();
            this.warningTime = border.getWarningTime();
            this.size = border.getSize();
            this.sizeLerpTime = border.getLerpRemainingTime();
            this.sizeLerpTarget = border.getLerpTarget();
        }

        public double getCenterX() {
            return this.centerX;
        }

        public double getCenterZ() {
            return this.centerZ;
        }

        public double getDamagePerBlock() {
            return this.damagePerBlock;
        }

        public double getSafeZone() {
            return this.safeZone;
        }

        public int getWarningBlocks() {
            return this.warningBlocks;
        }

        public int getWarningTime() {
            return this.warningTime;
        }

        public double getSize() {
            return this.size;
        }

        public long getSizeLerpTime() {
            return this.sizeLerpTime;
        }

        public double getSizeLerpTarget() {
            return this.sizeLerpTarget;
        }

        public static WorldBorder.Settings read(DynamicLike<?> dynamic, WorldBorder.Settings defaultValue) {
            double d0 = Mth.clamp(dynamic.get("BorderCenterX").asDouble(defaultValue.centerX), -2.9999984E7, 2.9999984E7);
            double d1 = Mth.clamp(dynamic.get("BorderCenterZ").asDouble(defaultValue.centerZ), -2.9999984E7, 2.9999984E7);
            double d2 = dynamic.get("BorderSize").asDouble(defaultValue.size);
            long i = dynamic.get("BorderSizeLerpTime").asLong(defaultValue.sizeLerpTime);
            double d3 = dynamic.get("BorderSizeLerpTarget").asDouble(defaultValue.sizeLerpTarget);
            double d4 = dynamic.get("BorderSafeZone").asDouble(defaultValue.safeZone);
            double d5 = dynamic.get("BorderDamagePerBlock").asDouble(defaultValue.damagePerBlock);
            int j = dynamic.get("BorderWarningBlocks").asInt(defaultValue.warningBlocks);
            int k = dynamic.get("BorderWarningTime").asInt(defaultValue.warningTime);
            return new WorldBorder.Settings(d0, d1, d5, d4, j, k, d2, i, d3);
        }

        public void write(CompoundTag nbt) {
            nbt.putDouble("BorderCenterX", this.centerX);
            nbt.putDouble("BorderCenterZ", this.centerZ);
            nbt.putDouble("BorderSize", this.size);
            nbt.putLong("BorderSizeLerpTime", this.sizeLerpTime);
            nbt.putDouble("BorderSafeZone", this.safeZone);
            nbt.putDouble("BorderDamagePerBlock", this.damagePerBlock);
            nbt.putDouble("BorderSizeLerpTarget", this.sizeLerpTarget);
            nbt.putDouble("BorderWarningBlocks", (double)this.warningBlocks);
            nbt.putDouble("BorderWarningTime", (double)this.warningTime);
        }
    }

    class StaticBorderExtent implements WorldBorder.BorderExtent {
        private final double size;
        private double minX;
        private double minZ;
        private double maxX;
        private double maxZ;
        private VoxelShape shape;

        public StaticBorderExtent(double size) {
            this.size = size;
            this.updateBox();
        }

        @Override
        public double getMinX() {
            return this.minX;
        }

        @Override
        public double getMaxX() {
            return this.maxX;
        }

        @Override
        public double getMinZ() {
            return this.minZ;
        }

        @Override
        public double getMaxZ() {
            return this.maxZ;
        }

        @Override
        public double getSize() {
            return this.size;
        }

        @Override
        public BorderStatus getStatus() {
            return BorderStatus.STATIONARY;
        }

        @Override
        public double getLerpSpeed() {
            return 0.0;
        }

        @Override
        public long getLerpRemainingTime() {
            return 0L;
        }

        @Override
        public double getLerpTarget() {
            return this.size;
        }

        private void updateBox() {
            this.minX = Mth.clamp(
                WorldBorder.this.getCenterX() - this.size / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize
            );
            this.minZ = Mth.clamp(
                WorldBorder.this.getCenterZ() - this.size / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize
            );
            this.maxX = Mth.clamp(
                WorldBorder.this.getCenterX() + this.size / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize
            );
            this.maxZ = Mth.clamp(
                WorldBorder.this.getCenterZ() + this.size / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize
            );
            this.shape = Shapes.join(
                Shapes.INFINITY,
                Shapes.box(
                    Math.floor(this.getMinX()),
                    Double.NEGATIVE_INFINITY,
                    Math.floor(this.getMinZ()),
                    Math.ceil(this.getMaxX()),
                    Double.POSITIVE_INFINITY,
                    Math.ceil(this.getMaxZ())
                ),
                BooleanOp.ONLY_FIRST
            );
        }

        @Override
        public void onAbsoluteMaxSizeChange() {
            this.updateBox();
        }

        @Override
        public void onCenterChange() {
            this.updateBox();
        }

        @Override
        public WorldBorder.BorderExtent update() {
            return this;
        }

        @Override
        public VoxelShape getCollisionShape() {
            return this.shape;
        }
    }
}
