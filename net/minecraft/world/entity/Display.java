package net.minecraft.world.entity;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Brightness;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

public abstract class Display extends Entity {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final int NO_BRIGHTNESS_OVERRIDE = -1;
    private static final EntityDataAccessor<Integer> DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID = SynchedEntityData.defineId(
        Display.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID = SynchedEntityData.defineId(
        Display.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> DATA_POS_ROT_INTERPOLATION_DURATION_ID = SynchedEntityData.defineId(
        Display.class, EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Vector3f> DATA_TRANSLATION_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DATA_SCALE_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Quaternionf> DATA_LEFT_ROTATION_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Quaternionf> DATA_RIGHT_ROTATION_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Byte> DATA_BILLBOARD_RENDER_CONSTRAINTS_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_BRIGHTNESS_OVERRIDE_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_VIEW_RANGE_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SHADOW_RADIUS_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SHADOW_STRENGTH_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WIDTH_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GLOW_COLOR_OVERRIDE_ID = SynchedEntityData.defineId(Display.class, EntityDataSerializers.INT);
    private static final IntSet RENDER_STATE_IDS = IntSet.of(
        DATA_TRANSLATION_ID.id(),
        DATA_SCALE_ID.id(),
        DATA_LEFT_ROTATION_ID.id(),
        DATA_RIGHT_ROTATION_ID.id(),
        DATA_BILLBOARD_RENDER_CONSTRAINTS_ID.id(),
        DATA_BRIGHTNESS_OVERRIDE_ID.id(),
        DATA_SHADOW_RADIUS_ID.id(),
        DATA_SHADOW_STRENGTH_ID.id()
    );
    private static final float INITIAL_SHADOW_RADIUS = 0.0F;
    private static final float INITIAL_SHADOW_STRENGTH = 1.0F;
    private static final int NO_GLOW_COLOR_OVERRIDE = -1;
    public static final String TAG_POS_ROT_INTERPOLATION_DURATION = "teleport_duration";
    public static final String TAG_TRANSFORMATION_INTERPOLATION_DURATION = "interpolation_duration";
    public static final String TAG_TRANSFORMATION_START_INTERPOLATION = "start_interpolation";
    public static final String TAG_TRANSFORMATION = "transformation";
    public static final String TAG_BILLBOARD = "billboard";
    public static final String TAG_BRIGHTNESS = "brightness";
    public static final String TAG_VIEW_RANGE = "view_range";
    public static final String TAG_SHADOW_RADIUS = "shadow_radius";
    public static final String TAG_SHADOW_STRENGTH = "shadow_strength";
    public static final String TAG_WIDTH = "width";
    public static final String TAG_HEIGHT = "height";
    public static final String TAG_GLOW_COLOR_OVERRIDE = "glow_color_override";
    private long interpolationStartClientTick = -2147483648L;
    private int interpolationDuration;
    private float lastProgress;
    private AABB cullingBoundingBox;
    protected boolean updateRenderState;
    private boolean updateStartTick;
    private boolean updateInterpolationDuration;
    @Nullable
    private Display.RenderState renderState;
    @Nullable
    private Display.PosRotInterpolationTarget posRotInterpolationTarget;

    public Display(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.cullingBoundingBox = this.getBoundingBox();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HEIGHT_ID.equals(key) || DATA_WIDTH_ID.equals(key)) {
            this.updateCulling();
        }

        if (DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID.equals(key)) {
            this.updateStartTick = true;
        }

        if (DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID.equals(key)) {
            this.updateInterpolationDuration = true;
        }

        if (RENDER_STATE_IDS.contains(key.id())) {
            this.updateRenderState = true;
        }
    }

    private static Transformation createTransformation(SynchedEntityData synchedEntityData) {
        Vector3f vector3f = synchedEntityData.get(DATA_TRANSLATION_ID);
        Quaternionf quaternionf = synchedEntityData.get(DATA_LEFT_ROTATION_ID);
        Vector3f vector3f1 = synchedEntityData.get(DATA_SCALE_ID);
        Quaternionf quaternionf1 = synchedEntityData.get(DATA_RIGHT_ROTATION_ID);
        return new Transformation(vector3f, quaternionf, vector3f1, quaternionf1);
    }

    @Override
    public void tick() {
        Entity entity = this.getVehicle();
        if (entity != null && entity.isRemoved()) {
            this.stopRiding();
        }

        if (this.level().isClientSide) {
            if (this.updateStartTick) {
                this.updateStartTick = false;
                int i = this.getTransformationInterpolationDelay();
                this.interpolationStartClientTick = (long)(this.tickCount + i);
            }

            if (this.updateInterpolationDuration) {
                this.updateInterpolationDuration = false;
                this.interpolationDuration = this.getTransformationInterpolationDuration();
            }

            if (this.updateRenderState) {
                this.updateRenderState = false;
                boolean flag = this.interpolationDuration != 0;
                if (flag && this.renderState != null) {
                    this.renderState = this.createInterpolatedRenderState(this.renderState, this.lastProgress);
                } else {
                    this.renderState = this.createFreshRenderState();
                }

                this.updateRenderSubState(flag, this.lastProgress);
            }

            if (this.posRotInterpolationTarget != null) {
                if (this.posRotInterpolationTarget.steps == 0) {
                    this.posRotInterpolationTarget.applyTargetPosAndRot(this);
                    this.setOldPosAndRot();
                    this.posRotInterpolationTarget = null;
                } else {
                    this.posRotInterpolationTarget.applyLerpStep(this);
                    this.posRotInterpolationTarget.steps--;
                    if (this.posRotInterpolationTarget.steps == 0) {
                        this.posRotInterpolationTarget = null;
                    }
                }
            }
        }
    }

    protected abstract void updateRenderSubState(boolean interpolate, float partialTick);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_POS_ROT_INTERPOLATION_DURATION_ID, 0);
        builder.define(DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID, 0);
        builder.define(DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID, 0);
        builder.define(DATA_TRANSLATION_ID, new Vector3f());
        builder.define(DATA_SCALE_ID, new Vector3f(1.0F, 1.0F, 1.0F));
        builder.define(DATA_RIGHT_ROTATION_ID, new Quaternionf());
        builder.define(DATA_LEFT_ROTATION_ID, new Quaternionf());
        builder.define(DATA_BILLBOARD_RENDER_CONSTRAINTS_ID, Display.BillboardConstraints.FIXED.getId());
        builder.define(DATA_BRIGHTNESS_OVERRIDE_ID, -1);
        builder.define(DATA_VIEW_RANGE_ID, 1.0F);
        builder.define(DATA_SHADOW_RADIUS_ID, 0.0F);
        builder.define(DATA_SHADOW_STRENGTH_ID, 1.0F);
        builder.define(DATA_WIDTH_ID, 0.0F);
        builder.define(DATA_HEIGHT_ID, 0.0F);
        builder.define(DATA_GLOW_COLOR_OVERRIDE_ID, -1);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains("transformation")) {
            Transformation.EXTENDED_CODEC
                .decode(NbtOps.INSTANCE, compound.get("transformation"))
                .resultOrPartial(Util.prefix("Display entity", LOGGER::error))
                .ifPresent(p_270952_ -> this.setTransformation(p_270952_.getFirst()));
        }

