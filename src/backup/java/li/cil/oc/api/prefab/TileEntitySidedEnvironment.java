package li.cil.oc.api.prefab;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * TileEntities can implement the {@link li.cil.oc.api.network.SidedEnvironment}
 * interface to allow them to interact with the component network, by providing
 * a separate {@link li.cil.oc.api.network.Node} for each block face, and
 * connecting it to said network. This allows more control over connectivity
 * than the simple {@link li.cil.oc.api.network.Environment}.
 * <br>
 * Nodes in such a network can communicate with each other, or just use the
 * network as an index structure to find other nodes connected to them.
 * <br>
 * <b>1.21.1 移植说明</b>：与 {@link TileEntityEnvironment} 相同，本类不再有默认构造器，
 * 子类必须把注册好的 {@link BlockEntityType} 与坐标、方块状态一起透传给构造器；
 * {@code updateEntity()} 需要通过 {@link #ticker()} 或 {@code EntityBlock#getTicker} 驱动；
 * {@code readFromNBT}/{@code writeToNBT} 变为
 * {@code loadAdditional}/{@code saveAdditional}。
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class TileEntitySidedEnvironment extends BlockEntity implements SidedEnvironment {
    // See constructor.
    protected Node[] nodes = new Node[6];

    // See updateEntity().
    protected boolean addedToNetwork = false;

    /**
     * This expects a node per face that is used to represent this tile entity.
     * <br>
     * You must only create new nodes using the factory method in the network
     * API, {@link li.cil.oc.api.Network#newNode(li.cil.oc.api.network.Environment, li.cil.oc.api.network.Visibility)}.
     * <br>
     * For example:
     * <pre>
     * // The first parameters to newNode is the host() of the node, which will
     * // usually be this tile entity. The second one is it's reachability,
     * // which determines how other nodes in the same network can query this
     * // node. See {@link li.cil.oc.api.network.Network#nodes(li.cil.oc.api.network.Node)}.
     * super(type, pos, state, Network.newNode(???, Visibility.Network)
     *       // This call allows the node to consume energy from the
     *       // component network it is in and act as a consumer, or to
     *       // inject energy into that network and act as a producer.
     *       // If you do not need energy remove this call.
     *       .withConnector()
     *       // This call marks the tile entity as a component. This means you
     *       // can mark methods in it using the {@link li.cil.oc.api.machine.Callback}
     *       // annotation, making them callable from user code. The first
     *       // parameter is the name by which the component will be known in
     *       // the computer, in this case it could be accessed as
     *       // <tt>component.example</tt>. The second parameter is the
     *       // component's visibility. This is like the node's reachability,
     *       // but only applies to computers. For example, network cards can
     *       // only be <em>seen</em> by the computer they're installed in, but
     *       // can be <em>reached</em> by all other network cards in the same
     *       // network. If you do not need callbacks remove this call.
     *       .withComponent("example", Visibility.Neighbors)
     *       // Finalizes the construction of the node and returns it.
     *       .create(), ...);
     * </pre>
     *
     * @param type  已注册的方块实体类型。
     * @param pos   方块实体所在坐标。
     * @param state 方块实体所在方块的当前状态。
     * @param nodes 每个面（{@code Direction#ordinal()}，即 DOWN/UP/NORTH/SOUTH/WEST/EAST）
     *              对应的节点，允许少于六个。
     */
    protected TileEntitySidedEnvironment(final BlockEntityType<?> type, final BlockPos pos, final BlockState state, final Node... nodes) {
        super(type, pos, state);
        System.arraycopy(nodes, 0, this.nodes, 0, Math.min(nodes.length, this.nodes.length));
    }

    // ----------------------------------------------------------------------- //

    // canConnect() is for the client side, to determine how cables are
    // rendered, for example, so you'll have to provide that logic yourself.
    // Nodes are only created on the server side, so checking whether a node
    // exists for a side won't work on the client.

    @Override
    public Node sidedNode(final Direction side) {
        // 1.21.1 的 Direction 只有六个朝向，不再有 ForgeDirection.UNKNOWN 这样的
        // “无朝向”常量；这里对 null 与越界做保护，返回 null 表示该面没有节点。
        if (side == null) {
            return null;
        }
        final int index = side.ordinal();
        return index >= 0 && index < nodes.length ? nodes[index] : null;
    }

    // ----------------------------------------------------------------------- //

    /**
     * 每 tick 的初始化逻辑，语义与 {@link TileEntityEnvironment#updateEntity()} 相同：
     * 1.21.1 没有自动的 {@code updateEntity()} 钩子，需要由 ticker 驱动。
     */
    public void updateEntity() {
        // On the first update, try to add our node to nearby networks. We do
        // this in the update logic, not in validate() because we need to access
        // neighboring tile entities, which isn't possible in validate().
        // We could alternatively check node != null && node.network() == null,
        // but this has somewhat better performance, and makes it clearer.
        if (!addedToNetwork) {
            addedToNetwork = true;
            // Note that joinOrCreateNetwork will try to connect each of our
            // sided nodes to their respective neighbor (sided) node.
            Network.joinOrCreateNetwork(this);
        }
    }

    /**
     * 便捷方法：返回一个只调用 {@link #updateEntity()} 的 ticker，用法同
     * {@link TileEntityEnvironment#ticker()}。
     *
     * @param <T> 方块实体类型。
     * @return 一个每 tick 调用 {@link #updateEntity()} 的 ticker。
     */
    public static <T extends BlockEntity> BlockEntityTicker<T> ticker() {
        return (level, pos, state, blockEntity) -> {
            if (blockEntity instanceof TileEntitySidedEnvironment environment) {
                environment.updateEntity();
            }
        };
    }

    /**
     * 1.21.1 中 {@code onChunkUnload()} 被 NeoForge 的
     * {@code IBlockEntityExtension#onChunkUnloaded()} 取代。
     */
    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        // Make sure to remove the node from its network when its environment,
        // meaning this tile entity, gets unloaded.
        for (Node node : nodes) {
            if (node != null) node.remove();
        }
    }

    /**
     * 1.21.1 中 {@code invalidate()} 被 {@link BlockEntity#setRemoved()} 取代。
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        // Make sure to remove the node from its network when its environment,
        // meaning this tile entity, gets unloaded.
        for (Node node : nodes) {
            if (node != null) node.remove();
        }
    }

    // ----------------------------------------------------------------------- //

    @Override
    protected void loadAdditional(final CompoundTag nbt, final HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        int index = 0;
        for (Node node : nodes) {
            // The host check may be superfluous for you. It's just there to allow
            // some special cases, where getNode() returns some node managed by
            // some other instance (for example when you have multiple internal
            // nodes in this tile entity).
            if (node != null && node.host() == this) {
                // This restores the node's address, which is required for networks
                // to continue working without interruption across loads. If the
                // node is a power connector this is also required to restore the
                // internal energy buffer of the node.
                node.load(nbt.getCompound("oc:node" + index));
            }
            ++index;
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag nbt, final HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        int index = 0;
        for (Node node : nodes) {
            // See loadAdditional() regarding host check.
            if (node != null && node.host() == this) {
                final CompoundTag nodeNbt = new CompoundTag();
                node.save(nodeNbt);
                nbt.put("oc:node" + index, nodeNbt);
            }
            ++index;
        }
    }
}
