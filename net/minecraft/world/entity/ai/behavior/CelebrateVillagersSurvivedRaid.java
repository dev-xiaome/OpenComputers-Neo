package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

public class CelebrateVillagersSurvivedRaid extends Behavior<Villager> {
    @Nullable
    private Raid currentRaid;

    public CelebrateVillagersSurvivedRaid(int minDuration, int maxDuration) {
        super(ImmutableMap.of(), minDuration, maxDuration);
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        BlockPos blockpos = owner.blockPosition();
        this.currentRaid = level.getRaidAt(blockpos);
        return this.currentRaid != null && this.currentRaid.isVictory() && MoveToSkySeeingSpot.hasNoBlocksAbove(level, owner, blockpos);
    }

    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.currentRaid != null && !this.currentRaid.isStopped();
    }

    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        this.currentRaid = null;
        entity.getBrain().updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
    }

    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        RandomSource randomsource = owner.getRandom();
        if (randomsource.nextInt(100) == 0) {
            owner.playCelebrateSound();
        }

        if (randomsource.nextInt(200) == 0 && MoveToSkySeeingSpot.hasNoBlocksAbove(level, owner, owner.blockPosition())) {
            DyeColor dyecolor = Util.getRandom(DyeColor.values(), randomsource);
            int i = randomsource.nextInt(3);
            ItemStack itemstack = this.getFirework(dyecolor, i);
            FireworkRocketEntity fireworkrocketentity = new FireworkRocketEntity(
                owner.level(), owner, owner.getX(), owner.getEyeY(), owner.getZ(), itemstack
            );
            owner.level().addFreshEntity(fireworkrocketentity);
        }
    }

    private ItemStack getFirework(DyeColor color, int flightTime) {
        ItemStack itemstack = new ItemStack(Items.FIREWORK_ROCKET);
        itemstack.set(
            DataComponents.FIREWORKS,
            new Fireworks(
                (byte)flightTime,
                List.of(new FireworkExplosion(FireworkExplosion.Shape.BURST, IntList.of(color.getFireworkColor()), IntList.of(), false, false))
            )
        );
        return itemstack;
    }
}
