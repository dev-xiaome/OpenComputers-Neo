package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.{Connector, Visibility}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.level.block.state.BlockState

import scala.jdk.CollectionConverters._

/**
 * 能量转换器（对应 1.7.10 的 `common.tileentity.PowerConverter`）。
 *
 * 把外部模组的能量通过 [[traits.PowerAcceptor]]（`traits.power` 系列）注入自己的
 * 连接器节点，从而给 OC 网络供电。<br>
 * 节点本身是 `Visibility.None` 的纯连接器（没有组件名），因此只参与能量平衡、不出现在
 * 计算机的组件列表里。
 *
 * 纹理：下 = PowerConverterTop，其它四面 = PowerConverterSide。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - 删除 `@SideOnly(Side.CLIENT)`；[[hasConnector]] 仍然只应在客户端渲染时调用。
 *  - `WrapAsJava` → 显式 `.asJava`。
 *  - IC2 / AE2 等模组接口已随之删除（见 `traits.PowerAcceptor`）。
 */
class PowerConverter(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.PowerAcceptor with traits.Environment with traits.NotAnalyzable with DeviceInfo {

  val node = api.Network.newNode(this, Visibility.None).
    withConnector(Settings.get.bufferConverter).
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Power,
    DeviceAttribute.Description -> "Power converter",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Transgizer-PX5",
    DeviceAttribute.Capacity -> energyThroughput.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // 只应在客户端渲染时调用（原 `@SideOnly(Side.CLIENT)`，1.21.1 已删除该注解）。
  override protected def hasConnector(side: Direction): Boolean = true

  override protected def connector(side: Direction): Option[Connector] = Option(node)

  override def energyThroughput: Double = Settings.get.powerConverterRate

  override def canUpdate: Boolean = isServer
}
