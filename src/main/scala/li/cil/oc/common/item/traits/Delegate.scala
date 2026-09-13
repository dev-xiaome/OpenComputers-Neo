package li.cil.oc.common.item.traits

import java.util

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.client.KeyBindings
import li.cil.oc.common.item.Delegator
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ItemCosts
import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.UseAnim
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

trait Delegate {
  type Icon = net.minecraft.util.IIcon
  type IconRegister = net.minecraft.client.renderer.texture.IIconRegister

  def parent: Delegator

  def unlocalizedName = getClass.getSimpleName

  protected def tooltipName = Option(unlocalizedName)

  protected def tooltipData = Seq.empty[Any]

  var showInItemList = true

  val itemId = parent.add(this)

  private var _icon: Option[Icon] = None

  def maxStackSize = 64

  def createItemStack(amount: Int = 1) = new ItemStack(parent, amount, itemId)

  // ----------------------------------------------------------------------- //

  def doesSneakBypassUse(position: BlockPosition, player: Player) = false

  def onItemUseFirst(stack: ItemStack, player: Player, position: BlockPosition, side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = false

  def onItemUse(stack: ItemStack, player: Player, position: BlockPosition, side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = false

  def onItemRightClick(stack: ItemStack, world: Level, player: Player): ItemStack = stack

  def getItemUseAction(stack: ItemStack): EnumAction = EnumAction.none

  def getMaxItemUseDuration(stack: ItemStack) = 0

  def onEaten(stack: ItemStack, world: Level, player: Player): ItemStack = stack

  def onPlayerStoppedUsing(stack: ItemStack, player: Player, duration: Int) {}

  def update(stack: ItemStack, world: Level, player: Entity, slot: Int, selected: Boolean) {}

  // ----------------------------------------------------------------------- //

  def rarity(stack: ItemStack) = Rarity.byTier(tierFromDriver(stack))

  protected def tierFromDriver(stack: ItemStack) =
    api.Driver.driverFor(stack) match {
      case driver: api.driver.Item => driver.tier(stack)
      case _ => 0
    }

  def color(stack: ItemStack, pass: Int) = 0xFFFFFF

  def getContainerItem(stack: ItemStack): ItemStack = null

  def hasContainerItem(stack: ItemStack): Boolean = false

  def displayName(stack: ItemStack): Option[String] = None

  @SideOnly(Dist.CLIENT)
  def tooltipLines(stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {
    if (tooltipName.isDefined) {
      tooltip.addAll(Tooltip.get(tooltipName.get, tooltipData: _*))
      tooltipExtended(stack, tooltip)
    }
    tooltipCosts(stack, tooltip)
  }

  // For stuff that goes to the normal 'extended' tooltip, before the costs.
  protected def tooltipExtended(stack: ItemStack, tooltip: java.util.List[String]) {}

  protected def tooltipCosts(stack: ItemStack, tooltip: java.util.List[String]): Unit = {
    if (ItemCosts.hasCosts(stack)) {
      if (KeyBindings.showMaterialCosts) {
        ItemCosts.addTooltip(stack, tooltip.asInstanceOf[util.List[String]])
      }
      else {
        tooltip.add(Localization.localizeImmediately(
          Settings.namespace + "tooltip.MaterialCosts",
          KeyBindings.getKeyBindingName(KeyBindings.materialCosts)))
      }
    }
    if (stack.hasTagCompound && stack.getTagCompound.contains(Settings.namespace + "data")) {
      val data = stack.getTagCompound.getCompound(Settings.namespace + "data")
      if (data.contains("node") && data.getCompound("node").contains("address")) {
        tooltip.add("§8" + data.getCompound("node").getString("address").substring(0, 13) + "...§7")
      }
    }
  }

  def isDamageable = false

  def damage(stack: ItemStack) = 0

  def maxDamage(stack: ItemStack) = 0

  @SideOnly(Dist.CLIENT)
  def icon: Option[Icon] = _icon

  @SideOnly(Dist.CLIENT)
  protected def icon_=(value: Icon) = _icon = Option(value)

  @SideOnly(Dist.CLIENT)
  def icon(stack: ItemStack, pass: Int): Option[Icon] = icon

  @SideOnly(Dist.CLIENT)
  def registerIcons(iconRegister: IconRegister): Unit = {
    icon = iconRegister.registerIcon(Settings.resourceDomain + ":" + unlocalizedName)
  }

  // ----------------------------------------------------------------------- //

  def equals(stack: ItemStack) =
    stack != null && stack.getItem == parent && parent.subItem(stack).contains(this)
}
