package net.minecraft.world.level.levelgen.blockpredicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;

public class InsideWorldBoundsPredicate implements BlockPredicate {
    public static final MapCodec<InsideWorldBoundsPredicate> CODEC = RecordCodecBuilder.mapCodec(
        p_190473_ -> p_190473_.group(Vec3i.offsetCodec(16).optionalFieldOf("offset", BlockPos.ZERO).forGetter(p_190475_ -> p_190475_.offset))
                .apply(p_190473_, InsideWorldBoundsPredicate::new)
    );
    private final Vec3i offset;

    public InsideWorldBoundsPredicate(Vec3i offset) {
        this.offset = offset;
    }

    public boolean test(WorldGenLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos.offset(this.offset));
    }

    @Override
    public BlockPredicateType<?> type() {
        return BlockPredicateType.INSIDE_WORLD_BOUNDS;
    }
}
