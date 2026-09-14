package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.{Node, Visibility}
import li.cil.oc.api.prefab
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState

/**
 * 运动传感器（对应 1.7.10 的 `common.tileentity.MotionSensor`）。
 *
 * 方块实体只是「组件宿主」：检测逻辑在服务端组件里，方块实体负责创建它、暴露 `node`，
 * 每刻驱动它的 `update()`，并在存档时转发 `load` / `save`。
 *
 * 纹理：所有面 = MotionSensor。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `updateEntity()` → [[traits.TileEntity.tick]]（覆写时先调 `super.tick()`）。
 *  - `readFromNBTForServer` / `writeToNBTForServer` 在 1.21.1 是 `protected` 钩子。
 *
 * ==降级说明==
 * TODO(server.component): 原实现为 `val motionSensor = new component.MotionSensor(this)`
 * （`li.cil.oc.server.component.MotionSensor`）。`server` 包尚未移植，这里换成本文件内的
 * [[MotionSensor.Placeholder]]：保留 `motionSensor` / `node` / `update` / `load` / `save`
 * 这些对外名字与节点形状（组件名 `motion_sensor` + 连接器），实体检测与
 * `motion` 信号广播待组件层移植后补回。
 */
class MotionSensor(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment {

  val motionSensor = new MotionSensor.Placeholder(this)

  def node: Node = motionSensor.node

  override def canUpdate: Boolean = isServer

  override def tick(): Unit = {
    super.tick()
    motionSensor.update()
  }

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    motionSensor.load(nbt)
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    motionSensor.save(nbt)
  }
}

object MotionSensor {

  /**
   * 运动传感器组件的**最小占位实现**（原 `li.cil.oc.server.component.MotionSensor`）。
   *
   * 保留原组件的公开成员名（`node` / `sensitivity` / `getSensitivity` / `setSensitivity` /
   * `update` / `load` / `save`）与节点形状，检测逻辑本身是空实现。
   *
   * TODO(server.component): `li.cil.oc.server.component.MotionSensor` 移植完成后删除本类。
   * 恢复时需要的实现要点（原组件）：
   *  - 每 10 刻用半径 8 的包围盒采样 `LivingEntity`，过滤出「在范围内且视线未被遮挡」的实体；
   *  - 新出现的实体必定触发，已知实体位移超过 `sensitivity` 时触发；
   *  - 触发时 `node.sendToReachable("computer.signal", "motion", dx, dy, dz[, name])`。
   */
  private[tileentity] class Placeholder(val host: api.network.EnvironmentHost)
    extends prefab.ManagedEnvironment {

    setNode(api.Network.newNode(this, Visibility.Network).
      withComponent("motion_sensor").
      withConnector().
      create())

    private final val SensitivityTag = Settings.namespace + "sensitivity"

    private val radius = 8

    private var sensitivity = 0.4

    override def update(): Unit = {
      // TODO(server.component): 实体检测尚未移植，这里不做任何事。
    }

    def getSensitivity: Double = sensitivity

    def setSensitivity(value: Double): Double = {
      val oldValue = sensitivity
      sensitivity = math.max(0.2, value)
      oldValue
    }

    override def load(nbt: CompoundTag): Unit = {
      super.load(nbt)
      sensitivity = nbt.getDouble(SensitivityTag)
    }

    override def save(nbt: CompoundTag): Unit = {
      super.save(nbt)
      nbt.putDouble(SensitivityTag, sensitivity)
    }
  }
}
