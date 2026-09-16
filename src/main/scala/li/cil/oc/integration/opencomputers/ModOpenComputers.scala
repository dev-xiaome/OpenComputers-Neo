package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.item.Chargeable
import li.cil.oc.api.internal
import li.cil.oc.api.internal.Wrench
import li.cil.oc.api.manual.PathProvider
import li.cil.oc.api.prefab.ItemStackTabIconRenderer
import li.cil.oc.api.prefab.ResourceContentProvider
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.RedstoneCard
import li.cil.oc.common.template._
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.integration.util.WirelessRedstone
import li.cil.oc.server.network.{Waypoints, WirelessNetwork}
import li.cil.oc.util.Color
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.{ChunkEvent, LevelEvent}

/**
 * OpenComputers 自身的驱动 / 转换器 / 模板注册代理。
 *
 * ==1.21.1 迁移要点==
 *  - 驱动一律通过 `li.cil.oc.api.Driver.add(...)` 登记（底层是
 *    `li.cil.oc.server.driver.Registry`）；
 *  - 事件注册统一走 `li.cil.oc.common.event.EventHandlers`（NeoForge 对 Scala `object` 上的
 *    注解扫描不可靠，工程统一改成 `addListener`）；`Waypoints` / `WirelessNetwork` 这两个
 *    还没有 `initialize()` 的对象在这里用 `addListener` 直接挂上；
 *  - `MinecraftForge.EVENT_BUS` → `net.neoforged.neoforge.common.NeoForge.EVENT_BUS`；
 *    `FMLCommonHandler.instance.bus`（mod 事件总线）在 1.21.1 通过
 *    `ModLoadingContext#getActiveContainer#getEventBus` 取得。
 *
 * ==降级清单==
 *  - **手册（Manual）的客户端资源**：`TextureImageProvider` / `ItemImageProvider` /
 *    `BlockImageProvider` / `OreDictImageProvider` / `Textures.guiManualHome`
 *    全部来自尚未移植的 `client/**`，这里只保留服务端可用的 PathProvider 与
 *    ResourceContentProvider，其余留 TODO。
 *  - **`common.asm.SimpleComponentTickHandler`**：ASM 注入层整体删除，随之一并移除。
 *  - **`ForgeChunkManager.setForcedChunkLoadingCallback`**：1.21.1 的区块强制加载改为
 *    `ServerLevel#setChunkForced` / ticket，由 `common.event.ChunkloaderUpgradeHandler`
 *    自己处理（它已由 `common.event.EventHandlers.initialize` 接线）。
 *  - **`Analyzer` / `Tablet` 的事件注册**：这两个对象上的监听器属于客户端展示逻辑
 *    （工具提示 / 实体进世界的客户端同步），等 `client/**` 移植后再接。
 */
object ModOpenComputers extends ModProxy {
  override def getMod = Mods.OpenComputers

