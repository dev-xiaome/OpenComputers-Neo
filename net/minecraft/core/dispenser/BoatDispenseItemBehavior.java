package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

public class BoatDispenseItemBehavior extends DefaultDispenseItemBehavior {
    private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();
    private final Boat.Type type;
    private final boolean isChestBoat;

    public BoatDispenseItemBehavior(Boat.Type type) {
        this(type, false);
    }

    public BoatDispenseItemBehavior(Boat.Type type, boolean isChestBoat) {
        this.type = type;
        this.isChestBoat = isChestBoat;
    }

    @Override
    public ItemStack execute(BlockSource blockSource, ItemStack item) {
        Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
        ServerLevel serverlevel = blockSource.level();
        Vec3 vec3 = blockSource.center();
        double d0 = 0.5625 + (double)EntityType.BOAT.getWidth() / 2.0;
        double d1 = vec3.x() + (double)direction.getStepX() * d0;
        double d2 = vec3.y() + (double)((float)direction.getStepY() * 1.125F);
        double d3 = vec3.z() + (double)direction.getStepZ() * d0;
        BlockPos blockpos = blockSource.pos().relative(direction);
        Boat boat = (Boat)(this.isChestBoat ? new ChestBoat(serverlevel, d1, d2, d3) : new Boat(serverlevel, d1, d2, d3));
        EntityType.<Boat>createDefaultStackConfig(serverlevel, item, null).accept(boat);
        boat.setVariant(this.type);
        boat.setYRot(direction.toYRot());
        double d4;
        if (boat.canBoatInFluid(serverlevel.getFluidState(blockpos))) {
            d4 = 1.0;
        } else {
            if (!serverlevel.getBlockState(blockpos).isAir() || !boat.canBoatInFluid(serverlevel.getFluidState(blockpos.below()))) {
                return this.defaultDispenseItemBehavior.dispense(blockSource, item);
            }

            d4 = 0.0;
        }

        boat.setPos(d1, d2 + d4, d3);
        serverlevel.addFreshEntity(boat);
        item.shrink(1);
        return item;
    }

    @Override
    protected void playSound(BlockSource blockSource) {
        blockSource.level().levelEvent(1000, blockSource.pos(), 0);
    }
}
