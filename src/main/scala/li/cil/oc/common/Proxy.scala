package li.cil.oc.common

import li.cil.oc.api
import li.cil.oc.common.init.Registry
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.{OpenComputers, Settings}
import net.minecraft.world.item.Item

import scala.reflect.ClassTag

/**
 * 初始化编排（对应 1.7.10 的 `common.Proxy`）。
 *
 * 1.21.1 迁移要点：
 *  - 没有 `@SidedProxy` / `FMLPreInitializationEvent` / `FMLInitializationEvent` /
 *    `FMLPostInitializationEvent` 了。NeoForge 的初始化是「`@Mod` 构造期 + 各个
 *    `FML*SetupEvent`」，因此这里改成三个显式方法，由主类
 *    [[li.cil.oc.OpenComputersNeo]] 在合适的时机调用：
 *    {{{
 *      // mod 构造期（注册表事件之前）：只做登记与 API 接线
 *      proxy.preInit()
 *      // 注册表冻结之后：FMLCommonSetupEvent#enqueueWork（此时才能安全地建物品堆叠）
 *      proxy.init()
 *      // 全部 mod 加载完成：FMLLoadCompleteEvent
 *      proxy.postInit()
 *    }}}
 *  - `OreDictionary` 已移除（改用物品/方块 tag，`c:` 命名空间），原 `preInit` 里那一整段
 *    矿辞注册整段删除；
 *  - `NetworkRegistry` / `EntityRegistry` / `GameRegistry` 的注册在 1.21.1 各有替代机制，
 *    分别由 [[li.cil.oc.common.PacketHandler]]、`EntityType` 的 `DeferredRegister`、
 *    [[Registry]] 负责，见下面对应位置的说明。
 *
 * 关于执行顺序的两个坑：
 *  1. [[preInit]] 会调用 [[Registry.Items.init]] / [[Registry.Blocks.init]]，
 *     **主类不要再直接调用它们**（会重复注册而报错）；`Registry.init(modBus)` 仍必须在
 *     mod 构造期调用一次（它只负责把各 `DeferredRegister` 挂到事件总线）。
 *  2. `Settings.get` 依赖主类在 `FMLCommonSetupEvent` 里调用的
 *     `OpenComputers.loadSettings(configFile)`。若 [[preInit]] 早于此执行，
 *     设置尚未加载，本类会跳过所有需要配置的接线（并留日志），不会抛异常。
 */
class Proxy {
  private var preInitialized = false

  private var initialized = false

  // ----------------------------------------------------------------------- //
  // preInit：注册 + API 接线
  // ----------------------------------------------------------------------- //

  def preInit(): Unit = {
    if (preInitialized) return
    preInitialized = true

    checkForBrokenJavaVersion()

    OpenComputers.log.debug("Initializing blocks and items.")

    // 1.7.10 的 `Blocks.init()` / `Items.init()`：
    // 1.21.1 里具体内容登记在 `Registry.Blocks.initBlocks()` / `Registry.Items.initItems()`，
    // OreDict 注册（`OreDictionary.registerOre(...)`）整段删除——改用 data 侧的物品 tag。
    Registry.Items.init()
    Registry.Blocks.init()

    OpenComputers.log.info("Initializing OpenComputers API.")

    // 原 `api.CreativeTab.instance = CreativeTab`：1.21.1 的标签页由
    // `li.cil.oc.api.CreativeTab.REGISTRY` 自己持有（主类调用 `CreativeTab.register(modBus)`），
    // 既不需要也无法再赋值（`instance` 现在是只读方法）。
    api.API.items = Registry
    api.API.fileSystem = li.cil.oc.server.fs.FileSystem

    // TODO(server.driver): 原为 `api.API.driver = driver.Registry`。`server/driver`（约 30 文件）
    //   尚未纳入 `scala_ported_packages`，common 层引用它会编译失败；该包编译通过后请在本行下面补：
    //   `api.API.driver = li.cil.oc.server.driver.Registry`
    // TODO(server.machine): 原为 `api.API.machine = machine.Machine`（`li.cil.oc.server.machine.Machine`）。
    //   同上，等 `server/machine`（含 Lua）进入编译集后补上。
    // TODO(common.nanomachines): 原为 `api.API.nanomachines = nanomachines.Nanomachines`。
    //   该包在编译集里但仍在施工（`common/nanomachines/Nanomachines.scala` 等尚未收敛），
    //   等其编译通过后补上。
    // TODO(server.network): 原为 `api.API.network = network.Network`（`li.cil.oc.server.network.Network`）。
    //   等该包进入编译集后补上；在此之前 `api.Network` 相关 API 会返回 null。
    // TODO(client.manual): 原为 `api.API.manual = Manual`（`li.cil.oc.client.Manual`，客户端专用）。
    //   随 `client` 包一起移植时接上。

    val settings = Settings.get
    if (settings != null) {
      api.API.config = settings.config
    }
    else {
      // 见类注释第 2 条：主类若在 FMLCommonSetupEvent 里才加载配置，这里是预期行为。
      OpenComputers.log.debug("Settings not loaded yet; API.config will stay unset until OpenComputers.loadSettings runs.")
    }

    // TODO(server.machine): Lua 架构注册。原为
    //   `LuaStateFactory`（原生 Lua，按 docs/PROGRESS.md 第 7 条暂不移植）+
    //   `api.Machine.add(classOf[LuaJLuaArchitecture])`
    //   + `api.Machine.LuaArchitecture = if (Settings.get.forceLuaJ) ... else api.Machine.architectures.head`。
    //   `li.cil.oc.server.machine.luaj.LuaJLuaArchitecture` 属于尚未移植的 `server/machine`，
    //   等它进入编译集后在这段注释的位置接上（LuaJ 依赖已经在 build.gradle 里接好）。
  }

