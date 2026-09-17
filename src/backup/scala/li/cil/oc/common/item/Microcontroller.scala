package li.cil.oc.common.item

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.MicrocontrollerData
import net.minecraft.world.item.ItemStack

/**
 * 微控制器相关的工厂方法（原 1.7.10 放在 `common/init/Items.scala` 里）。
 *
 * 1.21.1 里没有独立的 `Microcontroller` 物品类：微控制器是**方块**
 * （`Constants.BlockName.Microcontroller`），这里只承载「创造模式标签页里的
 * 预配置微控制器堆叠」这一个由物品层生成的辅助功能。
 *
 * 变异说明：原版会用 `LuaStateFactory.setDefaultArch(...)` 给 CPU 写入默认架构，
 * 但 `server/machine/luac` 未移植，这里退化为直接使用 CPU 堆叠。
 */
object Microcontroller {

  /** `safeGetStack` 语义：物品未注册时返回空堆叠。 */
  private def safeGetStack(name: String): ItemStack = {
    val info = api.Items.get(name)
    if (info == null) ItemStack.EMPTY else info.createItemStack(1)
  }

  /** 创造模式标签页里的预配置微控制器（原 `Items.createConfiguredMicrocontroller`）。 */
  def createConfiguredMicrocontroller(): ItemStack = {
    val data = new MicrocontrollerData(Constants.BlockName.Microcontroller)

    data.tier = Tier.Four
    data.storedEnergy = Settings.get.bufferMicrocontroller.toInt
    data.components = Array(
      safeGetStack(Constants.ItemName.SignUpgrade),
      safeGetStack(Constants.ItemName.PistonUpgrade),

      safeGetStack(Constants.ItemName.RedstoneCardTier2),
      safeGetStack(Constants.ItemName.WirelessNetworkCardTier2),

      // TODO(架构): 恢复 `LuaStateFactory.setDefaultArch(safeGetStack(ItemName.CPUTier3))`
      safeGetStack(Constants.ItemName.CPUTier3),
      safeGetStack(Constants.ItemName.RAMTier6),
      safeGetStack(Constants.ItemName.RAMTier6)
    )

    data.createItemStack()
  }
}
