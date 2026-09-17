package li.cil.oc.client

import li.cil.oc.Settings
import net.minecraft.resources.ResourceLocation

/**
 * 客户端贴图位置总表。
 *
 * ==与 1.7.10 版的区别==
 *  - 原实现里除了一堆 `ResourceLocation` 还有大量 `IIcon` 字段（`iconOn` / `iconSideOn` …），
 *    由 `TextureStitchEvent` 在贴图注册时填充。NeoForge 1.21.1 已经**没有**
 *    `TextureStitchEvent`，`IIcon` 也整体被 `TextureAtlasSprite` 取代，
 *    因此这里只保留「位置」，取精灵统一走
 *    [[li.cil.oc.client.renderer.tileentity.RenderUtil.sprite]]。
 *  - 贴图目录按资源迁移规则改为 `textures/block`（原 `textures/blocks`）下的任意层级
 *    —— 见 `docs/PROGRESS.md` 的资源迁移记录；文件名一律小写。
 *  - 原 `init(TextureManager)` 负责把 GUI 贴图预绑定一遍（1.7.10 的 `bindTexture` 顺带
 *    把贴图加载进显存）。1.21.1 的 `TextureManager` 没有 `bindTexture`，
 *    贴图由 GPU 管线按需绑定，这个预热步骤**整体删除**。
 */
