package li.cil.oc.api.prefab;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * TileEntities can implement the {@link li.cil.oc.api.network.Environment}
 * interface to allow them to interact with the component network, by providing
 * a {@link li.cil.oc.api.network.Node} and connecting it to said network.
 * <br>
 * Nodes in such a network can communicate with each other, or just use the
 * network as an index structure to find other nodes connected to them.
 * <br>
 * <b>1.21.1 移植说明（注册方式变化）</b>
 * <ul>
 * <li>1.7.10 的 {@code TileEntity} 有无参构造器，由
 * {@code Block.createTileEntity} 负责实例化。1.21.1 的
 * {@link BlockEntity} 没有无参构造器，必须写成
 * {@code BlockEntity(BlockEntityType<?>, BlockPos, BlockState)}。</li>
 * <li>因此本类不再提供无参构造器，子类必须把 {@link BlockEntityType}
 * 透传给 {@link #TileEntityEnvironment(BlockEntityType, BlockPos, BlockState)}。</li>
 * <li>{@code BlockEntityType} 本身要注册到 {@code DeferredRegister}（见
 * {@code BlockEntityType.Builder.of(...)}），并且方块需要实现
 * {@code EntityBlock#newBlockEntity} 与 {@code EntityBlock#getTicker} 才能被创建和更新。</li>
 * <li>1.21.1 不再有 {@code updateEntity()} 钩子，请改用
 * {@link #ticker()} 或自行在 {@code EntityBlock#getTicker} 中调用
 * {@link #updateEntity()}。</li>
 * </ul>
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class TileEntityEnvironment extends BlockEntity implements Environment {
    /**
     * This must be set in subclasses to the node that is used to represent
     * this tile entity.
     * <br>
     * You must only create new nodes using the factory method in the network
     * API, {@link li.cil.oc.api.Network#newNode(Environment, Visibility)}.
     * <br>
     * For example:
     * <pre>
     * // The first parameters to newNode is the host() of the node, which will
     * // usually be this tile entity. The second one is it's reachability,
     * // which determines how other nodes in the same network can query this
     * // node. See {@link li.cil.oc.api.network.Network#nodes(li.cil.oc.api.network.Node)}.
     * node = Network.newNode(this, Visibility.Network)
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
     *       .create();
     * </pre>
     */
    protected Node node;

    // See updateEntity().
    protected boolean addedToNetwork = false;

    // ----------------------------------------------------------------------- //

    /**
     * 1.21.1 的方块实体必须由 {@link BlockEntityType} 构造，子类请把注册好的类型
     * 透传进来：
     * <pre>
     * public class MyBlockEntity extends TileEntityEnvironment {
     *     public MyBlockEntity(BlockEntityType&lt;?&gt; type, BlockPos pos, BlockState state) {
     *         super(type, pos, state);
     *     }
     * }
     * </pre>
     *
     * @param type  已注册的方块实体类型。
     * @param pos   方块实体所在坐标。
     * @param state 方块实体所在方块的当前状态。
     */
    protected TileEntityEnvironment(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    // ----------------------------------------------------------------------- //

    @Override
    public Node node() {
        return node;
    }

    @Override
    public void onConnect(final Node node) {
        // This is called when the call to Network.joinOrCreateNetwork(this) in
        // updateEntity was successful, in which case `node == this`.
        // This is also called for any other node that gets connected to the
        // network our node is in, in which case `node` is the added node.
        // If our node is added to an existing network, this is called for each
        // node already in said network.
    }

    @Override
    public void onDisconnect(final Node node) {
        // This is called when this node is removed from its network when the
        // tile entity is removed from the world (see onChunkUnloaded() and
        // setRemoved()), in which case `node == this`.
        // This is also called for each other node that gets removed from the
        // network our node is in, in which case `node` is the removed node.
        // If a net-split occurs this is called for each node that is no longer
        // connected to our node.
    }

    @Override
    public void onMessage(final Message message) {
        // This is used to deliver messages sent via node.sendToXYZ. Handle
        // messages at your own discretion. If you do not wish to handle a
        // message you should *not* throw an exception, though.
    }

    // ----------------------------------------------------------------------- //

    /**
     * 每 tick 的初始化逻辑。
     * <br>
     * 1.21.1 的 {@link BlockEntity} 没有 {@code updateEntity()} 钩子，更新由
     * {@link BlockEntityTicker} 驱动。子类可以在 {@code EntityBlock#getTicker}
     * 里返回 {@link #ticker()}，或者自行在 ticker 中调用本方法。
     * <br>
     * 原实现把“加入网络”推迟到第一次更新，因为 {@code validate()} 阶段还不能访问
     * 相邻方块实体；这一约定保持不变。
     */
    public void updateEntity() {
        // On the first update, try to add our node to nearby networks. We do
        // this in the update logic, not in validate() because we need to access
        // neighboring tile entities, which isn't possible in validate().
        // We could alternatively check node != null && node.network() == null,
        // but this has somewhat better performance, and makes it clearer.
        if (!addedToNetwork) {
            addedToNetwork = true;
            Network.joinOrCreateNetwork(this);
        }
    }

    /**
     * 便捷方法：返回一个只调用 {@link #updateEntity()} 的 ticker，用来替代 1.7.10
     * 里由 {@code TileEntity} 自动驱动的 {@code updateEntity()}。
     * <br>
     * 典型用法：
     * <pre>
     * &#64;Override
     * public &lt;T extends BlockEntity&gt; BlockEntityTicker&lt;T&gt; getTicker(Level level, BlockState state, BlockEntityType&lt;T&gt; type) {
     *     return TileEntityEnvironment.ticker();
     * }
     * </pre>
     *
     * @param <T> 方块实体类型。
     * @return 一个每 tick 调用 {@link #updateEntity()} 的 ticker。
     */
    public static <T extends BlockEntity> BlockEntityTicker<T> ticker() {
        return (level, pos, state, blockEntity) -> {
            if (blockEntity instanceof TileEntityEnvironment environment) {
                environment.updateEntity();
            }
        };
    }

    /**
     * 1.21.1 的 {@link BlockEntity} 已经删除了 {@code onChunkUnload()}，对应的钩子是
     * NeoForge 在 {@code IBlockEntityExtension} 中提供的 {@code onChunkUnloaded()}，
     * 由 {@code LevelChunk} 在区块卸载时调用。
     */
    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        // Make sure to remove the node from its network when its environment,
        // meaning this tile entity, gets unloaded.
        if (node != null) node.remove();
    }

    /**
     * 1.21.1 中 {@code invalidate()} 被 {@link BlockEntity#setRemoved()} 取代。
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        // Make sure to remove the node from its network when its environment,
        // meaning this tile entity, gets unloaded.
        if (node != null) node.remove();
    }

    // ----------------------------------------------------------------------- //

    /**
     * 1.21.1 中 {@code readFromNBT(nbt)} 被
     * {@code loadAdditional(CompoundTag, HolderLookup.Provider)} 取代；
     * 后者是 {@code protected} 的，并且多了注册表查询上下文。
     */
    @Override
    protected void loadAdditional(final CompoundTag nbt, final HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        // The host check may be superfluous for you. It's just there to allow
        // some special cases, where getNode() returns some node managed by
        // some other instance (for example when you have multiple internal
        // nodes in this tile entity).
        if (node != null && node.host() == this) {
            // This restores the node's address, which is required for networks
            // to continue working without interruption across loads. If the
            // node is a power connector this is also required to restore the
            // internal energy buffer of the node.
            node.load(nbt.getCompound("oc:node"));
        }
    }

    /**
     * 1.21.1 中 {@code writeToNBT(nbt)} 被
     * {@code saveAdditional(CompoundTag, HolderLookup.Provider)} 取代。
     */
    @Override
    protected void saveAdditional(final CompoundTag nbt, final HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        // See loadAdditional() regarding host check.
        if (node != null && node.host() == this) {
            final CompoundTag nodeNbt = new CompoundTag();
            node.save(nodeNbt);
            nbt.put("oc:node", nodeNbt);
        }
    }
}
