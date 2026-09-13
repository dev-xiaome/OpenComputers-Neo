package li.cil.oc.api.prefab;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraftforge.oredict.OreDictionary;

/**
 * If you wish to create a block component for a third-party block, i.e. a block
 * for which you do not control the tile entity, such as vanilla blocks, you
 * will need a block driver.
 * <br>
 * This prefab allows creating a driver that works for a specified list of item
 * stacks (to support different blocks with the same id but different metadata
 * values).
 * <br>
 * You still have to provide the implementation for creating its environment, if
 * any.
 * <br>
 * To limit sidedness, I recommend overriding {@link #worksWith(Level, int, int, int, Direction)}
 * and calling <code>super.worksWith</code> in addition to the side check.
 *
 * @see li.cil.oc.api.network.ManagedEnvironment
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class DriverSidedBlock implements li.cil.oc.api.driver.SidedBlock {
    protected final ItemStack[] blocks;

    protected DriverSidedBlock(final ItemStack... blocks) {
        this.blocks = blocks.clone();
    }

    @Override
    public boolean worksWith(final Level world, final int x, final int y, final int z, final Direction side) {
        return worksWith(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));
    }

    protected boolean worksWith(final Block referenceBlock, final int referenceMetadata) {
        for (ItemStack stack : blocks) {
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                final ItemBlock item = (ItemBlock) stack.getItem();
                final Block supportedBlock = item.field_150939_a;
                final int supportedMetadata = item.getMetadata(stack.getItemDamage());
                if (referenceBlock == supportedBlock && (referenceMetadata == supportedMetadata || stack.getItemDamage() == OreDictionary.WILDCARD_VALUE)) {
                    return true;
                }
            }
        }
        return false;
    }
}