        if (compound.contains("interpolation_duration", 99)) {
            int i = compound.getInt("interpolation_duration");
            this.setTransformationInterpolationDuration(i);
        }

        if (compound.contains("start_interpolation", 99)) {
            int j = compound.getInt("start_interpolation");
            this.setTransformationInterpolationDelay(j);
        }

        if (compound.contains("teleport_duration", 99)) {
            int k = compound.getInt("teleport_duration");
            this.setPosRotInterpolationDuration(Mth.clamp(k, 0, 59));
        }

        if (compound.contains("billboard", 8)) {
            Display.BillboardConstraints.CODEC
                .decode(NbtOps.INSTANCE, compound.get("billboard"))
                .resultOrPartial(Util.prefix("Display entity", LOGGER::error))
                .ifPresent(p_270691_ -> this.setBillboardConstraints(p_270691_.getFirst()));
        }

        if (compound.contains("view_range", 99)) {
            this.setViewRange(compound.getFloat("view_range"));
        }

        if (compound.contains("shadow_radius", 99)) {
            this.setShadowRadius(compound.getFloat("shadow_radius"));
        }

        if (compound.contains("shadow_strength", 99)) {
            this.setShadowStrength(compound.getFloat("shadow_strength"));
        }

        if (compound.contains("width", 99)) {
            this.setWidth(compound.getFloat("width"));
        }

        if (compound.contains("height", 99)) {
            this.setHeight(compound.getFloat("height"));
        }

        if (compound.contains("glow_color_override", 99)) {
            this.setGlowColorOverride(compound.getInt("glow_color_override"));
        }

