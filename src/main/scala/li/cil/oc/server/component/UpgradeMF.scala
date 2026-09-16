package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network._
import li.cil.oc.api.prefab
import li.cil.oc.server.network
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import li.cil.oc.Settings
import li.cil.oc.api
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.minecraft.core.Direction

import scala.jdk.CollectionConverters._

/**
  * Mostly stolen from {@link li.cil.oc.common.tileentity.Adapter}
  *
  * ==1.21.1 迁移要点==
  *  - `ForgeDirection` → `Direction`；`Vec3.createVectorHelper` → `new Vec3`；
  *    `xCoord/yCoord/zCoord` → `x/y/z`。
  *  - `World#getTotalWorldTime` → `Level#getGameTime`，
  *    `world.provider.dimensionId` → `world.dimension()`（`ResourceKey[Level]` 比较）。
  *  - `deviceInfo` 是 Scala `Map`，`getDeviceInfo` 需要 `.asJava`。
  *
  * ==已知降级==
  * TODO(server): 1.7.10 通过 `common.event.BlockChangeHandler` 注册「方块变化」监听，
  * 由它回调 `onBlockChanged()` 重新绑定相邻方块上的驱动。`li.cil.oc.common.event` 包还未进入编译范围，
  * 因此改为在 [[update]] 中按 `Settings#tickFrequency` 轮询调用 [[updateBoundState]]
  * （方块被增删后最迟一个 `tickFrequency` 内被发现；绑定过程是幂等的）。
  * 等 `BlockChangeHandler` 可用后可恢复事件驱动，并把轮询去掉。
  */
