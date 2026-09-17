package net.minecraft.world.level.pathfinder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class Path {
    private final List<Node> nodes;
    @Nullable
    private Path.DebugData debugData;
    private int nextNodeIndex;
    private final BlockPos target;
    private final float distToTarget;
    private final boolean reached;

    public Path(List<Node> nodes, BlockPos target, boolean reached) {
        this.nodes = nodes;
        this.target = target;
        this.distToTarget = nodes.isEmpty() ? Float.MAX_VALUE : this.nodes.get(this.nodes.size() - 1).distanceManhattan(this.target);
        this.reached = reached;
    }

    public void advance() {
        this.nextNodeIndex++;
    }

    public boolean notStarted() {
        return this.nextNodeIndex <= 0;
    }

    public boolean isDone() {
        return this.nextNodeIndex >= this.nodes.size();
    }

    @Nullable
    public Node getEndNode() {
        return !this.nodes.isEmpty() ? this.nodes.get(this.nodes.size() - 1) : null;
    }

    /**
     * Returns the {@link net.minecraft.world.level.pathfinder.Node} located at the specified index, usually the current one.
     */
    public Node getNode(int index) {
        return this.nodes.get(index);
    }

    public void truncateNodes(int length) {
        if (this.nodes.size() > length) {
            this.nodes.subList(length, this.nodes.size()).clear();
        }
    }

    public void replaceNode(int index, Node point) {
        this.nodes.set(index, point);
    }

    public int getNodeCount() {
        return this.nodes.size();
    }

    public int getNextNodeIndex() {
        return this.nextNodeIndex;
    }

    public void setNextNodeIndex(int currentPathIndex) {
        this.nextNodeIndex = currentPathIndex;
    }

    /**
     * Gets the vector of the {@link net.minecraft.world.level.pathfinder.Node} associated with the given index.
     */
    public Vec3 getEntityPosAtNode(Entity entity, int index) {
        Node node = this.nodes.get(index);
        double d0 = (double)node.x + (double)((int)(entity.getBbWidth() + 1.0F)) * 0.5;
        double d1 = (double)node.y;
        double d2 = (double)node.z + (double)((int)(entity.getBbWidth() + 1.0F)) * 0.5;
        return new Vec3(d0, d1, d2);
    }

    public BlockPos getNodePos(int index) {
        return this.nodes.get(index).asBlockPos();
    }

    /**
     * @return the current {@code PathEntity} target node as a {@code Vec3D}
     */
    public Vec3 getNextEntityPos(Entity entity) {
        return this.getEntityPosAtNode(entity, this.nextNodeIndex);
    }

    public BlockPos getNextNodePos() {
        return this.nodes.get(this.nextNodeIndex).asBlockPos();
    }

    public Node getNextNode() {
        return this.nodes.get(this.nextNodeIndex);
    }

    @Nullable
    public Node getPreviousNode() {
        return this.nextNodeIndex > 0 ? this.nodes.get(this.nextNodeIndex - 1) : null;
    }

    /**
     * Returns {@code true} if the EntityPath are the same. Non instance related equals.
     */
    public boolean sameAs(@Nullable Path pathentity) {
        if (pathentity == null) {
            return false;
        } else if (pathentity.nodes.size() != this.nodes.size()) {
            return false;
        } else {
            for (int i = 0; i < this.nodes.size(); i++) {
                Node node = this.nodes.get(i);
                Node node1 = pathentity.nodes.get(i);
                if (node.x != node1.x || node.y != node1.y || node.z != node1.z) {
                    return false;
                }
            }

            return true;
        }
    }

    public boolean canReach() {
        return this.reached;
    }

    @VisibleForDebug
    void setDebug(Node[] openSet, Node[] closedSet, Set<Target> targetNodes) {
        this.debugData = new Path.DebugData(openSet, closedSet, targetNodes);
    }

    @Nullable
    public Path.DebugData debugData() {
        return this.debugData;
    }

    public void writeToStream(FriendlyByteBuf buffer) {
        if (this.debugData != null && !this.debugData.targetNodes.isEmpty()) {
            buffer.writeBoolean(this.reached);
            buffer.writeInt(this.nextNodeIndex);
            buffer.writeBlockPos(this.target);
            buffer.writeCollection(this.nodes, (p_294084_, p_294085_) -> p_294085_.writeToStream(p_294084_));
            this.debugData.write(buffer);
        }
    }

    public static Path createFromStream(FriendlyByteBuf buf) {
        boolean flag = buf.readBoolean();
        int i = buf.readInt();
        BlockPos blockpos = buf.readBlockPos();
        List<Node> list = buf.readList(Node::createFromStream);
        Path.DebugData path$debugdata = Path.DebugData.read(buf);
        Path path = new Path(list, blockpos, flag);
        path.debugData = path$debugdata;
        path.nextNodeIndex = i;
        return path;
    }

    @Override
    public String toString() {
        return "Path(length=" + this.nodes.size() + ")";
    }

    public BlockPos getTarget() {
        return this.target;
    }

    public float getDistToTarget() {
        return this.distToTarget;
    }

    static Node[] readNodeArray(FriendlyByteBuf buffer) {
        Node[] anode = new Node[buffer.readVarInt()];

        for (int i = 0; i < anode.length; i++) {
            anode[i] = Node.createFromStream(buffer);
        }

        return anode;
    }

    static void writeNodeArray(FriendlyByteBuf buffer, Node[] nodeArray) {
        buffer.writeVarInt(nodeArray.length);

        for (Node node : nodeArray) {
            node.writeToStream(buffer);
        }
    }

    public Path copy() {
        Path path = new Path(this.nodes, this.target, this.reached);
        path.debugData = this.debugData;
        path.nextNodeIndex = this.nextNodeIndex;
        return path;
    }

    public static record DebugData(Node[] openSet, Node[] closedSet, Set<Target> targetNodes) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeCollection(this.targetNodes, (p_295084_, p_294361_) -> p_294361_.writeToStream(p_295084_));
            Path.writeNodeArray(buffer, this.openSet);
            Path.writeNodeArray(buffer, this.closedSet);
        }

        public static Path.DebugData read(FriendlyByteBuf buffer) {
            HashSet<Target> hashset = buffer.readCollection(HashSet::new, Target::createFromStream);
            Node[] anode = Path.readNodeArray(buffer);
            Node[] anode1 = Path.readNodeArray(buffer);
            return new Path.DebugData(anode, anode1, hashset);
        }
    }
}
