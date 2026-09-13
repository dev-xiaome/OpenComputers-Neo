package li.cil.oc.common.block

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.client.KeyBindings
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.common.tileentity
import li.cil.oc.integration.util.NEI
import li.cil.oc.integration.util.Wrench
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.Rarity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.HitResult
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

import scala.reflect.ClassTag

class Microcontroller(protected implicit val tileTag: ClassTag[tileentity.Microcontroller]) extends RedstoneAware with traits.PowerAcceptor with traits.StateAware with traits.CustomDrops[tileentity.Microcontroller] {
  setCreativeTab(null)
  NEI.hide(this)

  override protected def customTextures = Array(
    Some("MicrocontrollerTop"),
    Some("MicrocontrollerTop"),
    Some("MicrocontrollerSide"),
    Some("MicrocontrollerFront"),
    Some("MicrocontrollerSide"),
    Some("MicrocontrollerSide")
  )

  // ----------------------------------------------------------------------- //

  override def getPickBlock(target: HitResult, world: Level, x: Int, y: Int, z: Int) =
    world.getTileEntity(x, y, z) match {
      case mcu: tileentity.Microcontroller => mcu.info.copyItemStack()
      case _ => null
    }

  // ----------------------------------------------------------------------- //

  override protected def tooltipTail(metadata: Int, stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipTail(metadata, stack, player, tooltip, advanced)
    if (KeyBindings.showExtendedTooltips) {
      val info = new MicrocontrollerData(stack)
      for (component <- info.components if component != null) {
        tooltip.add("- " + component.getDisplayName)
      }
    }
  }

  override def rarity(stack: ItemStack) = {
    val data = new MicrocontrollerData(stack)
    Rarity.byTier(data.tier)
  }

  // ----------------------------------------------------------------------- //

  override def energyThroughput = Settings.get.caseRate(Tier.One)

  override def createTileEntity(world: Level, metadata: Int) = new tileentity.Microcontroller()

  // ----------------------------------------------------------------------- //

  override def onBlockActivated(world: Level, x: Int, y: Int, z: Int, player: Player,
                                side: Direction, hitX: Float, hitY: Float, hitZ: Float) = {
    if (!Wrench.holdsApplicableWrench(player, BlockPosition(x, y, z))) {
      if (!player.isSneaking) {
        if (!world.isRemote) {
          world.getTileEntity(x, y, z) match {
            case mcu: tileentity.Microcontroller =>
              if (mcu.machine.isRunning) mcu.machine.stop()
              else mcu.machine.start()
            case _ =>
          }
        }
        true
      }
      else if (api.Items.get(player.getHeldItem) == api.Items.get(Constants.ItemName.EEPROM)) {
        if (!world.isRemote) {
          world.getTileEntity(x, y, z) match {
            case mcu: tileentity.Microcontroller =>
              val newEeprom = player.inventory.decrStackSize(player.inventory.currentItem, 1)
              mcu.changeEEPROM(newEeprom) match {
                case Some(oldEeprom) => InventoryUtils.addToPlayerInventory(oldEeprom, player)
                case _ =>
              }
          }
        }
        true
      }
      else false
    }
    else false
  }

  override protected def doCustomInit(tileEntity: tileentity.Microcontroller, player: LivingEntity, stack: ItemStack): Unit = {
    super.doCustomInit(tileEntity, player, stack)
    if (!tileEntity.world.isRemote) {
      tileEntity.info.load(stack)
      tileEntity.snooperNode.changeBuffer(tileEntity.info.storedEnergy - tileEntity.snooperNode.localBuffer)
    }
  }

  override protected def doCustomDrops(tileEntity: tileentity.Microcontroller, player: Player, willHarvest: Boolean): Unit = {
    super.doCustomDrops(tileEntity, player, willHarvest)
    tileEntity.saveComponents()
    tileEntity.info.storedEnergy = tileEntity.snooperNode.localBuffer.toInt
    dropBlockAsItem(tileEntity.world, tileEntity.x, tileEntity.y, tileEntity.z, tileEntity.info.createItemStack())
  }
}
