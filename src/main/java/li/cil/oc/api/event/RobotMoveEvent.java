package li.cil.oc.api.event;

import net.neoforged.bus.api.ICancellableEvent;
import li.cil.oc.api.internal.Agent;
import net.minecraft.core.Direction;

public abstract class RobotMoveEvent extends RobotEvent {
    /**
     * The direction in which the robot will be moving.
     */
    public final Direction direction;

    protected RobotMoveEvent(Agent agent, Direction direction) {
        super(agent);
        this.direction = direction;
    }

    /**
     * Fired when a robot is about to move.
     * <br>
     * Canceling the event will prevent the robot from moving.
     */
    @Cancelable
    public static class Pre extends RobotMoveEvent {
        public Pre(Agent agent, Direction direction) {
            super(agent, direction);
        }
    }

    /**
     * Fired after a robot moved.
     */
    public static class Post extends RobotMoveEvent {
        public Post(Agent agent, Direction direction) {
            super(agent, direction);
        }
    }
}
