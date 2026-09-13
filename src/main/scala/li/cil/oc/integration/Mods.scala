package li.cil.oc.integration

import li.cil.oc.Settings
import li.cil.oc.integration
import net.neoforged.fml.ModList

import scala.collection.mutable

/**
 * 已加载模组的探测与集成代理登记。
 *
 * 1.7.10 使用 `Loader` / `ModAPIManager` / `VersionParser`；
 * 1.21.1 统一改为 `ModList.get().isLoaded(id)`，不再做版本区间判断。
 * 原版针对各第三方模组的代理（`integration.<mod>.ModXxx`）已随本移植一并移除，
 * 只保留 OpenComputers 自身与 Vanilla 两个代理。
 */
object Mods {
  private val handlers = mutable.Set.empty[ModProxy]

  private val knownMods = mutable.ArrayBuffer.empty[ModBase]

  // ----------------------------------------------------------------------- //

  def All = knownMods.clone()

  val AgriCraft = new SimpleMod(IDs.AgriCraft)
  val AppliedEnergistics2 = new SimpleMod(IDs.AppliedEnergistics2)
  val BattleGear2 = new SimpleMod(IDs.BattleGear2)
  val BetterRecords = new SimpleMod(IDs.BetterRecords)
  val BloodMagic = new SimpleMod(IDs.BloodMagic)
  val BluePower = new SimpleMod(IDs.BluePower)
  val BuildCraft = new SimpleMod(IDs.BuildCraft)
  val BuildCraftLibrary = new SimpleMod(IDs.BuildCraftLibrary)
  val BuildCraftRecipes = new SimpleMod(IDs.BuildCraftRecipes)
  val BuildCraftTiles = new SimpleMod(IDs.BuildCraftTiles)
  val BuildCraftTools = new SimpleMod(IDs.BuildCraftTools)
  val BuildCraftTransport = new SimpleMod(IDs.BuildCraftTransport)
  val CoFHEnergy = new SimpleMod(IDs.CoFHEnergy)
  val CoFHItem = new SimpleMod(IDs.CoFHItem)
  val CoFHTileEntity = new SimpleMod(IDs.CoFHTileEntity)
  val CoFHTransport = new SimpleMod(IDs.CoFHTransport)
  val ColoredLights = new SimpleMod(IDs.ColoredLights)
  val ComputerCraft = new SimpleMod(IDs.ComputerCraft)
  val CraftingCosts = new SimpleMod(IDs.CraftingCosts)
  val DeepStorageUnit = new SimpleMod(IDs.DeepStorageUnit)
  val ElectricalAge = new SimpleMod(IDs.ElectricalAge)
  val EnderIO = new SimpleMod(IDs.EnderIO)
  val EnderStorage = new SimpleMod(IDs.EnderStorage)
  val ExtraCells = new SimpleMod(IDs.ExtraCells)
  val Factorization = new SimpleMod(IDs.Factorization)
  val Forestry = new SimpleMod(IDs.Forestry)
  val ForgeMultipart = new SimpleMod(IDs.ForgeMultipart)
  val Galacticraft = new SimpleMod(IDs.Galacticraft)
  val GregTech = new SimpleMod(IDs.GregTech)
  val IndustrialCraft2 = new SimpleMod(IDs.IndustrialCraft2)
  val IndustrialCraft2Classic = new SimpleMod(IDs.IndustrialCraft2Classic)
  val IngameWiki = new SimpleMod(IDs.IngameWiki)
  val Mekanism = new SimpleMod(IDs.Mekanism)
  val MekanismGas = new SimpleMod(IDs.MekanismGas)
  val Minecraft = new SimpleMod(IDs.Minecraft)
  val MineFactoryReloaded = new SimpleMod(IDs.MineFactoryReloaded)
  val Mystcraft = new SimpleMod(IDs.Mystcraft)
  val NotEnoughItems = new SimpleMod(IDs.NotEnoughItems)
  val NotEnoughKeys = new SimpleMod(IDs.NotEnoughKeys)
  val OpenComputers = new SimpleMod(IDs.OpenComputers)
  val PortalGun = new SimpleMod(IDs.PortalGun)
  val ProjectRedCore = new SimpleMod(IDs.ProjectRedCore)
  val ProjectRedTransmission = new SimpleMod(IDs.ProjectRedTransmission)
  val Railcraft = new SimpleMod(IDs.Railcraft)
  val RedLogic = new SimpleMod(IDs.RedLogic)
  val RotaryCraft = new SimpleMod(IDs.RotaryCraft)
  val StargateTech2 = new SimpleMod(IDs.StargateTech2)
  val Thaumcraft = new SimpleMod(IDs.Thaumcraft)
  val ThaumicEnergistics = new SimpleMod(IDs.ThaumicEnergistics)
  val ThermalExpansion = new SimpleMod(IDs.ThermalExpansion)
  val TinkersConstruct = new SimpleMod(IDs.TinkersConstruct)
  val TIS3D = new SimpleMod(IDs.TIS3D)
  val TMechWorks = new SimpleMod(IDs.TMechWorks)
  val VersionChecker = new SimpleMod(IDs.VersionChecker)
  val Waila = new SimpleMod(IDs.Waila)
  val WirelessRedstoneCBE = new SimpleMod(IDs.WirelessRedstoneCBE)
  val WirelessRedstoneSVE = new SimpleMod(IDs.WirelessRedstoneSV)