  override def initialize(): Unit = {
    DroneTemplate.register()
    MicrocontrollerTemplate.register()
    NavigationUpgradeTemplate.register()
    RobotTemplate.register()
    ServerTemplate.register()
    TabletTemplate.register()
    TemplateBlacklist.register()

    api.IMC.registerWrenchTool("li.cil.oc.integration.opencomputers.ModOpenComputers.useWrench")
    api.IMC.registerWrenchToolCheck("li.cil.oc.integration.opencomputers.ModOpenComputers.isWrench")
    api.IMC.registerItemCharge(
      "OpenComputers",
      "li.cil.oc.integration.opencomputers.ModOpenComputers.canCharge",
      "li.cil.oc.integration.opencomputers.ModOpenComputers.charge")

    api.IMC.registerInkProvider("li.cil.oc.integration.opencomputers.ModOpenComputers.inkCartridgeInkProvider")
    api.IMC.registerInkProvider("li.cil.oc.integration.opencomputers.ModOpenComputers.dyeInkProvider")

    api.IMC.registerProgramDiskLabel("build", "builder", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("dig", "dig", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("base64", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("deflate", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("gpg", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("inflate", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("md5sum", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("sha256sum", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("refuel", "generator", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("irc", "irc", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("maze", "maze", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("arp", "network", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("ifconfig", "network", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("ping", "network", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("route", "network", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("opl-flash", "openloader", "Lua 5.2", "Lua 5.3", "LuaJ")
    api.IMC.registerProgramDiskLabel("oppm", "oppm", "Lua 5.2", "Lua 5.3", "LuaJ")

    // 事件接线：对应 1.7.10 的 `FMLCommonHandler.instance.bus.register(...)` 与
    // `MinecraftForge.EVENT_BUS.register(...)`。
    modBus.foreach(li.cil.oc.common.event.EventHandlers.initialize)
    // `Waypoints` / `WirelessNetwork` 还没有 `initialize()`，在这里显式挂上世界 / 区块钩子。
    NeoForge.EVENT_BUS.addListener((e: LevelEvent.Load) => Waypoints.onWorldLoad(e))
    NeoForge.EVENT_BUS.addListener((e: LevelEvent.Unload) => Waypoints.onWorldUnload(e))
    NeoForge.EVENT_BUS.addListener((e: ChunkEvent.Unload) => Waypoints.onChunkUnload(e))
    NeoForge.EVENT_BUS.addListener((e: LevelEvent.Load) => WirelessNetwork.onWorldLoad(e))
    NeoForge.EVENT_BUS.addListener((e: LevelEvent.Unload) => WirelessNetwork.onWorldUnload(e))
    NeoForge.EVENT_BUS.addListener((e: ChunkEvent.Unload) => WirelessNetwork.onChunkUnload(e))
    // TODO(port): 1.7.10 还会把 `Analyzer`（工具提示）与 `Tablet`（实体进世界）注册到事件总线，
    // 两者的监听器都属于客户端展示逻辑，等 `client/**` 移植后再接。
    // `Loot` 由主类（`OpenComputersNeo`）注册；`SaveHandler` / `EventHandler` /
    // `server.ComponentTracker` 等已包含在 `common.event.EventHandlers.initialize` 里。

    api.Driver.add(ConverterNanomachines)
    api.Driver.add(ConverterLinkedCard)

    api.Driver.add(DriverAPU)
    api.Driver.add(DriverComponentBus)
    api.Driver.add(DriverCPU)
    api.Driver.add(DriverDataCard)
    api.Driver.add(DriverDebugCard)
    api.Driver.add(DriverEEPROM)
    api.Driver.add(DriverFileSystem)
    api.Driver.add(DriverGraphicsCard)
    api.Driver.add(DriverInternetCard)
    api.Driver.add(DriverLinkedCard)
    api.Driver.add(DriverLootDisk)
    api.Driver.add(DriverMemory)
    api.Driver.add(DriverNetworkCard)
    api.Driver.add(DriverKeyboard)
    api.Driver.add(DriverRedstoneCard)
    api.Driver.add(DriverTablet)
    api.Driver.add(DriverWirelessNetworkCard)

    api.Driver.add(DriverContainerCard)
    api.Driver.add(DriverContainerFloppy)
    api.Driver.add(DriverContainerUpgrade)

    api.Driver.add(DriverGeolyzer)
    api.Driver.add(DriverMotionSensor)
    api.Driver.add(DriverScreen)
    api.Driver.add(DriverTransposer)

    api.Driver.add(DriverDiskDriveMountable)
    api.Driver.add(DriverServer)
    api.Driver.add(DriverTerminalServer)

    api.Driver.add(DriverUpgradeAngel)
    api.Driver.add(DriverUpgradeBarcodeReader)
    api.Driver.add(DriverUpgradeBattery)
    api.Driver.add(DriverUpgradeChunkloader)
    api.Driver.add(DriverUpgradeCrafting)
    api.Driver.add(DriverUpgradeDatabase)
    api.Driver.add(DriverUpgradeExperience)
    api.Driver.add(DriverUpgradeGenerator)
    api.Driver.add(DriverUpgradeHover)
    api.Driver.add(DriverUpgradeInventory)
    api.Driver.add(DriverUpgradeInventoryController)
    api.Driver.add(DriverUpgradeLeash)
    api.Driver.add(DriverUpgradeNavigation)
    api.Driver.add(DriverUpgradePiston)
    api.Driver.add(DriverUpgradeSign)
    api.Driver.add(DriverUpgradeSolarGenerator)
    api.Driver.add(DriverUpgradeTank)
    api.Driver.add(DriverUpgradeTankController)
    api.Driver.add(DriverUpgradeTractorBeam)
    api.Driver.add(DriverUpgradeTrading)
    api.Driver.add(DriverUpgradeMF)

    api.Driver.add(DriverAPU.Provider)
    api.Driver.add(DriverDataCard.Provider)
    api.Driver.add(DriverDebugCard.Provider)
    api.Driver.add(DriverEEPROM.Provider)
    api.Driver.add(DriverGraphicsCard.Provider)
    api.Driver.add(DriverInternetCard.Provider)
    api.Driver.add(DriverLinkedCard.Provider)
    api.Driver.add(DriverNetworkCard.Provider)
    api.Driver.add(DriverRedstoneCard.Provider)
    api.Driver.add(DriverWirelessNetworkCard.Provider)

    api.Driver.add(DriverGeolyzer.Provider)
    api.Driver.add(DriverMotionSensor.Provider)
    api.Driver.add(DriverScreen.Provider)
    api.Driver.add(DriverTransposer.Provider)

    api.Driver.add(DriverUpgradeChunkloader.Provider)
    api.Driver.add(DriverUpgradeCrafting.Provider)
    api.Driver.add(DriverUpgradeDatabase.Provider)
    api.Driver.add(DriverUpgradeExperience.Provider)
    api.Driver.add(DriverUpgradeGenerator.Provider)
    api.Driver.add(DriverUpgradeInventoryController.Provider)
    api.Driver.add(DriverUpgradeLeash.Provider)
    api.Driver.add(DriverUpgradeNavigation.Provider)
    api.Driver.add(DriverUpgradePiston.Provider)
    api.Driver.add(DriverUpgradeSign.Provider)
    api.Driver.add(DriverUpgradeTankController.Provider)
    api.Driver.add(DriverUpgradeTractorBeam.Provider)
    api.Driver.add(DriverUpgradeMF.Provider)

    api.Driver.add(EnvironmentProviderBlocks)

    api.Driver.add(InventoryProviderDatabase)
    api.Driver.add(InventoryProviderServer)

    blacklistHost(classOf[internal.Adapter],
      Constants.BlockName.Geolyzer,
      Constants.BlockName.MotionSensor,
      Constants.BlockName.Keyboard,
      Constants.BlockName.ScreenTier1,
      Constants.BlockName.Transposer,
      Constants.BlockName.CarpetedCapacitor,
      Constants.ItemName.Analyzer,
      Constants.ItemName.AngelUpgrade,
      Constants.ItemName.BatteryUpgradeTier1,
      Constants.ItemName.BatteryUpgradeTier2,
      Constants.ItemName.BatteryUpgradeTier3,
      Constants.ItemName.ChunkloaderUpgrade,
      Constants.ItemName.CraftingUpgrade,
      Constants.ItemName.ExperienceUpgrade,
      Constants.ItemName.GeneratorUpgrade,
      Constants.ItemName.HoverUpgradeTier1,
      Constants.ItemName.HoverUpgradeTier2,
      Constants.ItemName.InventoryUpgrade,
      Constants.ItemName.NavigationUpgrade,
      Constants.ItemName.PistonUpgrade,
      Constants.ItemName.SolarGeneratorUpgrade,
      Constants.ItemName.TankUpgrade,
      Constants.ItemName.TractorBeamUpgrade,
      Constants.ItemName.LeashUpgrade,
      Constants.ItemName.TradingUpgrade)
    blacklistHost(classOf[internal.Drone],
      Constants.BlockName.Keyboard,
      Constants.BlockName.ScreenTier1,
      Constants.BlockName.Transposer,
      Constants.BlockName.CarpetedCapacitor,
      Constants.ItemName.Analyzer,
      Constants.ItemName.APUTier1,
      Constants.ItemName.APUTier2,
      Constants.ItemName.GraphicsCardTier1,
      Constants.ItemName.GraphicsCardTier2,
      Constants.ItemName.GraphicsCardTier3,
      Constants.ItemName.NetworkCard,
      Constants.ItemName.RedstoneCardTier1,
      Constants.ItemName.AngelUpgrade,
      Constants.ItemName.CraftingUpgrade,
      Constants.ItemName.HoverUpgradeTier1,
      Constants.ItemName.HoverUpgradeTier2)
    blacklistHost(classOf[internal.Microcontroller],
      Constants.BlockName.Keyboard,
      Constants.BlockName.ScreenTier1,
      Constants.BlockName.CarpetedCapacitor,
      Constants.ItemName.Analyzer,
      Constants.ItemName.APUTier1,
      Constants.ItemName.APUTier2,
      Constants.ItemName.GraphicsCardTier1,
      Constants.ItemName.GraphicsCardTier2,
      Constants.ItemName.GraphicsCardTier3,
      Constants.ItemName.AngelUpgrade,
      Constants.ItemName.CraftingUpgrade,
      Constants.ItemName.DatabaseUpgradeTier1,
      Constants.ItemName.DatabaseUpgradeTier2,
      Constants.ItemName.DatabaseUpgradeTier3,
      Constants.ItemName.ExperienceUpgrade,
      Constants.ItemName.GeneratorUpgrade,
      Constants.ItemName.HoverUpgradeTier1,
      Constants.ItemName.HoverUpgradeTier2,
      Constants.ItemName.InventoryUpgrade,
      Constants.ItemName.InventoryControllerUpgrade,
      Constants.ItemName.NavigationUpgrade,
      Constants.ItemName.TankUpgrade,
      Constants.ItemName.TankControllerUpgrade,
      Constants.ItemName.TractorBeamUpgrade,
      Constants.ItemName.LeashUpgrade,
      Constants.ItemName.TradingUpgrade)
    blacklistHost(classOf[internal.Robot],
      Constants.BlockName.Transposer,
      Constants.BlockName.CarpetedCapacitor,
      Constants.ItemName.Analyzer,
      Constants.ItemName.LeashUpgrade)
    blacklistHost(classOf[internal.Tablet],
      Constants.BlockName.ScreenTier1,
      Constants.BlockName.Transposer,
      Constants.BlockName.CarpetedCapacitor,
      Constants.ItemName.NetworkCard,
      Constants.ItemName.RedstoneCardTier1,
      Constants.ItemName.AngelUpgrade,
      Constants.ItemName.ChunkloaderUpgrade,
      Constants.ItemName.CraftingUpgrade,
      Constants.ItemName.DatabaseUpgradeTier1,
      Constants.ItemName.DatabaseUpgradeTier2,
      Constants.ItemName.DatabaseUpgradeTier3,
      Constants.ItemName.ExperienceUpgrade,
      Constants.ItemName.GeneratorUpgrade,
      Constants.ItemName.HoverUpgradeTier1,
      Constants.ItemName.HoverUpgradeTier2,
      Constants.ItemName.InventoryUpgrade,
      Constants.ItemName.InventoryControllerUpgrade,
      Constants.ItemName.TankUpgrade,
      Constants.ItemName.TankControllerUpgrade,
      Constants.ItemName.LeashUpgrade,
      Constants.ItemName.TradingUpgrade)

    if (!WirelessRedstone.isAvailable) {
      blacklistHost(classOf[internal.Drone], Constants.ItemName.RedstoneCardTier2)
      blacklistHost(classOf[internal.Tablet], Constants.ItemName.RedstoneCardTier2)
    }

    // Note: kinda nasty, but we have to check for availability for extended
    // redstone mods after integration init, so we have to set tier two
    // redstone card availability here, after all other mods were inited.
    if (BundledRedstone.isAvailable || WirelessRedstone.isAvailable) {
      OpenComputers.log.info("Found extended redstone mods, enabling tier two redstone card.")
      Delegator.subItem(api.Items.get(Constants.ItemName.RedstoneCardTier2).createItemStack(1)) match {
        case Some(redstone: RedstoneCard) => redstone.showInItemList = true
        case _ =>
      }
    }

    api.Manual.addProvider(DefinitionPathProvider)
    api.Manual.addProvider(new ResourceContentProvider(Settings.resourceDomain, "doc/"))
    // TODO(port): 1.7.10 还注册了四个**客户端**图片提供器
    //   （`TextureImageProvider` / `ItemImageProvider` / `BlockImageProvider` /
    //   `OreDictImageProvider`）与一个用 `Textures.guiManualHome` 的标签页图标。
    //   它们都在尚未移植的 `client.renderer.markdown.segment.render` / `client.Textures` 里，
    //   等 `client/**` 完成后补回。

    api.Manual.addTab(new ItemStackTabIconRenderer(api.Items.get("case1").createItemStack(1)), "oc:gui.Manual.Blocks", "%LANGUAGE%/block/index.md")
    api.Manual.addTab(new ItemStackTabIconRenderer(api.Items.get("cpu1").createItemStack(1)), "oc:gui.Manual.Items", "%LANGUAGE%/item/index.md")

    api.Nanomachines.addProvider(DisintegrationProvider)
    api.Nanomachines.addProvider(HungryProvider)
    api.Nanomachines.addProvider(ParticleProvider)
    api.Nanomachines.addProvider(PotionProvider)
    api.Nanomachines.addProvider(MagnetProvider)
  }

  /**
   * mod 事件总线（1.7.10 的 `FMLCommonHandler.instance.bus`）。
   *
   * `ModProxy#initialize()` 没有参数，所以这里从 `ModLoadingContext` 反查当前容器；
   * 取不到时返回 `None`（只跳过需要 mod 总线的那部分接线，不会崩）。
   *
   * TODO(port): 更干净的做法是给 `ModProxy#initialize` 加一个 `IEventBus` 参数，
   * 由 `server.Proxy.init` 把总线传下来；在此之前先用这里反查。
   */
  private def modBus: Option[net.neoforged.bus.api.IEventBus] = try {
    Option(net.neoforged.fml.ModLoadingContext.get().getActiveContainer).map(_.getEventBus)
  }
  catch {
    case _: Throwable => None
  }

  def useWrench(player: Player, x: Int, y: Int, z: Int, changeDurability: Boolean): Boolean = {
    // 1.21.1：`getHeldItem` → `getMainHandItem`；`getEntityWorld` → `level()`。
    player.getMainHandItem.getItem match {
      case wrench: Wrench => wrench.useWrenchOnBlock(player, player.level(), x, y, z, !changeDurability)
      case _ => false
    }
  }

  def isWrench(stack: ItemStack): Boolean = stack.getItem.isInstanceOf[Wrench]

  def canCharge(stack: ItemStack): Boolean = stack.getItem match {
    case chargeable: Chargeable => chargeable.canCharge(stack)
    case _ => false
  }

  def charge(stack: ItemStack, amount: Double, simulate: Boolean): Double = {
    stack.getItem match {
      case chargeable: Chargeable => chargeable.charge(stack, amount, simulate)
      case _ => 0.0
    }
  }

  def inkCartridgeInkProvider(stack: ItemStack): Int = {
    if (api.Items.get(stack) == api.Items.get(Constants.ItemName.InkCartridge))
      Settings.get.printInkValue
    else
      0
  }

  def dyeInkProvider(stack: ItemStack): Int = {
    if (Color.isDye(stack))
      Settings.get.printInkValue / 10
    else
      0
  }

  private def blacklistHost(host: Class[_], itemNames: String*): Unit = {
    for (itemName <- itemNames) {
      api.IMC.blacklistHost(itemName, host, api.Items.get(itemName).createItemStack(1))
    }
  }

  object DefinitionPathProvider extends PathProvider {
    private final val Blacklist = Set(
      Constants.ItemName.Debugger,
      Constants.ItemName.DiamondChip,
      Constants.BlockName.Endstone,
      Constants.ItemName.IronNugget
    )

    override def pathFor(stack: ItemStack): String = Option(api.Items.get(stack)) match {
      case Some(definition) => checkBlacklisted(definition)
      case _ => null
    }

    override def pathFor(world: Level, x: Int, y: Int, z: Int): String =
      // 1.21.1：`world.getBlock(x, y, z)` 改成方块状态取方块；`api.Items.get(block)` 也没有了，
      // 改为先用方块反查物品，再通过物品堆叠查描述符。
      world.getBlockState(new BlockPos(x, y, z)).getBlock match {
        case block: li.cil.oc.common.block.SimpleBlock =>
          val item = block.asItem()
          if (item == null || item == net.minecraft.world.item.Items.AIR) null
          else checkBlacklisted(api.Items.get(new ItemStack(item)))
        case _ => null
      }

    private def checkBlacklisted(info: api.detail.ItemInfo): String =
      if (info == null || Blacklist.contains(info.name)) null
      else if (info.block != null) "%LANGUAGE%/block/" + info.name + ".md"
      else "%LANGUAGE%/item/" + info.name + ".md"
  }

}