  // ----------------------------------------------------------------------- //
  // init：注册表冻结之后
  // ----------------------------------------------------------------------- //

  def init(): Unit = {
    if (initialized) return
    initialized = true

    // 原为 `NetworkRegistry.INSTANCE.newEventDrivenChannel("OpenComputers")` +
    // `OpenComputers.channel.register(server.PacketHandler)`：1.21.1 已改由
    // `li.cil.oc.common.PacketHandler.initialize(modBus)`（主类在 mod 构造期调用）完成，
    // 这里不再需要做任何事。

    OpenComputers.log.debug("Initializing loot disks.")
    Loot.init()
    // TODO(loot): `Loot` 还需要挂到 NeoForge 事件总线上才能收到 `LevelEvent.Load`
    //   （用于读取存档目录 `opencomputers_neo/loot/loot.properties` 里的自定义战利品磁盘）：
    //   请在主类里加 `NeoForge.EVENT_BUS.register(Loot)`，或由 `common/EventHandler` 转发。
    //   `Loot.initForWorld` 本身已经可用。

    // TODO(common.Achievement): 原为 `Achievement.init()`。1.21.1 的成就系统改成了
    //   advancement 数据包，`common/Achievement.scala` 目前仍是 1.7.10 代码（1.21.1 里
    //   `Achievement`/`AchievementPage` 都不存在），需要整体改写或删除。

    // TODO(common.entity): 原为
    //   `EntityRegistry.registerModEntity(classOf[Drone], "Drone", 0, OpenComputers, 80, 1, true)`。
    //   1.21.1 改为用 `DeferredRegister` 注册 `EntityType`（`common/entity/Drone.scala` 仍在施工），
    //   接线位置在 `Registry`（新增一个 `entityTypes` 注册器）而不是这里。

    // TODO(integration): 原为 `Mods.init()`。`li.cil.oc.integration` 整体尚未移植
    //   （按 docs/PROGRESS.md 第 8 条只保留 `integration/opencomputers` 与 `integration/util`）。

    OpenComputers.log.debug("Initializing recipes.")
    // TODO(recipe): `Recipes.init()` 现在是**占位实现**（运行期解析 HOCON、调用
    //   `GameRegistry.addRecipe` 的那一整套已经删除）。等 `data/opencomputers_neo/recipe/` 下的
    //   json 配方由 `.recipes` 转换生成后，这一行可以直接删掉。
    Recipes.init()

    val settings = Settings.get
    if (settings != null) {
      api.API.isPowerEnabled = !settings.ignorePower
    }
    else {
      OpenComputers.log.warn("Settings were not loaded before Proxy.init(); power usage will stay disabled.")
    }
  }

  // ----------------------------------------------------------------------- //
  // postInit：全部 mod 加载完成
  // ----------------------------------------------------------------------- //

  def postInit(): Unit = {
    // Don't allow driver registration after this point, to avoid issues.
    // TODO(server.driver): 原为 `driver.Registry.locked = true`。等 `server/driver`
    //   进入编译集后，在这里补 `li.cil.oc.server.driver.Registry.locked = true`。
  }

  // ----------------------------------------------------------------------- //
  // 兼容辅助
  // ----------------------------------------------------------------------- //

  /**
   * 原 1.7.10 里「按矿辞是否存在，决定铁粒 / 钻石碎片是否出现在物品列表」的辅助方法。
   *
   * TODO(标签): 1.21.1 没有 `OreDictionary`（改用物品 tag，`c:` 命名空间），
   * 粒与芯片物品已经在 `Registry.Items.initItems()` 里**无条件注册**，
   * 因此这里不再需要任何逻辑。方法保留只是为了兼容调用方；
   * 若将来确实要按 tag 隐藏条目，请在 `data/opencomputers_neo/tags/` 里声明。
   */
  def tryRegisterNugget[TItem <: Delegate : ClassTag](nuggetItemName: String, nuggetOredictName: String, ingotItem: Item, ingotOredictName: String): Unit = {
    OpenComputers.log.debug(s"tryRegisterNugget('$nuggetItemName') is a no-op in 1.21.1; nugget items are registered unconditionally and OreDictionary is replaced by item tags.")
  }

  // 原 `missingMappings(e: FMLMissingMappingsEvent)` + `blockRenames` / `itemRenames` 整段删除：
  //  - NeoForge 21.1 不提供该事件（本移植版依赖的 neoforge-21.1.244-universal 里没有
  //    `MissingMappingsEvent` 之类的类型）；
  //  - 本移植版使用全新的 mod id `opencomputers_neo`，不存在需要重映射的旧存档。

  // OK, seriously now, I've gotten one too many bug reports because of this Java version being broken.

  private final val BrokenJavaVersions = Set("1.6.0_65, Apple Inc.")

  def isBrokenJavaVersion: Boolean = {
    val javaVersion = System.getProperty("java.version") + ", " + System.getProperty("java.vendor")
    BrokenJavaVersions.contains(javaVersion)
  }

  def checkForBrokenJavaVersion(): Unit = if (isBrokenJavaVersion) {
    OpenComputers.log.error("You're using a broken Java version! Please update now, or remove OpenComputers. DO NOT REPORT THIS! UPDATE YOUR JAVA!")
    throw new Exception("You're using a broken Java version! Please update now, or remove OpenComputers. DO NOT REPORT THIS! UPDATE YOUR JAVA!")
  }
}