class UpgradeMF(val host: EnvironmentHost, val coord: BlockPosition, val dir: Direction) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = api.Network.newNode(this, Visibility.None).
    withConnector().
    create()

  private var otherEnv: Option[api.network.Environment] = None
  private var otherDrv: Option[(ManagedEnvironment, api.driver.SidedBlock)] = None
  private var blockData: Option[BlockData] = None

  override val canUpdate = true

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Bus,
    DeviceAttribute.Description -> "Remote Adapter",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.Scummtech,
    DeviceAttribute.Product -> "ERR NAME NOT FOUND"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  private def otherNode(tile: BlockEntity, f: (Node) => Unit) {
    network.Network.getNetworkNode(tile, dir) match {
      case Some(otherNode) => f(otherNode)
      case _ => // Nothing to do here
    }
  }

  private def updateBoundState(): Unit = {
    if (node != null && node.network != null && coord.world.exists(_.dimension() == host.world.dimension())
      && coord.toVec3.distanceTo(new Vec3(host.xPosition(), host.yPosition(), host.zPosition())) <= Settings.get.mfuRange) {
      host.world.getTileEntity(coord) match {
        case env: BlockEntity with api.network.Environment =>
          otherEnv match {
            case Some(environment: BlockEntity) =>
              otherNode(environment, node.disconnect)
              otherEnv = None
            case _ => // Nothing to do here.
          }
          otherEnv = Some(env)
          // Remove any driver that might be there.
          otherDrv match {
            case Some((environment, driver)) =>
              node.disconnect(environment.node)
              environment.save(blockData.get.data)
              Option(environment.node).foreach(_.remove())
              otherDrv = None
            case _ => // Nothing to do here.
          }
          otherNode(env, node.connect)
        case _ =>
          // Remove any environment that might have been there.
          otherEnv match {
            case Some(environment: BlockEntity) =>
              otherNode(environment, node.disconnect)
              otherEnv = None
            case _ => // Nothing to do here.
          }
          val (world, x, y, z) = (coord.world.get, coord.x, coord.y, coord.z)
          Option(api.Driver.driverFor(world, coord.x, coord.y, coord.z, dir)) match {
            case Some(newDriver) =>
              otherDrv match {
                case Some((oldEnvironment, driver)) =>
                  if (newDriver != driver) {
                    // This is... odd. Maybe moved by some other mod? First, clean up.
                    otherDrv = None
                    blockData = None
                    node.disconnect(oldEnvironment.node)

                    // Then rebuild - if we have something.
                    val environment = newDriver.createEnvironment(world, x, y, z, dir)
                    if (environment != null) {
                      otherDrv = Some((environment, newDriver))
                      blockData = Some(new BlockData(environment.getClass.getName, new CompoundTag()))
                      node.connect(environment.node)
                    }
                  } // else: the more things change, the more they stay the same.
                case _ =>
                  // A challenger appears. Maybe.
                  val environment = newDriver.createEnvironment(world, x, y, z, dir)
                  if (environment != null) {
                    otherDrv = Some((environment, newDriver))
                    blockData match {
                      case Some(data) if data.name == environment.getClass.getName =>
                        environment.load(data.data)
                      case _ =>
                    }
                    blockData = Some(new BlockData(environment.getClass.getName, new CompoundTag()))
                    node.connect(environment.node)
                  }
              }
            case _ => otherDrv match {
              case Some((environment, driver)) =>
                // We had something there, but it's gone now...
                node.disconnect(environment.node)
                environment.save(blockData.get.data)
                Option(environment.node).foreach(_.remove())
                otherDrv = None
              case _ => // Nothing before, nothing now.
            }
          }
      }
    }
  }

  private def disconnect(): Unit = {
    otherEnv match {
      case Some(environment: BlockEntity) =>
        otherNode(environment, node.disconnect)
        otherEnv = None
      case _ => // Nothing to do here.
    }
    otherDrv match {
      case Some((environment, driver)) =>
        node.disconnect(environment.node)
        environment.save(blockData.get.data)
        Option(environment.node).foreach(_.remove())
        otherDrv = None
      case _ => // Nothing to do here.
    }
  }

  /** 方块变化时的重新绑定入口（现在由 [[update]] 轮询调用，见类注释的降级说明）。 */
  def onBlockChanged(): Unit = updateBoundState()

  override def update(): Unit = {
    super.update()
    otherDrv match {
      case Some((env, drv)) if env.canUpdate => env.update()
      case _ => // No driver
    }
    if (host.world.getGameTime % Settings.get.tickFrequency == 0) {
      // TODO(server): 旧实现由 `common.event.BlockChangeHandler` 在方块变化时触发
      // `onBlockChanged()`；该对象尚未进入编译范围，这里按 tickFrequency 轮询重新绑定。
      updateBoundState()
      if (!node.tryChangeBuffer(-Settings.get.mfuCost * Settings.get.tickFrequency
        * coord.toVec3.distanceTo(new Vec3(host.xPosition(), host.yPosition(), host.zPosition())))) {
        disconnect()
      }
    }
  }

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      // Not checking for range yet because host may be a moving adapter, who knows?
      updateBoundState()
    }
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    otherEnv match {
      case Some(env: BlockEntity) => otherNode(env, (otherNode) => if (node == otherNode) otherEnv = None)
      case _ => // No environment
    }
    otherDrv match {
      case Some((env, drv)) if node == env.node => otherDrv = None
      case _ => // No driver
    }
  }

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    Option(nbt.getCompound(Settings.namespace + "adapter.block")) match {
      case Some(blockNbt: CompoundTag) =>
        if (blockNbt.contains("name") && blockNbt.contains("data")) {
          blockData = Some(new BlockData(blockNbt.getString("name"), blockNbt.getCompound("data")))
        }
      case _ => // Invalid tag
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    val blockNbt = new CompoundTag()
    blockData.foreach({ data =>
      otherDrv.foreach(_._1.save(data.data))
      blockNbt.putString("name", data.name)
      blockNbt.put("data", data.data)
    })
    nbt.put(Settings.namespace + "adapter.block", blockNbt)
  }

  // ----------------------------------------------------------------------- //

  private class BlockData(val name: String, val data: CompoundTag)

}
