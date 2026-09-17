package net.minecraft.world.level.pathfinder;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class Node {
    public final int x;
    public final int y;
    public final int z;
    private final int hash;
    /**
     * The index in the PathHeap. -1 if not assigned.
     */
    public int heapIdx = -1;
    /**
     * The total cost of all path points up to this one. Corresponds to the A* g-score.
     */
    public float g;
    /**
     * The estimated cost from this path point to the target. Corresponds to the A* h-score.
     */
    public float h;
    /**
     * The total cost of the path containing this path point. Used as sort criteria in PathHeap. Corresponds to the A* f-score.
     */
    public float f;
    @Nullable
    public Node cameFrom;
    public boolean closed;
    public float walkedDistance;
    /**
     * The additional cost of the path point. If negative, the path point will be sorted out by NodeProcessors.
     */
    public float costMalus;
    public PathType type = PathType.BLOCKED;

    public Node(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hash = createHash(x, y, z);
    }

    public Node cloneAndMove(int x, int y, int z) {
        Node node = new Node(x, y, z);
        node.heapIdx = this.heapIdx;
        node.g = this.g;
        node.h = this.h;
        node.f = this.f;
        node.cameFrom = this.cameFrom;
        node.closed = this.closed;
        node.walkedDistance = this.walkedDistance;
        node.costMalus = this.costMalus;
        node.type = this.type;
        return node;
    }

    public static int createHash(int x, int y, int z) {
        return y & 0xFF | (x & 32767) << 8 | (z & 32767) << 24 | (x < 0 ? Integer.MIN_VALUE : 0) | (z < 0 ? 32768 : 0);
    }

    /**
     * Returns the linear distance to another path point
     */
    public float distanceTo(Node point) {
        float f = (float)(point.x - this.x);
        float f1 = (float)(point.y - this.y);
        float f2 = (float)(point.z - this.z);
        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public float distanceToXZ(Node point) {
        float f = (float)(point.x - this.x);
        float f1 = (float)(point.z - this.z);
        return Mth.sqrt(f * f + f1 * f1);
    }

    public float distanceTo(BlockPos pos) {
        float f = (float)(pos.getX() - this.x);
        float f1 = (float)(pos.getY() - this.y);
        float f2 = (float)(pos.getZ() - this.z);
        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    /**
     * Returns the squared distance to another path point
     */
    public float distanceToSqr(Node point) {
        float f = (float)(point.x - this.x);
        float f1 = (float)(point.y - this.y);
        float f2 = (float)(point.z - this.z);
        return f * f + f1 * f1 + f2 * f2;
    }

    public float distanceToSqr(BlockPos pos) {
        float f = (float)(pos.getX() - this.x);
        float f1 = (float)(pos.getY() - this.y);
        float f2 = (float)(pos.getZ() - this.z);
        return f * f + f1 * f1 + f2 * f2;
    }

    public float distanceManhattan(Node point) {
        float f = (float)Math.abs(point.x - this.x);
        float f1 = (float)Math.abs(point.y - this.y);
        float f2 = (float)Math.abs(point.z - this.z);
        return f + f1 + f2;
    }

    public float distanceManhattan(BlockPos pos) {
        float f = (float)Math.abs(pos.getX() - this.x);
        float f1 = (float)Math.abs(pos.getY() - this.y);
        float f2 = (float)Math.abs(pos.getZ() - this.z);
        return f + f1 + f2;
    }

    public BlockPos asBlockPos() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public Vec3 asVec3() {
        return new Vec3((double)this.x, (double)this.y, (double)this.z);
    }

    @Override
    public boolean equals(Object other) {
        return !(other instanceof Node node) ? false : this.hash == node.hash && this.x == node.x && this.y == node.y && this.z == node.z;
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    public boolean inOpenSet() {
        return this.heapIdx >= 0;
    }

    @Override
    public String toString() {
        return "Node{x=" + this.x + ", y=" + this.y + ", z=" + this.z + "}";
    }

    public void writeToStream(FriendlyByteBuf buffer) {
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeFloat(this.walkedDistance);
        buffer.writeFloat(this.costMalus);
        buffer.writeBoolean(this.closed);
        buffer.writeEnum(this.type);
        buffer.writeFloat(this.f);
    }

    public static Node createFromStream(FriendlyByteBuf buffer) {
        Node node = new Node(buffer.readInt(), buffer.readInt(), buffer.readInt());
        readContents(buffer, node);
        return node;
    }

    protected static void readContents(FriendlyByteBuf buffer, Node node) {
        node.walkedDistance = buffer.readFloat();
        node.costMalus = buffer.readFloat();
        node.closed = buffer.readBoolean();
        node.type = buffer.readEnum(PathType.class);
        node.f = buffer.readFloat();
    }
}
