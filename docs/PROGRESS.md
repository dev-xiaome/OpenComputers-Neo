# 移植进度

> **GUI 链路已打通（本仓库的最新进展，优先看这一段）**
>
> 1.21.1 打开容器界面需要**四段**配合，现在四段都已落地：
>
> 1. **服务端开界面**：`common/block/traits/GUI.scala` → `server.GuiHandler.openGui`
>    → `common.GuiHandler.openGui` → `common/container/MenuOpening`。
>    `MenuOpening` 自己实现了 `MenuProvider`（原版 `SimpleMenuProvider` 是 `final` 且不写自定义数据），
>    在 `writeClientSideData` 里把「宿主是什么」写进载荷。
> 2. **载荷格式**：`common/container/MenuHostPayload.scala`（新增）。四种宿主：
>    `Block`（方块坐标）/ `Entity`（实体 id）/ `RackSlot`（机架坐标 + 槽位号）/ `ItemInHand`（物品堆叠）。
>    写与读严格配对放在同一个文件里，改一边必须同步另一边。
> 3. **客户端重建容器**：`common/container/MenuTypes.scala`。16 个 `MenuType` 的工厂按载荷
>    取回宿主（`level.getBlockEntity(pos)` / `level.getEntity(id)` / `rack.getMountable(slot)` /
>    载荷里的物品堆叠），再 new 出容器。**同一个 `MenuType` 会被多种宿主共用**
>    （例如 `diskdrive` 同时服务方块形态与机架插槽形态），工厂按**宿主实际类型**分派。
> 4. **菜单 → 屏幕**：`client/GuiHandler.registerScreens`（`RegisterMenuScreensEvent`，mod 总线），
>    由 `client/Proxy.initialize(modBus)` 挂上，再由 `client/ClientSetup.initialize(modBus)` 转发。
>
> **主类 `OpenComputersNeo.scala` 还需要加这一行**（放在已有的
> `if (FMLEnvironment.dist.isClient)` 块里、`ColorHandlers.initialize(modBus)` 后面）：
>
> ```scala
> // 客户端接线总入口：菜单→屏幕（RegisterMenuScreensEvent）、方块实体/实体渲染器、
> // 物品渲染扩展、键位、客户端 setup。内部是幂等的。
> li.cil.oc.client.ClientSetup.initialize(modBus)
> ```
>
> **已完成的屏幕（16 个菜单全部有屏幕）**：`Case` / `Adapter` / `Charger` / `DiskDrive` /
> `Raid` / `Database` / `Switch` / `Disassembler` / `Relay` / `Printer` / `Assembler` /
> `Rack` / `Robot` / `Drone` / `Server` / `Tablet`。
>
> **已知降级（都留有 `TODO(...)` 注释）**：
> - `Tablet` 容器的平板内部物品栏用 `common/container/EmptyItemHandler` 兜底
>   （`TabletCaseInventory` 尚未移植）→ 界面能开、槽位是空的；
> - `Robot` / `Drone` 界面里「机器人自带屏幕 / 无人机状态屏」的**文本内容**依赖
>   文本缓冲区渲染子系统，目前只画深色底 + 一行状态文字；
> - 容器的**自定义数据同步**（`Player.customDataSync`）仍是空操作，等 `server.PacketSender`
>   移植后接上；受影响的是各界面上的进度条 / 中继速率 / 服务器运行状态等**显示**，不影响逻辑；
> - `client/gui/Screen.scala`（终端屏幕）、`Drive.scala`、`Waypoint.scala`、`Manual.scala`
>   与 `gui/traits/{DisplayBuffer,InputBuffer,Window}.scala` **尚未移植**，因此
>   `scala_ported_packages` 里**不要**写 `li/cil/oc/client/gui/**` 通配，要用逐文件白名单。
>
> **编译集注意**：`Scala 2.13.14` 里**没有** `scala.jdk.CollectionConverters.asJavaCollection`
> （只有 `asJava` 那一组隐式），搬运代码里遇到它请改成 `CustomGuiContainer.toJava(...)`。
>
> ---
>
> **当前状态（工作区干净，`gradlew compileScala` 绿色）**
>
> **已可运行**：`gradlew runClient` 能通过注册阶段、进入世界（日志有 `Greetings, user! Booting OpenComputers Neo.`
> → `Loaded settings` → `Sound engine started`）。启动期已修掉 6 个崩溃点：
> 1. 注册期读 `Settings`（配置已前置到 `OpenComputersNeo` 构造期）
> 2. 创造模式标签页重复 `accept` 同一堆叠（`Registry.addCreativeTabEntries` 已去重）
> 3. `CPULike.tooltipExtended` 向 `Tooltip.get` 传 null → NPE
> 4. `HardDiskDrive.displayName` 无限递归 → StackOverflow
> 5. `APUCreative` 等级传成 `Tier.Four` → 数组越界（已改回 `Tier.Three`）
> 6. Scala trait 读 `BlockEntity.worldPosition`（protected）→ `IllegalAccessError`（改用 `getBlockPos`）
>
> 另已修本地化键：`SimpleItem#getDescriptionId` → `"item.oc." + name + ".name"`，
> `SimpleBlock#getDescriptionId` → `"tile.oc." + name + ".name"`（**不改资源文件**，直接复用 1.7.10 风格的 lang json 键）。
>
> **并行施工中（子代理因额度中断，需要重派）**：
> - `server/component/**`（约 50 文件：CPU/GPU/内存/硬盘/网卡/升级/机器人/平板）
> - `server/{Proxy,PacketHandler,PacketSender,ComponentTracker,GuiHandler,PetVisibility,agent/**,command/**}` + `integration/**`
>   （注意 `integration/vanilla/**` 项目里还没有，需从 `mod_src\...\src\main\scala\li\cil\oc\integration\vanilla\` 复制；
>    且 `src/main/scala` 下的 `.java` **不会被编译**，要放到 `src/main/java` 下）
> - `client/renderer/**`（渲染器；`client/renderer/font/FontParserHex.java` 目前被 build.gradle 排除，依赖 gnu.trove）
>
> **下一步**：重派上述代理 → 完成后把对应包加进 `scala_ported_packages` → `gradlew build` 转绿 →
> `gradlew runClient` 验证「创造模式标签页可见 + 放下方块 + 右键打开机箱界面」。

> 恢复步骤：读本文件 → `git log --oneline` → 把已完成包补进 `gradle.properties` 的 `scala_ported_packages` →
> `.\gradlew.bat build --console=plain` 清错 → `.\tools\normalize-assets.ps1` 兜底资源大小写 → `.\gradlew.bat runClient` 验证。

> 目标：OpenComputers 1.8.10（MC 1.7.10 / Forge，Scala 2.11 + Java）
> → OpenComputers Neo（MC 1.21.1 / NeoForge 21.1.244，Scala 2.13.14 + Java 21）

## 已完成

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 项目骨架 | ✅ | `li.cil.oc.OpenComputersNeo`（Scala class + `@Mod`）、创造模式标签页、typesafe config 接入 |
| 构建工具链 | ✅ | 打通 Scala 2.13 + ModDevGradle 联合编译（`compileScala` 依赖 `compileJava`，Java 不得引用 Scala） |
| Gradle 分发 | ✅ | 改用 aliyun 镜像（`services.gradle.org` 在本机不可达） |
| 资源迁移 | ✅ | `assets/opencomputers` → `assets/opencomputers_neo`；11 个 `.lang` → `.json`；`textures/blocks|items` → `block|item` |
| 批量重写脚本 | ✅ | `tools/port-rewrite.ps1`（import / 符号 / NBT 方法名）、`tools/scala213-migrate.ps1`、`tools/lang-to-json.ps1`、`tools/errors.ps1` |
| 按包增量编译 | ✅ | `gradle.properties` 的 `scala_ported_packages` 控制编译范围，保证工程任何时刻可编译可启动 |
| `li.cil.oc.api` | ✅ | Java API 层全部编译通过（0 错误） |
| `li.cil.oc.util` | ✅ | Scala 工具层全部编译通过（0 错误） |
| 游戏内加载 | ✅ | `runClient` 可正常进入游戏；日志确认主类与配置初始化完成，生成 `config/opencomputers_neo.conf` |
| 创造模式标签页 | ⚠️ | 已注册但**空**，游戏会隐藏空标签页 → 阶段 2 注册物品后才会显示 |
| `li.cil.oc.common` | 🔄 | 阶段 2 进行中：注册层 ✅、网络传输层 ✅、`common/item/traits`+`data` ✅；`common/item` 顶层 / `common/block` / `common/tileentity` 待做 |
| 物品 NBT 方案 | ✅ | 自定义数据组件 `opencomputers_neo:nbt`（`li.cil.oc.common.DataComponents`）+ `li.cil.oc.util.ItemNBT` + Scala 隐式类 |
| 方块/物品染色 | ✅ | `li.cil.oc.client.ColorHandlers`（`RegisterColorHandlersEvent.Block/Item`，mod 总线）+ 模型继承 `block/tinted_cube`（`tintindex: 0`）：机箱 1-3 级/创造、屏幕 1-3 级、线缆、变色石 |
| 屏幕/机箱静态模型 | ✅ | `models/block/screen1|2|3.json` 由「空模型」补成可用方盒（`screen/f2` + `screen/b2` + `screen/b`）；`case*` 改为继承 `block/tinted_cube` 以便上色 |
| 模型引用校验脚本 | ✅ | `tools/check-models.ps1`：扫描 models + blockstates 的 `opencomputers_neo:` 引用（textures / parent / model）并沿 parent 链解析 `#变量`；当前 460 个引用 0 缺失 |
| `tools/scalac-check.ps1` | ✅ | 增加 glob 未匹配告警（避免 glob 写错时静默漏编译） |

## 关键设计决策

1. **语言**：实现层保留 Scala 2.13（原 2.11），API 层保留 Java。
   理由：60k 行 Scala 逻辑可原样搬运后按编译错误逐点修正，比整体改写为 Java 成本低得多。
2. **Java 不引用 Scala**：`compileJava` 先于 `compileScala`。需要双向调用时通过 Java 侧定义接口/常量解决。
3. **主类用 Scala class**：NeoForge 需要可反射实例化的 mod 类，Scala `object` 不行。
4. **物品 NBT**：1.21.1 `ItemStack` 无 tag，改用自定义 `DataComponentType<CompoundTag>`，
   并用 Scala 隐式类补回 `getTag()/setTag()/hasTag()`，从而让原代码基本无需改动。
5. **ASM coremod 全部移除**：`li.cil.oc.common.asm` + `TransformerLoader` 删除，改用 NeoForge 原生机制。
6. **`@SideOnly` 一律移除**：NeoForge 的 `RuntimeDistCleaner` 对类级 `@OnlyIn` 会直接抛异常，
   客户端专用方法改为 Javadoc 标注。
7. **Lua 架构**：已接入上游 `org.luaj:luaj-jse:3.0.1`，全局把 `li.cil.repack.org.luaj` 替换为 `org.luaj`。
   原生 Lua（JNLua / `server/machine/luac`）暂不移植，`util/ExtendedLuaState.scala` 暂时排除编译。
8. **其它模组集成**：仅保留 `integration/opencomputers`（OC 自身驱动）与 `integration/util`，
   第三方模组代理（AE2/IC2/BC/CC 等）整体移除，`Mods.scala` 改为 `ModList` 探测。
9. **运行时依赖可见性（关键坑）**：ModDevGradle 会把依赖放到 JVM 的 *boot module path* 上，
   FML 的类加载器读不到（`NoClassDefFoundError: scala.collection.immutable.List`）。
   解决方式：`compileOnly` + `jarJar`（发布用）+ `build.gradle` 里给所有 `write*LegacyClasspath`
   任务追加、并给 `runClient/runServer/runData/runGameTestServer` 的 VM 参数追加 `-cp <三个库>`（开发用）。
10. **不要再用 `com.typesafe.config.impl` 自定义类**：会与 `typesafe.config` 模块形成 JPMS 拆分包，
   导致 `ModuleLayerHandler` 解析失败。原 `OpenComputersConfigCommentManipulationHook` 已删除。
11. **1.7.10 的 damage 子类型 → 1.21.1 独立物品/方块**：`Delegator`/`Delegate` 派发机制不再需要。

## 待办（按顺序）

1. `li.cil.oc.common` 阶段 2（进行中）
   - ✅ `common/Tier.scala`、`common/GuiType.scala`、`common/Slot.scala`、`common/Sound.scala` + `common/SoundEvents.java`
   - ✅ 注册层 `common/init/Registry.scala`（`DeferredRegister` 封装 + `BuildCreativeModeTabContentsEvent` 填充标签页 +
     `api.Items` 接线；`Items.initItems()` / `Blocks.initBlocks()` 留了注册点）
   - ✅ 网络传输层：`common/network/{OpenComputersPayload,NetworkDispatcher,OpenComputersNetwork}.java` +
     `common/PacketBuilder.scala`、`common/PacketHandler.scala`（`PacketType.scala` 原样保留）
     → 主类需调用 `li.cil.oc.common.PacketHandler.initialize(modBus)`
   - ✅ `common/item/traits/**`（7 文件）、`common/item/data/**`（13 文件）
   - 🔄 `common/item/*.scala` 顶层（约 90 文件）：改为「每个 `Constants.ItemName.*` 一个独立 `Item`」，并在 `Items.initItems()` 注册 —— 代理施工中
   - ⬜ `common/block/**`（40 文件）：`BlockBehaviour.Properties` + `BlockState` 属性 + `VoxelShape`，`ItemBlock` → `BlockItem`
   - ⬜ `common/tileentity/**`（62 文件）：`BlockEntity(BlockEntityType, BlockPos, BlockState)` + `EntityBlock#getTicker`
   - ⬜ `common/template`、`common/inventory`、`common/container`、`common/recipe`、`common/event`、`common/component`
2. `li.cil.oc.server`：`fs` → `machine`（含 Lua）→ `component`
3. `li.cil.oc.client`：方块实体渲染器、GUI、键位
4. jarJar 打包运行时依赖（scala-library、typesafe config、luaj）—— 已在 `build.gradle` 配好，发布前需验证产物

## 常用命令

```powershell
# 编译（注意：同一时间只能有一个 gradle 进程）
.\gradlew.bat build --console=plain
# 只编译 Scala（会先编译 Java）
.\gradlew.bat compileScala --console=plain
# 启动客户端 / 服务端
.\gradlew.bat runClient
.\gradlew.bat runServer
# 批量重写新搬过来的源码
.\tools\port-rewrite.ps1 -Root "src\main\scala"
.\tools\scala213-migrate.ps1 -Root "src\main\scala"
```

---

## ⚠️ 重大方法论陷阱：typer 错误会**完全掩盖** refchecks 错误

`scalac` 分阶段执行（parser → namer → **typer** → **refchecks** → …），
**只要某个阶段报了错，后续阶段就直接跳过**。后果：

- 编译集里只要存在**任意一条**解析错误或 typer 错误，日志里就只剩那一两条，看起来"非常干净"；
- 而绝大多数 **Scala 2.13 收紧规则**的报错都发生在 **refchecks**：
  - `Unit companion object is not allowed in source; instead, use () for the unit value`
  - `method X overrides nothing`
  - `incompatible type in overriding` / `object creation impossible` / `class needs to be abstract`
  - `forward reference extends over definition`
  - `method X in trait Y is accessed from super. It may not be abstract …`
  - `weaker access privileges in overriding`
  - `class X inherits conflicting members`（泛型边界不同但擦除后签名相同）

**本工程曾多次因此误判为"编译通过"**：某次日志只有 3 条错误，而那 3 条是
`common/container/Adapter.scala` 的解析错误 → typer 跳过 → `server/component/**`
的 **refchecks 根本没执行过**，却被当成"0 error"。

### 硬性规则

1. **一律用 `tools/scalac-check-full.ps1`（`-Xmaxerrs 4000`）**。`scalac-check.ps1` 的上限是默认的
   100，会把真实清单截断。
2. **判断"是否真的检查过"看错误类型，不看条数**：日志里只要出现 `unclosed comment` /
   `';' expected` / `not found:` / `type mismatch` 这类 parser/typer 错误，
   就说明 refchecks 及之后全部没跑，此时"剩余错误很少"毫无意义。
3. **doc 注释里绝对不能出现 `/*` 序列**（Scala 块注释可嵌套）。写 `common/tileentity/**`
   这种 glob 时必须去掉 `/**`，否则整个文件被吞掉。
4. 多代理并行跑校验脚本时，脚本已把 `%TEMP%\oc-scalac-out` 与 `%TEMP%\oc-scalac-args.txt`
   按 `$PID` 隔离，可并行；但**日志路径要各自指定**。

### 已修掉的同类问题（供检索）

| 位置 | 问题 | 修法 |
| --- | --- | --- |
| `server/network/Component.scala` | trait 里 `super.load/super.save` 调抽象 Java 接口方法 | 删除 `super.` 调用（上游无实现可调用） |
| `server/component/traits/{Tank*,World*Analytics,InventoryTransfer}` | `result(Unit, "...")` 共 39 处 | 改为 `result((), "...")` |
| `client/gui/**`（17 个类） | `WidgetContainer.addWidget` 与 `Screen.addWidget` 擦除后签名冲突 | `WidgetContainer.addWidget` 重命名（`super.addWidget` 不动） |

## 当前真实状态（第 16 轮）

`tools/scalac-check-full.ps1` 全量（483 sources）：**147 errors**，分布：

| 数量 | 位置 | 归属 |
| --- | --- | --- |
| ~71 | `server/component/**`（GraphicsCard 17 / DebugCard 10 / InternetCard 8 / Robot 7 / Geolyzer 7 / EEPROM 5 / Upgrade* 15 / DiskDriveMountable 2 等） | 代理 6f2af87d |
| 39 | `server/component/traits/{TankInventoryControl,TankWorldControl,WorldTankAnalytics,WorldInventoryAnalytics,InventoryTransfer,TankControl}` | 主代理（**已修**） |
| 17 | `client/gui/**` + `client/gui/Robot` | 代理 32169eee |
| 5 | `server/network/{Network,QuantumNetwork,DebugNetwork,Component}` + `server/driver/Registry` | 代理 1dc46a07 |
| 4 | `server/machine/{ArgumentsImpl,Machine}` | 代理 8c203e47 |
| 3 | `common/container/EmptyItemHandler` + `common/component/**` | 代理 97945702 |

`integration/**` 与 `common/event/**` 还**不在**编译集（依赖 `client.*`，需先解耦），归代理 0f6b5268；
`Mods.init()` 与全部驱动的注册都在这条链上，是"机箱认不出组件 / 电脑开不了机"的阻塞点。

### 主类还欠的接线（编译集就绪后立刻做）

```scala
// 1) 用 server.Proxy 取代 common.Proxy（只有它会执行 Mods.init()，注册全部驱动与模板）
private val proxy = new li.cil.oc.server.Proxy
// 2) 客户端入口（RegisterMenuScreensEvent → 屏幕工厂；ColorHandlers 由 client.Proxy 内部调用）
if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient) {
  li.cil.oc.client.ClientSetup.initialize(modBus)
}
```

`common/Proxy.preInit` 现已直接接上 `api.API.{driver,machine,network,nanomachines}` 与
Lua 架构注册（1.7.10 原本就在这一层）：因为 `client.Proxy` **不继承** `common.Proxy`，
否则客户端侧这四个字段永远是 `null`（组件数据在客户端无法解析）。

### 「GUI 打不开」的完整修复链（已完成的 3 处 + 待做的 2 处）

1. ✅ `common/block/traits/GUI.scala#useBlock` 原本是空实现（TODO），现在真的调
   `server.GuiHandler.openGui(...)`；
2. ✅ `common/GuiHandler.scala` 新增 `hasServerMenu`，避免 `GuiType.Screen/Waypoint` 这类
   无服务端容器的界面把 `null` 菜单交给 `Player#openMenu` 而崩在 `menu.containerId`；
3. ✅ `common/Proxy` 接上 API 四槽（否则驱动/组件全线不可用）；
4. ⬜ 主类改调 `server.Proxy`（执行 `Mods.init()`，注册全部驱动）；
5. ⬜ 主类改调 `client.ClientSetup.initialize(modBus)`（`RegisterMenuScreensEvent` 注册屏幕工厂）。

### 用户报告的 4 个问题与状态

| 问题 | 状态 |
| --- | --- |
| 1. GUI 界面打不开 | 🔄 见上方修复链，第 4/5 步待做 |
| 2. 标签页名称应为「开放式电脑」（原为 `opencomputers_neo Neo`） | ✅ 已修（`lang/zh_cn.json` 的 `itemGroup.opencomputers_neo`） |
| 3. 机箱 1-3 级 / 创造机箱贴图无颜色 | ✅ 已修（`ColorHandlers` + `tinted_cube`），待主类接线生效 |
| 4. 1-3 级屏幕没有贴图 | ✅ 已修（`models/block/screen1|2|3.json` 补成可用方盒） |
