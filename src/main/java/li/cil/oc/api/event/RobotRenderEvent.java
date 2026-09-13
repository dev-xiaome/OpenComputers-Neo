package li.cil.oc.api.event;

import li.cil.oc.api.driver.item.UpgradeRenderer;
import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.internal.Robot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;

/**
 * Fired directly before the robot's chassis is rendered.
 * <br>
 * If this event is canceled, the chassis will <em>not</em> be rendered.
 * Component items' item renderers will still be invoked, at the possibly
 * modified mount points.
 * <br>
 * <em>Important</em>: the robot instance may be null in this event, in
 * case the render pass is for rendering the robot in an inventory.
 */
public class RobotRenderEvent extends RobotEvent implements ICancellableEvent {
    /**
     * Points on the robot at which component models may be rendered.
     * <br>
     * By convention, components should be rendered in order of their slots,
     * meaning that some components may not be rendered at all, if there are
     * not enough mount points.
     * <br>
     * The equipped tool is rendered at a fixed position, this list does not
     * contain a mount point for it.
     */
    public final MountPoint[] mountPoints;

    public RobotRenderEvent(Agent agent, MountPoint[] mountPoints) {
        super(agent);
        this.mountPoints = mountPoints;
    }

    /**
     * Describes points on the robot model at which components are "mounted",
     * i.e. where component models may be rendered.
     */
    public static class MountPoint {
        /**
         * The position of the mount point, relative to the robot's center.
         * For the purposes of this offset, the robot is always facing south,
         * i.e. the positive Z axis is 'forward'.
         * <br>
         * Note that the rotation is applied <em>before</em> the translation.
         */
        public final Vector3f offset = new Vector3f(0, 0, 0);

        /**
         * The orientation of the mount point as a rotation applied
         * <em>before</em> the offset above is applied.
         * <br>
         * 1.21.1 不再有 {@code GL11.glRotate()}，统一用 JOML 的四元数描述朝向：
         * 需要按“绕任意轴转任意角度”组合时，直接对该四元数做 {@code rotateAxis(...)}
         * 或 {@code mul(...)} 即可。
         */
        public final Quaternionf rotation = new Quaternionf();

        /**
         * The mount point's reference name.
         * <br>
         * This is what's used in {@link UpgradeRenderer#computePreferredMountPoint(ItemStack, Robot, Set)}.
         */
        public final String name;

        public MountPoint() {
            name = null;
        }

        public MountPoint(String name) {
            this.name = name;
        }
    }
}
