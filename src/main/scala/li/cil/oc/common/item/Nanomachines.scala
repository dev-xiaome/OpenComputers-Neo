package li.cil.oc.common.item

import li.cil.oc.util.ItemStackNBTExtensions._

import li.cil.oc.api
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.common.item.data.NanomachineData
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level

/**
 * 「纳米机器」（原 `li.cil.oc.common.item.Nanomachines`）。
 *
 * 1.21.1 迁移要点：
 *  - `player.setItemInUse(stack, n)` → `Item#use` 返回 `InteractionResultHolder.consume`
 *  - `EnumAction.eat` → `UseAnim.EAT`
 *  - `EnumRarity.uncommon` → 注册期 `Item.Properties#rarity(Rarity.UNCOMMON)`
 *    （见 `li.cil.oc.common.init.Registry.Items`）
 *  - `li.cil.oc.common.nanomachines.ControllerImpl` 尚未移植：
 *    `uuid` 与 `configuration` 两个成员用**结构类型**访问，等该包移植完成后
 *    换成实际类型即可恢复编译期校验（原实现用 `controller.configurable` 区分
 *    「已有配置」与「需要重新生成」两种情形）。
 */
class Nanomachines(props: Item.Properties) extends Item(props) with traits.Delegate {

  /** 纳米机器控制器中与物品数据相关的最小接口（占位结构类型）。 */
  type ConfigurableController = Controller {
    def uuid: String
    def uuid_=(value: String): Unit
    def configuration: { def load(nbt: CompoundTag): Unit }
  }

  override def tooltipLines(stack: ItemStack, player: Player,
                            tooltip: java.util.List[String], advanced: Boolean): Unit = {
    super.tooltipLines(stack, player, tooltip, advanced)
    if (stack.hasTag()) {
      val data = new NanomachineData(stack)
      if (data.uuid != null && data.uuid.nonEmpty) {
        val shown = if (data.uuid.length > 13) data.uuid.substring(0, 13) + "..." else data.uuid
        tooltip.add("§8" + shown + "§7")
      }
    }
  }

  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    InteractionResultHolder.consume(player.getItemInHand(hand))
  }

  override def getItemUseAction(stack: ItemStack): UseAnim = UseAnim.EAT

  override def getMaxItemUseDuration(stack: ItemStack): Int = 32

  override def onEaten(stack: ItemStack, world: Level, player: Player): ItemStack = {
    if (!world.isClientSide) {
      val data = new NanomachineData(stack)

      // Re-install to get new address, make sure we're configured.
      api.Nanomachines.uninstallController(player)
      api.Nanomachines.installController(player) match {
        case controller: ConfigurableController =>
          data.configuration match {
            case Some(nbt) =>
              if (data.uuid != null && data.uuid.nonEmpty) {
                controller.uuid = data.uuid
              }
              controller.configuration.load(nbt)
            case _ => controller.reconfigure()
          }
        case controller if controller != null => controller.reconfigure() // Huh.
        case _ =>
      }
    }
    val result = stack.copy()
    result.shrink(1)
    result
  }
}
