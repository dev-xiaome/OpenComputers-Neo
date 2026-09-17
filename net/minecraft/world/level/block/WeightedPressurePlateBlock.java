package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class WeightedPressurePlateBlock extends BasePressurePlateBlock {
    public static final MapCodec<WeightedPressurePlateBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_308853_ -> p_308853_.group(
                    Codec.intRange(1, 1024).fieldOf("max_weight").forGetter(p_304500_ -> p_304500_.maxWeight),
                    BlockSetType.CODEC.fieldOf("block_set_type").forGetter(p_304629_ -> p_304629_.type),
                    propertiesCodec()
                )
                .apply(p_308853_, WeightedPressurePlateBlock::new)
    );
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private final int maxWeight;

    @Override
    public MapCodec<WeightedPressurePlateBlock> codec() {
        return CODEC;
    }

    public WeightedPressurePlateBlock(int maxWeight, BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties, type);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, Integer.valueOf(0)));
        this.maxWeight = maxWeight;
    }

    /**
     * Calculates what the signal strength of a pressure plate at the given location should be.
     */
    @Override
    protected int getSignalStrength(Level level, BlockPos pos) {
        int i = Math.min(getEntityCount(level, TOUCH_AABB.move(pos), Entity.class), this.maxWeight);
        if (i > 0) {
            float f = (float)Math.min(this.maxWeight, i) / (float)this.maxWeight;
            return Mth.ceil(f * 15.0F);
        } else {
            return 0;
        }
    }

    /**
     * Returns the signal encoded in the given block state.
     */
    @Override
    protected int getSignalForState(BlockState state) {
        return state.getValue(POWER);
    }

    /**
     * Returns the block state that encodes the given signal.
     */
    @Override
    protected BlockState setSignalForState(BlockState state, int strength) {
        return state.setValue(POWER, Integer.valueOf(strength));
    }

    @Override
    protected int getPressedTime() {
        return 10;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }
}
