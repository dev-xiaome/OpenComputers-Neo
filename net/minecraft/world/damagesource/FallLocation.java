package net.minecraft.world.damagesource;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public record FallLocation(String id) {
    public static final FallLocation GENERIC = new FallLocation("generic");
    public static final FallLocation LADDER = new FallLocation("ladder");
    public static final FallLocation VINES = new FallLocation("vines");
    public static final FallLocation WEEPING_VINES = new FallLocation("weeping_vines");
    public static final FallLocation TWISTING_VINES = new FallLocation("twisting_vines");
    public static final FallLocation SCAFFOLDING = new FallLocation("scaffolding");
    public static final FallLocation OTHER_CLIMBABLE = new FallLocation("other_climbable");
    public static final FallLocation WATER = new FallLocation("water");

    public static FallLocation blockToFallLocation(BlockState state) {
        if (state.is(Blocks.LADDER) || state.is(BlockTags.TRAPDOORS)) {
            return LADDER;
        } else if (state.is(Blocks.VINE)) {
            return VINES;
        } else if (state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT)) {
            return WEEPING_VINES;
        } else if (state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT)) {
            return TWISTING_VINES;
        } else {
            return state.is(Blocks.SCAFFOLDING) ? SCAFFOLDING : OTHER_CLIMBABLE;
        }
    }

    @Nullable
    public static FallLocation getCurrentFallLocation(LivingEntity entity) {
        Optional<BlockPos> optional = entity.getLastClimbablePos();
        if (optional.isPresent()) {
            BlockState blockstate = entity.level().getBlockState(optional.get());
            return blockToFallLocation(blockstate);
        } else {
            return entity.isInWater() ? WATER : null;
        }
    }

    public String languageKey() {
        return "death.fell.accident." + this.id;
    }
}