  // ----------------------------------------------------------------------- //

  val Proxies = Array(
    integration.vanilla.ModVanilla,

    // We go late to ensure all other mod integration is done.
    integration.opencomputers.ModOpenComputers
  )

  def init(): Unit = {
    for (proxy <- Proxies) {
      tryInit(proxy)
    }
  }

  private def tryInit(mod: ModProxy): Unit = {
    val isBlacklisted = Settings.get.modBlacklist.contains(mod.getMod.id)
    val alwaysEnabled = mod.getMod == null || mod.getMod == Mods.Minecraft
    if (!isBlacklisted && (alwaysEnabled || mod.getMod.isModAvailable) && handlers.add(mod)) {
      li.cil.oc.OpenComputers.log.debug(s"Initializing mod integration for '${mod.getMod.id}'.")
      try mod.initialize() catch {
        case e: Throwable =>
          li.cil.oc.OpenComputers.log.warn(s"Error initializing integration for '${mod.getMod.id}'", e)
      }
    }
  }

  // ----------------------------------------------------------------------- //

  object IDs {
    final val AgriCraft = "AgriCraft"
    final val AppliedEnergistics2 = "ae2"
    final val BattleGear2 = "battlegear2"
    final val BetterRecords = "betterrecords"
    final val BloodMagic = "bloodmagic"
    final val BluePower = "bluepower"
    final val BuildCraft = "buildcraftcore"
    final val BuildCraftLibrary = "buildcraftlib"
    final val BuildCraftRecipes = "buildcraftrecipes"
    final val BuildCraftTiles = "buildcrafttiles"
    final val BuildCraftTools = "buildcrafttools"
    final val BuildCraftTransport = "buildcrafttransport"
    final val CoFHEnergy = "cofhcore"
    final val CoFHItem = "cofhcore"
    final val CoFHTileEntity = "cofhcore"
    final val CoFHTransport = "cofhcore"
    final val ColoredLights = "easycoloredlights"
    final val ComputerCraft = "computercraft"
    final val CraftingCosts = "craftingcosts"
    final val ElectricalAge = "eln"
    final val EnderIO = "enderio"
    final val EnderStorage = "enderstorage"
    final val ExtraCells = "extracells"
    final val Factorization = "factorization"
    final val Forestry = "forestry"
    final val ForgeMultipart = "forgemultipartcbe"
    final val DeepStorageUnit = "deepstorageunit"
    final val Galacticraft = "galacticraftcore"
    final val GregTech = "gregtech"
    final val IndustrialCraft2 = "ic2"
    final val IndustrialCraft2Classic = "ic2-classic"
    final val IndustrialCraft2Spmod = "ic2-classic-spmod"
    final val IngameWiki = "igwmod"
    final val Mekanism = "mekanism"
    final val MekanismGas = "mekanism"
    final val Minecraft = "minecraft"
    final val MineFactoryReloaded = "minefactoryreloaded"
    final val Mystcraft = "mystcraft"
    final val NotEnoughItems = "jei"
    final val NotEnoughKeys = "notenoughkeys"
    final val OpenComputers = "open_computers_neo"
    final val PortalGun = "portalgun"
    final val ProjectRedCore = "projectred-core"
    final val ProjectRedTransmission = "projectred-transmission"
    final val Railcraft = "railcraft"
    final val RedLogic = "redlogic"
    final val RotaryCraft = "rotarycraft"
    final val StargateTech2 = "stargatetech2"
    final val Thaumcraft = "thaumcraft"
    final val ThaumicEnergistics = "thaumicenergistics"
    final val ThermalExpansion = "thermalexpansion"
    final val TinkersConstruct = "tconstruct"
    final val TIS3D = "tis3d"
    final val TMechWorks = "tmechworks"
    final val VersionChecker = "versionchecker"
    final val Waila = "waila"
    final val WirelessRedstoneCBE = "wrcbe"
    final val WirelessRedstoneSV = "wirelessredstone"
  }

  // ----------------------------------------------------------------------- //

  trait ModBase extends Mod {
    knownMods += this

    private var powerDisabled = false

    def isModAvailable: Boolean

    def id: String

    def isAvailable: Boolean = isModAvailable

    // This is called from the class transformer when injecting an interface of
    // this power type fails, to avoid class not found / class cast exceptions.
    def disablePower(): Unit = powerDisabled = true

    def version: Option[String] = {
      val info = ModList.get().getModContainerById(id)
      if (info.isPresent) Option(info.get().getModInfo.getVersion.toString) else None
    }
  }

  class SimpleMod(val id: String, version: String = "") extends ModBase {
    private lazy val isModAvailable_ = ModList.get().isLoaded(id)

    def isModAvailable: Boolean = isModAvailable_
  }

  class ClassBasedMod(val id: String, val classNames: String*) extends ModBase {
    private lazy val isModAvailable_ = classNames.forall(className => try Class.forName(className) != null catch {
      case _: Throwable => false
    })

    def isModAvailable: Boolean = isModAvailable_
  }

}
