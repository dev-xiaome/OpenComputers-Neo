package li.cil.oc.api.event;

import li.cil.oc.api.internal.Agent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class RobotPlaceBlockEvent extends RobotEvent {
    /**
     * The item that is used to place the block.
     */
    public final ItemStack stack;

    /**
     * The world in which the block will be placed.
     */
    public final Level world;

    /**
     * The coordinates at which the block will be placed.
     */
    public final int x, y, z;

    protected RobotPlaceBlockEvent(Agent agent, ItemStack stack, Level world, int x, int y, int z) {
        super(agent);
        this.stack = stack;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Fired when a robot is about to place a block.
     * <br>
     * Canceling this event will prevent the block from being placed.
     */
    public static class Pre extends RobotPlaceBlockEvent implements ICancellableEvent {
        public Pre(Agent agent, ItemStack stack, Level world, int x, int y, int z) {
            super(agent, stack, world, x, y, z);
        }
    }

    /**
     * Fired after a robot placed a block.
     */
    public static class Post extends RobotPlaceBlockEvent {
        public Post(Agent agent, ItemStack stack, Level world, int x, int y, int z) {
            super(agent, stack, world, x, y, z);
        }
    }
}
