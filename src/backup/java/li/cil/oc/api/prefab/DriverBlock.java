package li.cil.oc.api.prefab;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
 * <b>1.21.1 移植说明</b>：方块不再有 metadata，{@code OreDictionary.WILDCARD_VALUE}
 * 也不存在，因此匹配逻辑简化为“物品对应的 {@link Block} 是否与目标方块相同”。
 * 原来的 {@code worksWith(Block, int)} 钩子改为
 * {@link #worksWith(BlockState)} / {@link #worksWith(Block)}，覆盖旧签名的子类需要同步改写。
 *
 * @see li.cil.oc.api.network.ManagedEnvironment
 * @deprecated Use {@link DriverSidedBlock} instead.
 */
@Deprecated // TODO Remove in OC 1.7
@SuppressWarnings("UnusedDeclaration")
public abstract class DriverBlock implements li.cil.oc.api.driver.Block {
    protected final ItemStack[] blocks;

    protected DriverBlock(final ItemStack... blocks) {
        this.blocks = blocks.clone();
    }

    @Override
    public boolean worksWith(final Level world, final int x, final int y, final int z) {
        // 1.21.1 中 World#getBlock / getBlockMetadata 合并为 BlockState。
        return worksWith(world.getBlockState(new BlockPos(x, y, z)));
    }

    /**
     * 判断指定方块状态是否被本驱动器支持。
     *
     * @param state 目标位置当前的方块状态。
     * @return 是否支持。
     */
    protected boolean worksWith(final BlockState state) {
        return worksWith(state.getBlock());
    }

    /**
     * 判断指定方块是否被本驱动器支持。
     * <br>
     * 代替 1.7.10 的 {@code worksWith(Block referenceBlock, int referenceMetadata)}：
     * 1.21.1 没有 metadata，只比较方块本身。
     *
     * @param referenceBlock 目标位置的方块。
     * @return 是否支持。
     */
    protected boolean worksWith(final Block referenceBlock) {
        for (ItemStack stack : blocks) {
            if (stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem item) {
                if (item.getBlock() == referenceBlock) {
                    return true;
                }
            }
        }
        return false;
    }
}
