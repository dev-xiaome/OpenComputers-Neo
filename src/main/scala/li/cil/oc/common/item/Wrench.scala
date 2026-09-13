package li.cil.oc.common.item

import li.cil.oc.api
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import net.minecraft.world.level.block.Block
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.entity.item.EntityMinecart
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

@Injectable.InterfaceList(Array(
  new Injectable.Interface(value = "appeng.api.implementations.items.IAEWrench", modid = Mods.IDs.AppliedEnergistics2),
  new Injectable.Interface(value = "buildcraft.api.tools.IToolWrench", modid = Mods.IDs.BuildCraftTools),
  new Injectable.Interface(value = "com.bluepowermod.api.misc.IScrewdriver", modid = Mods.IDs.BluePower),
  new Injectable.Interface(value = "cofh.api.item.IToolHammer", modid = Mods.IDs.CoFHItem),
  new Injectable.Interface(value = "crazypants.enderio.tool.ITool", modid = Mods.IDs.EnderIO),
  new Injectable.Interface(value = "mekanism.api.IMekWrench", modid = Mods.IDs.Mekanism),
  new Injectable.Interface(value = "powercrystals.minefactoryreloaded.api.IMFRHammer", modid = Mods.IDs.MineFactoryReloaded),
  new Injectable.Interface(value = "mrtjp.projectred.api.IScrewdriver", modid = Mods.IDs.ProjectRedCore),
  new Injectable.Interface(value = "mods.railcraft.api.core.items.IToolCrowbar", modid = Mods.IDs.Railcraft),
  new Injectable.Interface(value = "ic2.api.item.IBoxable", modid = Mods.IDs.IndustrialCraft2)
))
class Wrench extends traits.SimpleItem with api.internal.Wrench {
  setHarvestLevel("wrench", 1)
  setMaxStackSize(1)

  override def doesSneakBypassUse(world: Level, x: Int, y: Int, z: Int, player: Player): Boolean = true

  override def onItemUseFirst(stack: ItemStack, player: Player, world: Level, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    world.blockExists(x, y, z) && world.canMineBlock(player, x, y, z) && (world.getBlock(x, y, z) match {
      case block: Block if block.rotateBlock(world, x, y, z, Direction.getOrientation(side)) =>
        block.onNeighborBlockChange(world, x, y, z, Blocks.air)
        player.swingItem()
        !world.isRemote
      case _ =>
        super.onItemUseFirst(stack, player, world, x, y, z, side, hitX, hitY, hitZ)
    })
  }

  def useWrenchOnBlock(player: Player, world: Level, x: Int, y: Int, z: Int, simulate: Boolean): Boolean = {
    if (!simulate) player.swingItem()
    true
  }

  // Applied Energistics 2

  def canWrench(stack: ItemStack, player: Player, x: Int, y: Int, z: Int): Boolean = true

  // BluePower
  def damage(stack: ItemStack, damage: Int, player: Player, simulated: Boolean): Boolean = damage == 0

  // BuildCraft

  def canWrench(player: Player, x: Int, y: Int, z: Int): Boolean = true

  def wrenchUsed(player: Player, x: Int, y: Int, z: Int): Unit = player.swingItem()

  def canWrench(player: Player, entity: Entity): Boolean = true

  def wrenchUsed(player: Player, entity: Entity): Unit = player.swingItem()

  // CoFH

  def isUsable(stack: ItemStack, player: LivingEntity, x: Int, y: Int, z: Int): Boolean = true

  def toolUsed(stack: ItemStack, player: LivingEntity, x: Int, y: Int, z: Int): Unit = player.swingItem()

  // EnderIO

  def canUse(stack: ItemStack, player: Player, x: Int, y: Int, z: Int): Boolean = true

  def used(stack: ItemStack, player: Player, x: Int, y: Int, z: Int): Unit = {}

  // Mekanism

  def canUseWrench(player: Player, x: Int, y: Int, z: Int): Boolean = true

  // Project Red

  def canUse(entityPlayer: Player, itemStack: ItemStack): Boolean = true

  // pre v4.7
  def damageScrewdriver(world: Level, player: Player): Unit = {}

  // v4.7+
  def damageScrewdriver(player: Player, stack: ItemStack): Unit = {}

  // Railcraft

  def canWhack(player: Player, stack: ItemStack, x: Int, y: Int, z: Int): Boolean = true

  def onWhack(player: Player, stack: ItemStack, x: Int, y: Int, z: Int): Unit = {}

  def canLink(player: Player, stack: ItemStack, cart: EntityMinecart): Boolean = false

  def onLink(player: Player, stack: ItemStack, cart: EntityMinecart): Unit = {}

  def canBoost(player: Player, stack: ItemStack, cart: EntityMinecart): Boolean = false

  def onBoost(player: Player, stack: ItemStack, cart: EntityMinecart): Unit = {}

  // IndustrialCraft 2

  def canBeStoredInToolbox(stack: ItemStack): Boolean = true
}
