package li.cil.oc.common

import li.cil.oc.common.init.OCItems
import li.cil.oc.{Constants, OpenComputers}
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

object Advancements {
  case class Definition(
                                 name: String,
                                 id: String,
                                 icon: String,
                                 parent: Option[String],
                                 crafting: Seq[String] = Seq.empty,
                                 assembling: Seq[String] = Seq.empty
                               ) {
    val location: ResourceLocation = ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, id)
  }

  val Definitions: Seq[Definition] = Seq(
    Definition("transistor", "transistor", Constants.ItemName.Transistor, None, crafting = Seq(Constants.ItemName.Transistor)),
    Definition("disassembler", "disassembler", Constants.BlockName.Disassembler, Some("transistor"), crafting = Seq(Constants.BlockName.Disassembler)),
    Definition("chip", "chip", Constants.ItemName.ChipTier1, Some("transistor"), crafting = Seq(Constants.ItemName.ChipTier1, Constants.ItemName.ChipTier2, Constants.ItemName.ChipTier3)),
    Definition("capacitor", "capacitor", Constants.BlockName.Capacitor, Some("chip"), crafting = Seq(Constants.BlockName.Capacitor)),
    Definition("assembler", "assembler", Constants.BlockName.Assembler, Some("capacitor"), crafting = Seq(Constants.BlockName.Assembler)),
    Definition("microcontroller", "microcontroller", Constants.BlockName.Microcontroller, Some("assembler"), assembling = Seq(Constants.BlockName.Microcontroller)),
    Definition("robot", "robot", Constants.BlockName.Robot, Some("assembler"), assembling = Seq(Constants.BlockName.Robot)),
    Definition("drone", "drone", Constants.ItemName.Drone, Some("assembler"), assembling = Seq(Constants.ItemName.Drone)),
    Definition("tablet", "tablet", Constants.ItemName.Tablet, Some("assembler"), assembling = Seq(Constants.ItemName.Tablet)),
    Definition("charger", "charger", Constants.BlockName.Charger, Some("capacitor"), crafting = Seq(Constants.BlockName.Charger)),
    Definition("cpu", "cpu", Constants.ItemName.CPUTier1, Some("chip"), crafting = Seq(Constants.ItemName.CPUTier1, Constants.ItemName.CPUTier2, Constants.ItemName.CPUTier3)),
    Definition("motionSensor", "motion_sensor", Constants.BlockName.MotionSensor, Some("cpu"), crafting = Seq(Constants.BlockName.MotionSensor)),
    Definition("geolyzer", "geolyzer", Constants.BlockName.Geolyzer, Some("cpu"), crafting = Seq(Constants.BlockName.Geolyzer)),
    Definition("redstoneIO", "redstone_io", Constants.BlockName.Redstone, Some("cpu"), crafting = Seq(Constants.BlockName.Redstone)),
    Definition("eeprom", "eeprom", Constants.ItemName.EEPROM, Some("chip"), crafting = Seq(Constants.ItemName.EEPROM)),
    Definition("ram", "ram", Constants.ItemName.RAMTier1, Some("chip"), crafting = Seq(Constants.ItemName.RAMTier1, Constants.ItemName.RAMTier2, Constants.ItemName.RAMTier3, Constants.ItemName.RAMTier4, Constants.ItemName.RAMTier5, Constants.ItemName.RAMTier6)),
    Definition("hdd", "hdd", Constants.ItemName.HDDTier1, Some("chip"), crafting = Seq(Constants.ItemName.HDDTier1, Constants.ItemName.HDDTier2, Constants.ItemName.HDDTier3)),
    Definition("case", "case", Constants.BlockName.CaseTier1, Some("chip"), crafting = Seq(Constants.BlockName.CaseTier1, Constants.BlockName.CaseTier2, Constants.BlockName.CaseTier3)),
    Definition("rack", "rack", Constants.BlockName.Rack, Some("case"), crafting = Seq(Constants.BlockName.Rack)),
    Definition("server", "server", Constants.ItemName.ServerTier1, Some("rack"), crafting = Seq(Constants.ItemName.ServerTier1, Constants.ItemName.ServerTier2, Constants.ItemName.ServerTier3)),
    Definition("screen", "screen", Constants.BlockName.ScreenTier1, Some("chip"), crafting = Seq(Constants.BlockName.ScreenTier1, Constants.BlockName.ScreenTier2, Constants.BlockName.ScreenTier3)),
    Definition("keyboard", "keyboard", Constants.BlockName.Keyboard, Some("screen"), crafting = Seq(Constants.BlockName.Keyboard)),
    Definition("hologram", "hologram", Constants.BlockName.HologramTier1, Some("screen"), crafting = Seq(Constants.BlockName.HologramTier1, Constants.BlockName.HologramTier2)),
    Definition("diskDrive", "disk_drive", Constants.BlockName.DiskDrive, Some("chip"), crafting = Seq(Constants.BlockName.DiskDrive)),
    Definition("floppy", "floppy", Constants.ItemName.Floppy, Some("diskDrive"), crafting = Seq(Constants.ItemName.Floppy)),
    Definition("openOS", "open_os", Constants.ItemName.Floppy, Some("floppy"), crafting = Seq(Constants.ItemName.OpenOS)),
    Definition("raid", "raid", Constants.BlockName.Raid, Some("diskDrive"), crafting = Seq(Constants.BlockName.Raid)),
    Definition("card", "card", Constants.ItemName.Card, None, crafting = Seq(Constants.ItemName.Card)),
    Definition("redstoneCard", "redstone_card", Constants.ItemName.RedstoneCardTier1, Some("card"), crafting = Seq(Constants.ItemName.RedstoneCardTier1, Constants.ItemName.RedstoneCardTier2)),
    Definition("graphicsCard", "graphics_card", Constants.ItemName.GraphicsCardTier1, Some("card"), crafting = Seq(Constants.ItemName.GraphicsCardTier1, Constants.ItemName.GraphicsCardTier2, Constants.ItemName.GraphicsCardTier3)),
    Definition("networkCard", "network_card", Constants.ItemName.NetworkCard, Some("card"), crafting = Seq(Constants.ItemName.NetworkCard)),
    Definition("wirelessNetworkCard", "wireless_network_card", Constants.ItemName.WirelessNetworkCardTier1, Some("networkCard"), crafting = Seq(Constants.ItemName.WirelessNetworkCardTier1, Constants.ItemName.WirelessNetworkCardTier2)),
    Definition("cable", "cable", Constants.BlockName.Cable, None, crafting = Seq(Constants.BlockName.Cable)),
    Definition("powerDistributor", "power_distributor", Constants.BlockName.PowerDistributor, Some("cable"), crafting = Seq(Constants.BlockName.PowerDistributor)),
    Definition("switch", "switch", Constants.BlockName.Relay, Some("cable"), crafting = Seq(Constants.BlockName.Relay)),
    Definition("adapter", "adapter", Constants.BlockName.Adapter, Some("cable"), crafting = Seq(Constants.BlockName.Adapter))
  )

  private val Crafting = Definitions.flatMap(definition => definition.crafting.map(_ -> definition.location)).toMap
  private val Assembling = Definitions.flatMap(definition => definition.assembling.map(_ -> definition.location)).toMap

  def getCraftingAdvancement(stack: ItemStack): ResourceLocation = {
    byRegisteredName(stack, Crafting).orElse(byMatchingStack(stack, _.crafting)).orNull
  }

  def getAssemblingAdvancement(stack: ItemStack): ResourceLocation = {
    byRegisteredName(stack, Assembling).orNull
  }

  private def byRegisteredName(stack: ItemStack, advancements: Map[String, ResourceLocation]): Option[ResourceLocation] = {
    Option(OCItems.get(stack)).flatMap(info => advancements.get(info.name))
  }

  private def byMatchingStack(stack: ItemStack, items: Definition => Seq[String]): Option[ResourceLocation] = {
    Definitions.find(definition => items(definition).exists(matches(stack))).map(_.location)
  }

  private def matches(stack: ItemStack)(name: String): Boolean = {
    Option(OCItems.get(name)).exists(info => ItemStack.matches(stack, info.createItemStack(1)))
  }
}