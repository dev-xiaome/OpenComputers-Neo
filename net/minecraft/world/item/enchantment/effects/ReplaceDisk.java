package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.phys.Vec3;

public record ReplaceDisk(
    LevelBasedValue radius,
    LevelBasedValue height,
    Vec3i offset,
    Optional<BlockPredicate> predicate,
    BlockStateProvider blockState,
    Optional<Holder<GameEvent>> triggerGameEvent
) implements EnchantmentEntityEffect {
    public static final MapCodec<ReplaceDisk> CODEC = RecordCodecBuilder.mapCodec(
        p_353035_ -> p_353035_.group(
                    LevelBasedValue.CODEC.fieldOf("radius").forGetter(ReplaceDisk::radius),
                    LevelBasedValue.CODEC.fieldOf("height").forGetter(ReplaceDisk::height),
                    Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(ReplaceDisk::offset),
                    BlockPredicate.CODEC.optionalFieldOf("predicate").forGetter(ReplaceDisk::predicate),
                    BlockStateProvider.CODEC.fieldOf("block_state").forGetter(ReplaceDisk::blockState),
                    GameEvent.CODEC.optionalFieldOf("trigger_game_event").forGetter(ReplaceDisk::triggerGameEvent)
                )
                .apply(p_353035_, ReplaceDisk::new)
    );

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        BlockPos blockpos = BlockPos.containing(origin).offset(this.offset);
        RandomSource randomsource = entity.getRandom();
        int i = (int)this.radius.calculate(enchantmentLevel);
        int j = (int)this.height.calculate(enchantmentLevel);

        for (BlockPos blockpos1 : BlockPos.betweenClosed(blockpos.offset(-i, 0, -i), blockpos.offset(i, Math.min(j - 1, 0), i))) {
            if (blockpos1.distToCenterSqr(origin.x(), (double)blockpos1.getY() + 0.5, origin.z()) < (double)Mth.square(i)
                && this.predicate.map(p_353051_ -> p_353051_.test(level, blockpos1)).orElse(true)
                && level.setBlockAndUpdate(blockpos1, this.blockState.getState(randomsource, blockpos1))) {
                this.triggerGameEvent.ifPresent(p_353037_ -> level.gameEvent(entity, (Holder<GameEvent>)p_353037_, blockpos1));
            }
        }
    }

    @Override
    public MapCodec<ReplaceDisk> codec() {
        return CODEC;
    }
}
