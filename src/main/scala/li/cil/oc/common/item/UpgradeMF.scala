package li.cil.oc.common.item

import li.cil.oc.util.BlockPosition
import li.cil.oc.{Localization, Settings}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「MFU 升级」（原 `li.cil.oc.common.item.UpgradeMF`）：记录多方块坐标用于远程访问。
 *
 * 1.21.1 迁移要点：
 *  - `player.worldObj.isRemote` → `player.level().isClientSide`
 *  - `world.provider.dimensionId`（int）→ `world.dimension().location().toString`（字符串），
 *    因此坐标数组最后一项由「维度 id」改为「维度 id 的字符串哈希」不合适，
 *    这里改为写入维度字符串到独立的 NBT 键，保持 `coord` 数组仍是纯 int 结构。
 *  - `stack.setTagCompound(new CompoundTag())` → 隐式扩展 `stack.setTag(tag)`
 */
class UpgradeMF(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier {

  override def onItemUseFirst(stack: ItemStack, player: Player, position: BlockPosition,
                              side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    if (!player.level().isClientSide && player.isShiftKeyDown) {
      val data = if (stack.hasTag()) stack.getTag() else {
        val tag = new CompoundTag()
        stack.setTag(tag)
        tag
      }
      data.putIntArray(Settings.namespace + "coord",
        Array(position.x, position.y, position.z, side))
      data.putString(Settings.namespace + "dimension", player.level().dimension().location().toString)
      return true
    }
    super.onItemUseFirst(stack, player, position, side, hitX, hitY, hitZ)
  }

  override protected def tooltipExtended(stack: ItemStack, tooltip: java.util.List[String]): Unit = {
    val linked = stack.hasTag() && stack.getTag().contains(Settings.namespace + "coord")
    tooltip.add(Localization.Tooltip.MFULinked(linked))
  }
}