object Textures {
  private def gui(name: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/gui/" + name)

  private def font(name: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/font/" + name)

  private def block(name: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/block/" + name)

  /**
   * 模型贴图的**完整文件路径**（`textures/model/<name>.png`）。
   *
   * 注意这里的语义与 [[block]] 的下游用法不同：`Textures.Model` 下的三个常量只被
   * `RenderType#entityCutoutNoCull` 使用（独立绑定一张贴图，UV 用 0..1 的贴图内相对坐标，
   * 见 `client.renderer.item.UpgradeRenderer#drawSimpleBlock`），因此必须是带
   * `textures/` 前缀与 `.png` 后缀的完整路径。图集精灵查询走的是另一套路径规则
   * （见 [[li.cil.oc.client.renderer.tileentity.RenderUtil.sprite]] 的规范化）。
   */
  private def model(name: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/model/" + name + ".png")

  /**
   * 方块贴图的**完整文件路径**（`textures/block/<name>.png`，`<name>` 可含子目录）。
   *
   * 1.7.10 的 `bindTexture` 与 1.21.1 的 `RenderType#entityCutout*` /
   * `RackMountableRenderEvent#renderOverlay` 都需要这种「完整文件路径」，
   * 而方块图集查询需要的是相对 `textures/`、不带扩展名的「精灵路径」。
   * 两者的差别是实机里紫黑方格（`missingno`）的常见来源，因此这里显式分开命名：
   *  - 精灵查询：把 [[Block]] 下的常量交给 `RenderUtil.sprite`（内部会规范化）；
   *  - 独立贴图绑定：用本方法。
   */
  def blockFile(name: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/block/" + name + ".png")

  // ----------------------------------------------------------------------- //
  // 字体
  // ----------------------------------------------------------------------- //

  val fontAntiAliased: ResourceLocation = font("chars.png")
  val fontAliased: ResourceLocation = font("chars_aliased.png")

  // ----------------------------------------------------------------------- //
  // GUI
  // ----------------------------------------------------------------------- //

  val guiBackground: ResourceLocation = gui("background.png")
  val guiBar: ResourceLocation = gui("bar.png")
  val guiBorders: ResourceLocation = gui("borders.png")
  val guiButtonDriveMode: ResourceLocation = gui("button_drive_mode.png")
  val guiButtonPower: ResourceLocation = gui("button_power.png")
  val guiButtonRange: ResourceLocation = gui("button_range.png")
  val guiButtonRun: ResourceLocation = gui("button_run.png")
  val guiButtonScroll: ResourceLocation = gui("button_scroll.png")
  val guiButtonSide: ResourceLocation = gui("button_side.png")
  val guiButtonRelay: ResourceLocation = gui("button_switch.png")
  val guiComputer: ResourceLocation = gui("computer.png")
  val guiDatabase: ResourceLocation = gui("database.png")
  val guiDatabase1: ResourceLocation = gui("database1.png")
  val guiDatabase2: ResourceLocation = gui("database2.png")
  val guiDisassembler: ResourceLocation = gui("disassembler.png")
  val guiDrive: ResourceLocation = gui("drive.png")
  val guiDrone: ResourceLocation = gui("drone.png")
  val guiKeyboardMissing: ResourceLocation = gui("keyboard_missing.png")
  val guiManual: ResourceLocation = gui("manual.png")
  val guiManualHome: ResourceLocation = gui("manual_home.png")
  val guiManualMissingItem: ResourceLocation = gui("manual_missing_item.png")
  val guiManualTab: ResourceLocation = gui("manual_tab.png")
  val guiPrinter: ResourceLocation = gui("printer.png")
  val guiPrinterInk: ResourceLocation = gui("printer_ink.png")
  val guiPrinterMaterial: ResourceLocation = gui("printer_material.png")
  val guiPrinterProgress: ResourceLocation = gui("printer_progress.png")
  val guiRack: ResourceLocation = gui("rack.png")
  val guiRaid: ResourceLocation = gui("raid.png")
  val guiRange: ResourceLocation = gui("range.png")
  val guiRobot: ResourceLocation = gui("robot.png")
  val guiRobotNoScreen: ResourceLocation = gui("robot_noscreen.png")
  val guiRobotAssembler: ResourceLocation = gui("robot_assembler.png")
  val guiRobotSelection: ResourceLocation = gui("robot_selection.png")
  val guiServer: ResourceLocation = gui("server.png")
  val guiSlot: ResourceLocation = gui("slot.png")
  val guiUpgradeTab: ResourceLocation = gui("upgrade_tab.png")
  val guiWaypoint: ResourceLocation = gui("waypoint.png")

  val overlayNanomachines: ResourceLocation = gui("nanomachines_power.png")
  val overlayNanomachinesBar: ResourceLocation = gui("nanomachines_power_bar.png")

  // ----------------------------------------------------------------------- //
  // 方块 / 模型
  //
  // 下面这些原本是 `Textures.Xxx.iconYyy` 形式的 `IIcon` 字段（在方块实体渲染器里用），
  // 现在统一挪进 [[Block]] / [[Model]] 子对象，只保留 `ResourceLocation`。
  // ----------------------------------------------------------------------- //

  object Block {
    val AdapterOn: ResourceLocation = block("adapteron")
    val CableCap: ResourceLocation = block("cablecap")
    val ChargerFrontOn: ResourceLocation = block("chargerfronton")
    val ChargerSideOn: ResourceLocation = block("chargersideon")
    val DisassemblerSideOn: ResourceLocation = block("disassemblersideon")
    val DisassemblerTopOn: ResourceLocation = block("disassemblertopon")
    val GeolyzerTopOn: ResourceLocation = block("geolyzertopon")
    val PowerDistributorSideOn: ResourceLocation = block("powerdistributorsideon")
    val PowerDistributorTopOn: ResourceLocation = block("powerdistributortopon")
    val AssemblerSideAssembling: ResourceLocation = block("assemblersideassembling")
    val AssemblerSideOn: ResourceLocation = block("assemblersideon")
    val AssemblerTopOn: ResourceLocation = block("assemblertopon")
    val SwitchSideOn: ResourceLocation = block("switchsideon")
    val NetSplitterOn: ResourceLocation = block("netsplitteron")
    val TransposerOn: ResourceLocation = block("transposeron")

    val CaseFrontOn: ResourceLocation = block("casefronton")
    val CaseFrontError: ResourceLocation = block("casefronterror")
    val CaseFrontActivity: ResourceLocation = block("casefrontactivity")
    val DiskDriveFrontActivity: ResourceLocation = block("diskdrivefrontactivity")
    val DiskDriveMountableActivity: ResourceLocation = block("diskdrivemountableactivity")
    val HologramEffect: ResourceLocation = block("hologrameffect")
    val MicrocontrollerFrontLight: ResourceLocation = block("microcontrollerfrontlight")
    val MicrocontrollerFrontOn: ResourceLocation = block("microcontrollerfronton")
    val MicrocontrollerFrontError: ResourceLocation = block("microcontrollerfronterror")
    val RaidFrontError: ResourceLocation = block("raidfronterror")
    val RaidFrontActivity: ResourceLocation = block("raidfrontactivity")
    val Robot: ResourceLocation = block("robot")
    val ScreenUpIndicator: ResourceLocation = block("screen/up_indicator")

    val RackDiskDriveActivity: ResourceLocation = block("diskdrivemountableactivity")
    val RackServerOn: ResourceLocation = block("serverfronton")
    val RackServerError: ResourceLocation = block("serverfronterror")
    val RackServerActivity: ResourceLocation = block("serverfrontactivity")
    val RackServerNetworkActivity: ResourceLocation = block("serverfrontnetworkactivity")
    val RackTerminalServerOn: ResourceLocation = block("terminalserverfronton")
    val RackTerminalServerPresence: ResourceLocation = block("terminalserverfrontpresence")

    /** 机架槽位（六个方向）的图标，索引与 `Direction#ordinal` 对齐。 */
    val RackIcons: Array[ResourceLocation] = Array(
      block("rackfront"), // DOWN（未使用，占位）
      block("rackfront"), // UP（未使用，占位）
      block("rackfront"), // NORTH
      block("rackfront"), // SOUTH
      block("rackfront"), // WEST
      block("rackfront") // EAST
    )
    val RackDiskDrive: ResourceLocation = block("diskdrivemountable")
    val RackServer: ResourceLocation = block("serverfront")
    val RackTerminal: ResourceLocation = block("terminalserverfront")
  }

  object Model {
    val UpgradeCrafting: ResourceLocation = model("upgradecrafting")
    val UpgradeGenerator: ResourceLocation = model("upgradegenerator")
    val UpgradeInventory: ResourceLocation = model("upgradeinventory")
  }

  /** 悬浮靴子的光效贴图（原 `Textures.HoverBoots.lightOverlay`）。 */
  val hoverBootsLightOverlay: ResourceLocation = gui("nanomachines_power.png")
}
