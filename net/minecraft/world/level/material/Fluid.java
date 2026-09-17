package net.minecraft.world.level.material;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class Fluid implements net.neoforged.neoforge.common.extensions.IFluidExtension {
    public static final IdMapper<FluidState> FLUID_STATE_REGISTRY = new IdMapper<>();
    protected final StateDefinition<Fluid, FluidState> stateDefinition;
    private FluidState defaultFluidState;
    private final Holder.Reference<Fluid> builtInRegistryHolder = BuiltInRegistries.FLUID.createIntrusiveHolder(this);

    protected Fluid() {
        StateDefinition.Builder<Fluid, FluidState> builder = new StateDefinition.Builder<>(this);
        this.createFluidStateDefinition(builder);
        this.stateDefinition = builder.create(Fluid::defaultFluidState, FluidState::new);
        this.registerDefaultState(this.stateDefinition.any());
    }

    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
    }

    public StateDefinition<Fluid, FluidState> getStateDefinition() {
        return this.stateDefinition;
    }

    protected final void registerDefaultState(FluidState state) {
        this.defaultFluidState = state;
    }

    public final FluidState defaultFluidState() {
        return this.defaultFluidState;
    }

    public abstract Item getBucket();

    protected void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
    }

    protected void tick(Level level, BlockPos pos, FluidState state) {
    }

    protected void randomTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
    }

    @Nullable
    protected ParticleOptions getDripParticle() {
        return null;
    }

    protected abstract boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction);

    protected abstract Vec3 getFlow(BlockGetter blockReader, BlockPos pos, FluidState fluidState);

    public abstract int getTickDelay(LevelReader level);

    protected boolean isRandomlyTicking() {
        return false;
    }

    protected boolean isEmpty() {
        return false;
    }

    protected abstract float getExplosionResistance();

    public abstract float getHeight(FluidState state, BlockGetter level, BlockPos pos);

    public abstract float getOwnHeight(FluidState state);

    protected abstract BlockState createLegacyBlock(FluidState state);

    public abstract boolean isSource(FluidState state);

    public abstract int getAmount(FluidState state);

    public boolean isSame(Fluid fluid) {
        return fluid == this;
    }

    @Deprecated
    public boolean is(TagKey<Fluid> tag) {
        return this.builtInRegistryHolder.is(tag);
    }

    public abstract VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos);

    @Override
    public String toString() {// Neo: Patch in a toString similar to Item#toString so that FluidStack#toStringis more readable
        return BuiltInRegistries.FLUID.wrapAsHolder(this).getRegisteredName();
    }

    private net.neoforged.neoforge.fluids.FluidType forgeFluidType;
    @Override
    public net.neoforged.neoforge.fluids.FluidType getFluidType() {
        if (forgeFluidType == null) forgeFluidType = net.neoforged.neoforge.common.CommonHooks.getVanillaFluidType(this);
        return forgeFluidType;
    }

    public Optional<SoundEvent> getPickupSound() {
        return Optional.empty();
    }

    @Deprecated
    public Holder.Reference<Fluid> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }
}