        if (compound.contains("brightness", 10)) {
            Brightness.CODEC
                .decode(NbtOps.INSTANCE, compound.get("brightness"))
                .resultOrPartial(Util.prefix("Display entity", LOGGER::error))
                .ifPresent(p_270247_ -> this.setBrightnessOverride(p_270247_.getFirst()));
        } else {
            this.setBrightnessOverride(null);
        }
    }

    private void setTransformation(Transformation transformation) {
        this.entityData.set(DATA_TRANSLATION_ID, transformation.getTranslation());
        this.entityData.set(DATA_LEFT_ROTATION_ID, transformation.getLeftRotation());
        this.entityData.set(DATA_SCALE_ID, transformation.getScale());
        this.entityData.set(DATA_RIGHT_ROTATION_ID, transformation.getRightRotation());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        Transformation.EXTENDED_CODEC
            .encodeStart(NbtOps.INSTANCE, createTransformation(this.entityData))
            .ifSuccess(p_270528_ -> compound.put("transformation", p_270528_));
        Display.BillboardConstraints.CODEC
            .encodeStart(NbtOps.INSTANCE, this.getBillboardConstraints())
            .ifSuccess(p_270227_ -> compound.put("billboard", p_270227_));
        compound.putInt("interpolation_duration", this.getTransformationInterpolationDuration());
        compound.putInt("teleport_duration", this.getPosRotInterpolationDuration());
        compound.putFloat("view_range", this.getViewRange());
        compound.putFloat("shadow_radius", this.getShadowRadius());
        compound.putFloat("shadow_strength", this.getShadowStrength());
        compound.putFloat("width", this.getWidth());
        compound.putFloat("height", this.getHeight());
        compound.putInt("glow_color_override", this.getGlowColorOverride());
        Brightness brightness = this.getBrightnessOverride();
        if (brightness != null) {
            Brightness.CODEC.encodeStart(NbtOps.INSTANCE, brightness).ifSuccess(p_270121_ -> compound.put("brightness", p_270121_));
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        int i = this.getPosRotInterpolationDuration();
        this.posRotInterpolationTarget = new Display.PosRotInterpolationTarget(i, x, y, z, (double)yRot, (double)xRot);
    }

    @Override
    public double lerpTargetX() {
        return this.posRotInterpolationTarget != null ? this.posRotInterpolationTarget.targetX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.posRotInterpolationTarget != null ? this.posRotInterpolationTarget.targetY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.posRotInterpolationTarget != null ? this.posRotInterpolationTarget.targetZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.posRotInterpolationTarget != null ? (float)this.posRotInterpolationTarget.targetXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.posRotInterpolationTarget != null ? (float)this.posRotInterpolationTarget.targetYRot : this.getYRot();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.cullingBoundingBox;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Nullable
    public Display.RenderState renderState() {
        return this.renderState;
    }

    private void setTransformationInterpolationDuration(int transformationInterpolationDuration) {
        this.entityData.set(DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID, transformationInterpolationDuration);
    }

    private int getTransformationInterpolationDuration() {
        return this.entityData.get(DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID);
    }

    private void setTransformationInterpolationDelay(int transformationInterpolationDelay) {
        this.entityData.set(DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID, transformationInterpolationDelay, true);
    }

    private int getTransformationInterpolationDelay() {
        return this.entityData.get(DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID);
    }

    private void setPosRotInterpolationDuration(int posRotInterpolationDuration) {
        this.entityData.set(DATA_POS_ROT_INTERPOLATION_DURATION_ID, posRotInterpolationDuration);
    }

    private int getPosRotInterpolationDuration() {
        return this.entityData.get(DATA_POS_ROT_INTERPOLATION_DURATION_ID);
    }

    private void setBillboardConstraints(Display.BillboardConstraints billboardConstraints) {
        this.entityData.set(DATA_BILLBOARD_RENDER_CONSTRAINTS_ID, billboardConstraints.getId());
    }

    private Display.BillboardConstraints getBillboardConstraints() {
        return Display.BillboardConstraints.BY_ID.apply(this.entityData.get(DATA_BILLBOARD_RENDER_CONSTRAINTS_ID));
    }

    private void setBrightnessOverride(@Nullable Brightness brightnessOverride) {
        this.entityData.set(DATA_BRIGHTNESS_OVERRIDE_ID, brightnessOverride != null ? brightnessOverride.pack() : -1);
    }

    @Nullable
    private Brightness getBrightnessOverride() {
        int i = this.entityData.get(DATA_BRIGHTNESS_OVERRIDE_ID);
        return i != -1 ? Brightness.unpack(i) : null;
    }

    private int getPackedBrightnessOverride() {
        return this.entityData.get(DATA_BRIGHTNESS_OVERRIDE_ID);
    }

    private void setViewRange(float viewRange) {
        this.entityData.set(DATA_VIEW_RANGE_ID, viewRange);
    }

    private float getViewRange() {
        return this.entityData.get(DATA_VIEW_RANGE_ID);
    }

    private void setShadowRadius(float shadowRadius) {
        this.entityData.set(DATA_SHADOW_RADIUS_ID, shadowRadius);
    }

    private float getShadowRadius() {
        return this.entityData.get(DATA_SHADOW_RADIUS_ID);
    }

    private void setShadowStrength(float shadowStrength) {
        this.entityData.set(DATA_SHADOW_STRENGTH_ID, shadowStrength);
    }

    private float getShadowStrength() {
        return this.entityData.get(DATA_SHADOW_STRENGTH_ID);
    }

    private void setWidth(float width) {
        this.entityData.set(DATA_WIDTH_ID, width);
    }

    private float getWidth() {
        return this.entityData.get(DATA_WIDTH_ID);
    }

    private void setHeight(float height) {
        this.entityData.set(DATA_HEIGHT_ID, height);
    }

    private int getGlowColorOverride() {
        return this.entityData.get(DATA_GLOW_COLOR_OVERRIDE_ID);
    }

    private void setGlowColorOverride(int glowColorOverride) {
        this.entityData.set(DATA_GLOW_COLOR_OVERRIDE_ID, glowColorOverride);
    }

    public float calculateInterpolationProgress(float partialTick) {
        int i = this.interpolationDuration;
        if (i <= 0) {
            return 1.0F;
        } else {
            float f = (float)((long)this.tickCount - this.interpolationStartClientTick);
            float f1 = f + partialTick;
            float f2 = Mth.clamp(Mth.inverseLerp(f1, 0.0F, (float)i), 0.0F, 1.0F);
            this.lastProgress = f2;
            return f2;
        }
    }

    private float getHeight() {
        return this.entityData.get(DATA_HEIGHT_ID);
    }

    /**
     * Sets the x,y,z of the entity from the given parameters. Also seems to set up a bounding box.
     */
    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        this.updateCulling();
    }

    private void updateCulling() {
        float f = this.getWidth();
        float f1 = this.getHeight();
        if (f != 0.0F && f1 != 0.0F) {
            this.noCulling = false;
            float f2 = f / 2.0F;
            double d0 = this.getX();
            double d1 = this.getY();
            double d2 = this.getZ();
            this.cullingBoundingBox = new AABB(d0 - (double)f2, d1, d2 - (double)f2, d0 + (double)f2, d1 + (double)f1, d2 + (double)f2);
        } else {
            this.noCulling = true;
        }
    }

    /**
     * Checks if the entity is in range to render.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < Mth.square((double)this.getViewRange() * 64.0 * getViewScale());
    }

    @Override
    public int getTeamColor() {
        int i = this.getGlowColorOverride();
        return i != -1 ? i : super.getTeamColor();
    }

    private Display.RenderState createFreshRenderState() {
        return new Display.RenderState(
            Display.GenericInterpolator.constant(createTransformation(this.entityData)),
            this.getBillboardConstraints(),
            this.getPackedBrightnessOverride(),
            Display.FloatInterpolator.constant(this.getShadowRadius()),
            Display.FloatInterpolator.constant(this.getShadowStrength()),
            this.getGlowColorOverride()
        );
    }

    private Display.RenderState createInterpolatedRenderState(Display.RenderState renderState, float partialTick) {
        Transformation transformation = renderState.transformation.get(partialTick);
        float f = renderState.shadowRadius.get(partialTick);
        float f1 = renderState.shadowStrength.get(partialTick);
        return new Display.RenderState(
            new Display.TransformationInterpolator(transformation, createTransformation(this.entityData)),
            this.getBillboardConstraints(),
            this.getPackedBrightnessOverride(),
            new Display.LinearFloatInterpolator(f, this.getShadowRadius()),
            new Display.LinearFloatInterpolator(f1, this.getShadowStrength()),
            this.getGlowColorOverride()
        );
    }

    public static enum BillboardConstraints implements StringRepresentable {
        FIXED((byte)0, "fixed"),
        VERTICAL((byte)1, "vertical"),
        HORIZONTAL((byte)2, "horizontal"),
        CENTER((byte)3, "center");

        public static final Codec<Display.BillboardConstraints> CODEC = StringRepresentable.fromEnum(Display.BillboardConstraints::values);
        public static final IntFunction<Display.BillboardConstraints> BY_ID = ByIdMap.continuous(
            Display.BillboardConstraints::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO
        );
        private final byte id;
        private final String name;

        private BillboardConstraints(byte id, String name) {
            this.name = name;
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        byte getId() {
            return this.id;
        }
    }

    public static class BlockDisplay extends Display {
        public static final String TAG_BLOCK_STATE = "block_state";
        private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE_ID = SynchedEntityData.defineId(
            Display.BlockDisplay.class, EntityDataSerializers.BLOCK_STATE
        );
        @Nullable
        private Display.BlockDisplay.BlockRenderState blockRenderState;

        public BlockDisplay(EntityType<?> entityType, Level level) {
            super(entityType, level);
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {
            super.defineSynchedData(builder);
            builder.define(DATA_BLOCK_STATE_ID, Blocks.AIR.defaultBlockState());
        }

        @Override
        public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
            super.onSyncedDataUpdated(key);
            if (key.equals(DATA_BLOCK_STATE_ID)) {
                this.updateRenderState = true;
            }
        }

        private BlockState getBlockState() {
            return this.entityData.get(DATA_BLOCK_STATE_ID);
        }

        private void setBlockState(BlockState blockState) {
            this.entityData.set(DATA_BLOCK_STATE_ID, blockState);
        }

        /**
         * (abstract) Protected helper method to read subclass entity data from NBT.
         */
        @Override
        protected void readAdditionalSaveData(CompoundTag compound) {
            super.readAdditionalSaveData(compound);
            this.setBlockState(NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), compound.getCompound("block_state")));
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag compound) {
            super.addAdditionalSaveData(compound);
            compound.put("block_state", NbtUtils.writeBlockState(this.getBlockState()));
        }

        @Nullable
        public Display.BlockDisplay.BlockRenderState blockRenderState() {
            return this.blockRenderState;
        }

        @Override
        protected void updateRenderSubState(boolean interpolate, float partialTick) {
            this.blockRenderState = new Display.BlockDisplay.BlockRenderState(this.getBlockState());
        }

        public static record BlockRenderState(BlockState blockState) {
        }
    }

    static record ColorInterpolator(int previous, int current) implements Display.IntInterpolator {
        @Override
        public int get(float p_278012_) {
            return FastColor.ARGB32.lerp(p_278012_, this.previous, this.current);
        }
    }

    @FunctionalInterface
    public interface FloatInterpolator {
        static Display.FloatInterpolator constant(float value) {
            return p_278040_ -> value;
        }

        float get(float partialTick);
    }

    @FunctionalInterface
    public interface GenericInterpolator<T> {
        static <T> Display.GenericInterpolator<T> constant(T value) {
            return p_277907_ -> value;
        }

        T get(float partialTick);
    }

    @FunctionalInterface
    public interface IntInterpolator {
        static Display.IntInterpolator constant(int value) {
            return p_277356_ -> value;
        }

        int get(float partialTick);
    }

    public static class ItemDisplay extends Display {
        private static final String TAG_ITEM = "item";
        private static final String TAG_ITEM_DISPLAY = "item_display";
        private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK_ID = SynchedEntityData.defineId(
            Display.ItemDisplay.class, EntityDataSerializers.ITEM_STACK
        );
        private static final EntityDataAccessor<Byte> DATA_ITEM_DISPLAY_ID = SynchedEntityData.defineId(Display.ItemDisplay.class, EntityDataSerializers.BYTE);
        private final SlotAccess slot = SlotAccess.of(this::getItemStack, this::setItemStack);
        @Nullable
        private Display.ItemDisplay.ItemRenderState itemRenderState;

        public ItemDisplay(EntityType<?> entityType, Level level) {
            super(entityType, level);
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {
            super.defineSynchedData(builder);
            builder.define(DATA_ITEM_STACK_ID, ItemStack.EMPTY);
            builder.define(DATA_ITEM_DISPLAY_ID, ItemDisplayContext.NONE.getId());
        }

        @Override
        public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
            super.onSyncedDataUpdated(key);
            if (DATA_ITEM_STACK_ID.equals(key) || DATA_ITEM_DISPLAY_ID.equals(key)) {
                this.updateRenderState = true;
            }
        }

        private ItemStack getItemStack() {
            return this.entityData.get(DATA_ITEM_STACK_ID);
        }

        private void setItemStack(ItemStack itemStack) {
            this.entityData.set(DATA_ITEM_STACK_ID, itemStack);
        }

        private void setItemTransform(ItemDisplayContext itemTransform) {
            this.entityData.set(DATA_ITEM_DISPLAY_ID, itemTransform.getId());
        }

        private ItemDisplayContext getItemTransform() {
            return ItemDisplayContext.BY_ID.apply(this.entityData.get(DATA_ITEM_DISPLAY_ID));
        }

        /**
         * (abstract) Protected helper method to read subclass entity data from NBT.
         */
        @Override
        protected void readAdditionalSaveData(CompoundTag compound) {
            super.readAdditionalSaveData(compound);
            if (compound.contains("item")) {
                this.setItemStack(ItemStack.parse(this.registryAccess(), compound.getCompound("item")).orElse(ItemStack.EMPTY));
            } else {
                this.setItemStack(ItemStack.EMPTY);
            }

            if (compound.contains("item_display", 8)) {
                ItemDisplayContext.CODEC
                    .decode(NbtOps.INSTANCE, compound.get("item_display"))
                    .resultOrPartial(Util.prefix("Display entity", Display.LOGGER::error))
                    .ifPresent(p_270456_ -> this.setItemTransform(p_270456_.getFirst()));
            }
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag compound) {
            super.addAdditionalSaveData(compound);
            if (!this.getItemStack().isEmpty()) {
                compound.put("item", this.getItemStack().save(this.registryAccess()));
            }

            ItemDisplayContext.CODEC.encodeStart(NbtOps.INSTANCE, this.getItemTransform()).ifSuccess(p_270615_ -> compound.put("item_display", p_270615_));
        }

        @Override
        public SlotAccess getSlot(int slot) {
            return slot == 0 ? this.slot : SlotAccess.NULL;
        }

        @Nullable
        public Display.ItemDisplay.ItemRenderState itemRenderState() {
            return this.itemRenderState;
        }

        @Override
        protected void updateRenderSubState(boolean interpolate, float partialTick) {
            ItemStack itemstack = this.getItemStack();
            itemstack.setEntityRepresentation(this);
            this.itemRenderState = new Display.ItemDisplay.ItemRenderState(itemstack, this.getItemTransform());
        }

        public static record ItemRenderState(ItemStack itemStack, ItemDisplayContext itemTransform) {
        }
    }

    static record LinearFloatInterpolator(float previous, float current) implements Display.FloatInterpolator {
        @Override
        public float get(float p_277511_) {
            return Mth.lerp(p_277511_, this.previous, this.current);
        }
    }

    static record LinearIntInterpolator(int previous, int current) implements Display.IntInterpolator {
        @Override
        public int get(float p_277960_) {
            return Mth.lerpInt(p_277960_, this.previous, this.current);
        }
    }

    static class PosRotInterpolationTarget {
        int steps;
        final double targetX;
        final double targetY;
        final double targetZ;
        final double targetYRot;
        final double targetXRot;

        PosRotInterpolationTarget(int steps, double targetX, double targetY, double targetZ, double targetYRot, double targetXRot) {
            this.steps = steps;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.targetYRot = targetYRot;
            this.targetXRot = targetXRot;
        }

        void applyTargetPosAndRot(Entity entity) {
            entity.setPos(this.targetX, this.targetY, this.targetZ);
            entity.setRot((float)this.targetYRot, (float)this.targetXRot);
        }

        void applyLerpStep(Entity entity) {
            entity.lerpPositionAndRotationStep(this.steps, this.targetX, this.targetY, this.targetZ, this.targetYRot, this.targetXRot);
        }
    }

    public static record RenderState(
        Display.GenericInterpolator<Transformation> transformation,
        Display.BillboardConstraints billboardConstraints,
        int brightnessOverride,
        Display.FloatInterpolator shadowRadius,
        Display.FloatInterpolator shadowStrength,
        int glowColorOverride
    ) {
    }

    public static class TextDisplay extends Display {
        public static final String TAG_TEXT = "text";
        private static final String TAG_LINE_WIDTH = "line_width";
        private static final String TAG_TEXT_OPACITY = "text_opacity";
        private static final String TAG_BACKGROUND_COLOR = "background";
        private static final String TAG_SHADOW = "shadow";
        private static final String TAG_SEE_THROUGH = "see_through";
        private static final String TAG_USE_DEFAULT_BACKGROUND = "default_background";
        private static final String TAG_ALIGNMENT = "alignment";
        public static final byte FLAG_SHADOW = 1;
        public static final byte FLAG_SEE_THROUGH = 2;
        public static final byte FLAG_USE_DEFAULT_BACKGROUND = 4;
        public static final byte FLAG_ALIGN_LEFT = 8;
        public static final byte FLAG_ALIGN_RIGHT = 16;
        private static final byte INITIAL_TEXT_OPACITY = -1;
        public static final int INITIAL_BACKGROUND = 1073741824;
        private static final EntityDataAccessor<Component> DATA_TEXT_ID = SynchedEntityData.defineId(Display.TextDisplay.class, EntityDataSerializers.COMPONENT);
        private static final EntityDataAccessor<Integer> DATA_LINE_WIDTH_ID = SynchedEntityData.defineId(Display.TextDisplay.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<Integer> DATA_BACKGROUND_COLOR_ID = SynchedEntityData.defineId(
            Display.TextDisplay.class, EntityDataSerializers.INT
        );
        private static final EntityDataAccessor<Byte> DATA_TEXT_OPACITY_ID = SynchedEntityData.defineId(Display.TextDisplay.class, EntityDataSerializers.BYTE);
        private static final EntityDataAccessor<Byte> DATA_STYLE_FLAGS_ID = SynchedEntityData.defineId(Display.TextDisplay.class, EntityDataSerializers.BYTE);
        private static final IntSet TEXT_RENDER_STATE_IDS = IntSet.of(
            DATA_TEXT_ID.id(), DATA_LINE_WIDTH_ID.id(), DATA_BACKGROUND_COLOR_ID.id(), DATA_TEXT_OPACITY_ID.id(), DATA_STYLE_FLAGS_ID.id()
        );
        @Nullable
        private Display.TextDisplay.CachedInfo clientDisplayCache;
        @Nullable
        private Display.TextDisplay.TextRenderState textRenderState;

        public TextDisplay(EntityType<?> entityType, Level level) {
            super(entityType, level);
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {
            super.defineSynchedData(builder);
            builder.define(DATA_TEXT_ID, Component.empty());
            builder.define(DATA_LINE_WIDTH_ID, 200);
            builder.define(DATA_BACKGROUND_COLOR_ID, 1073741824);
            builder.define(DATA_TEXT_OPACITY_ID, (byte)-1);
            builder.define(DATA_STYLE_FLAGS_ID, (byte)0);
        }

        @Override
        public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
            super.onSyncedDataUpdated(key);
            if (TEXT_RENDER_STATE_IDS.contains(key.id())) {
                this.updateRenderState = true;
            }
        }

        private Component getText() {
            return this.entityData.get(DATA_TEXT_ID);
        }

        private void setText(Component text) {
            this.entityData.set(DATA_TEXT_ID, text);
        }

        private int getLineWidth() {
            return this.entityData.get(DATA_LINE_WIDTH_ID);
        }

        private void setLineWidth(int lineWidth) {
            this.entityData.set(DATA_LINE_WIDTH_ID, lineWidth);
        }

        private byte getTextOpacity() {
            return this.entityData.get(DATA_TEXT_OPACITY_ID);
        }

        private void setTextOpacity(byte textOpacity) {
            this.entityData.set(DATA_TEXT_OPACITY_ID, textOpacity);
        }

        private int getBackgroundColor() {
            return this.entityData.get(DATA_BACKGROUND_COLOR_ID);
        }

        private void setBackgroundColor(int backgroundColor) {
            this.entityData.set(DATA_BACKGROUND_COLOR_ID, backgroundColor);
        }

        private byte getFlags() {
            return this.entityData.get(DATA_STYLE_FLAGS_ID);
        }

        private void setFlags(byte flags) {
            this.entityData.set(DATA_STYLE_FLAGS_ID, flags);
        }

        private static byte loadFlag(byte currentValue, CompoundTag tag, String flag, byte mask) {
            return tag.getBoolean(flag) ? (byte)(currentValue | mask) : currentValue;
        }

        /**
         * (abstract) Protected helper method to read subclass entity data from NBT.
         */
        @Override
        protected void readAdditionalSaveData(CompoundTag compound) {
            super.readAdditionalSaveData(compound);
            if (compound.contains("line_width", 99)) {
                this.setLineWidth(compound.getInt("line_width"));
            }

            if (compound.contains("text_opacity", 99)) {
                this.setTextOpacity(compound.getByte("text_opacity"));
            }

            if (compound.contains("background", 99)) {
                this.setBackgroundColor(compound.getInt("background"));
            }

            byte b0 = loadFlag((byte)0, compound, "shadow", (byte)1);
            b0 = loadFlag(b0, compound, "see_through", (byte)2);
            b0 = loadFlag(b0, compound, "default_background", (byte)4);
            Optional<Display.TextDisplay.Align> optional = Display.TextDisplay.Align.CODEC
                .decode(NbtOps.INSTANCE, compound.get("alignment"))
                .resultOrPartial(Util.prefix("Display entity", Display.LOGGER::error))
                .map(Pair::getFirst);
            if (optional.isPresent()) {
                b0 = switch ((Display.TextDisplay.Align)optional.get()) {
                    case CENTER -> b0;
                    case LEFT -> (byte)(b0 | 8);
                    case RIGHT -> (byte)(b0 | 16);
                };
            }

            this.setFlags(b0);
            if (compound.contains("text", 8)) {
                String s = compound.getString("text");

                try {
                    Component component = Component.Serializer.fromJson(s, this.registryAccess());
                    if (component != null) {
                        CommandSourceStack commandsourcestack = this.createCommandSourceStack().withPermission(2);
                        Component component1 = ComponentUtils.updateForEntity(commandsourcestack, component, this, 0);
                        this.setText(component1);
                    } else {
                        this.setText(Component.empty());
                    }
                } catch (Exception exception) {
                    Display.LOGGER.warn("Failed to parse display entity text {}", s, exception);
                }
            }
        }

        private static void storeFlag(byte currentValue, CompoundTag tag, String flag, byte mask) {
            tag.putBoolean(flag, (currentValue & mask) != 0);
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag compound) {
            super.addAdditionalSaveData(compound);
            compound.putString("text", Component.Serializer.toJson(this.getText(), this.registryAccess()));
            compound.putInt("line_width", this.getLineWidth());
            compound.putInt("background", this.getBackgroundColor());
            compound.putByte("text_opacity", this.getTextOpacity());
            byte b0 = this.getFlags();
            storeFlag(b0, compound, "shadow", (byte)1);
            storeFlag(b0, compound, "see_through", (byte)2);
            storeFlag(b0, compound, "default_background", (byte)4);
            Display.TextDisplay.Align.CODEC.encodeStart(NbtOps.INSTANCE, getAlign(b0)).ifSuccess(p_271001_ -> compound.put("alignment", p_271001_));
        }

        @Override
        protected void updateRenderSubState(boolean interpolate, float partialTick) {
            if (interpolate && this.textRenderState != null) {
                this.textRenderState = this.createInterpolatedTextRenderState(this.textRenderState, partialTick);
            } else {
                this.textRenderState = this.createFreshTextRenderState();
            }

            this.clientDisplayCache = null;
        }

        @Nullable
        public Display.TextDisplay.TextRenderState textRenderState() {
            return this.textRenderState;
        }

        private Display.TextDisplay.TextRenderState createFreshTextRenderState() {
            return new Display.TextDisplay.TextRenderState(
                this.getText(),
                this.getLineWidth(),
                Display.IntInterpolator.constant(this.getTextOpacity()),
                Display.IntInterpolator.constant(this.getBackgroundColor()),
                this.getFlags()
            );
        }

        private Display.TextDisplay.TextRenderState createInterpolatedTextRenderState(Display.TextDisplay.TextRenderState renderState, float partialTick) {
            int i = renderState.backgroundColor.get(partialTick);
            int j = renderState.textOpacity.get(partialTick);
            return new Display.TextDisplay.TextRenderState(
                this.getText(),
                this.getLineWidth(),
                new Display.LinearIntInterpolator(j, this.getTextOpacity()),
                new Display.ColorInterpolator(i, this.getBackgroundColor()),
                this.getFlags()
            );
        }

        public Display.TextDisplay.CachedInfo cacheDisplay(Display.TextDisplay.LineSplitter splitter) {
            if (this.clientDisplayCache == null) {
                if (this.textRenderState != null) {
                    this.clientDisplayCache = splitter.split(this.textRenderState.text(), this.textRenderState.lineWidth());
                } else {
                    this.clientDisplayCache = new Display.TextDisplay.CachedInfo(List.of(), 0);
                }
            }

            return this.clientDisplayCache;
        }

        public static Display.TextDisplay.Align getAlign(byte flags) {
            if ((flags & 8) != 0) {
                return Display.TextDisplay.Align.LEFT;
            } else {
                return (flags & 16) != 0 ? Display.TextDisplay.Align.RIGHT : Display.TextDisplay.Align.CENTER;
            }
        }

        public static enum Align implements StringRepresentable {
            CENTER("center"),
            LEFT("left"),
            RIGHT("right");

            public static final Codec<Display.TextDisplay.Align> CODEC = StringRepresentable.fromEnum(Display.TextDisplay.Align::values);
            private final String name;

            private Align(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }

        public static record CachedInfo(List<Display.TextDisplay.CachedLine> lines, int width) {
        }

        public static record CachedLine(FormattedCharSequence contents, int width) {
        }

        @FunctionalInterface
        public interface LineSplitter {
            Display.TextDisplay.CachedInfo split(Component text, int maxWidth);
        }

        public static record TextRenderState(
            Component text, int lineWidth, Display.IntInterpolator textOpacity, Display.IntInterpolator backgroundColor, byte flags
        ) {
        }
    }

    static record TransformationInterpolator(Transformation previous, Transformation current) implements Display.GenericInterpolator<Transformation> {
        public Transformation get(float p_278027_) {
            return (double)p_278027_ >= 1.0 ? this.current : this.previous.slerp(this.current, p_278027_);
        }
    }
}
