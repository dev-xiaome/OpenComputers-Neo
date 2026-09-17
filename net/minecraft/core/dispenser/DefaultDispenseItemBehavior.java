package net.minecraft.core.dispenser;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

public class DefaultDispenseItemBehavior implements DispenseItemBehavior {
    private static final int DEFAULT_ACCURACY = 6;

    @Override
    public final ItemStack dispense(BlockSource blockSource, ItemStack item) {
        ItemStack itemstack = this.execute(blockSource, item);
        this.playSound(blockSource);
        this.playAnimation(blockSource, blockSource.state().getValue(DispenserBlock.FACING));
        return itemstack;
    }

    protected ItemStack execute(BlockSource blockSource, ItemStack item) {
        Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
        Position position = DispenserBlock.getDispensePosition(blockSource);
        ItemStack itemstack = item.split(1);
        spawnItem(blockSource.level(), itemstack, 6, direction, position);
        return item;
    }

    public static void spawnItem(Level level, ItemStack stack, int speed, Direction facing, Position position) {
        double d0 = position.x();
        double d1 = position.y();
        double d2 = position.z();
        if (facing.getAxis() == Direction.Axis.Y) {
            d1 -= 0.125;
        } else {
            d1 -= 0.15625;
        }

        ItemEntity itementity = new ItemEntity(level, d0, d1, d2, stack);
        double d3 = level.random.nextDouble() * 0.1 + 0.2;
        itementity.setDeltaMovement(
            level.random.triangle((double)facing.getStepX() * d3, 0.0172275 * (double)speed),
            level.random.triangle(0.2, 0.0172275 * (double)speed),
            level.random.triangle((double)facing.getStepZ() * d3, 0.0172275 * (double)speed)
        );
        level.addFreshEntity(itementity);
    }

    protected void playSound(BlockSource blockSource) {
        playDefaultSound(blockSource);
    }

    protected void playAnimation(BlockSource blockSource, Direction direction) {
        playDefaultAnimation(blockSource, direction);
    }

    private static void playDefaultSound(BlockSource blockSource) {
        blockSource.level().levelEvent(1000, blockSource.pos(), 0);
    }

    private static void playDefaultAnimation(BlockSource blockSource, Direction direction) {
        blockSource.level().levelEvent(2000, blockSource.pos(), direction.get3DDataValue());
    }

    protected ItemStack consumeWithRemainder(BlockSource blockSource, ItemStack stack, ItemStack remainder) {
        stack.shrink(1);
        if (stack.isEmpty()) {
            return remainder;
        } else {
            this.addToInventoryOrDispense(blockSource, remainder);
            return stack;
        }
    }

    private void addToInventoryOrDispense(BlockSource blockSource, ItemStack remainder) {
        ItemStack itemstack = blockSource.blockEntity().insertItem(remainder);
        if (!itemstack.isEmpty()) {
            Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
            spawnItem(blockSource.level(), itemstack, 6, direction, DispenserBlock.getDispensePosition(blockSource));
            playDefaultSound(blockSource);
            playDefaultAnimation(blockSource, direction);
        }
    }
}
