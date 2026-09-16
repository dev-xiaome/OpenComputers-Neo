# 移植进度

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
> **编译集（`gradle.properties` 的 `scala_ported_packages`）**：api/util/common（除 `event`/`GuiHandler`/`EventHandler`）/server-fs
> —— `common/event/**`、`common/GuiHandler.scala`、`common/EventHandler.scala` 因引用未移植的 `client.*`/`server.component.*`
> 暂时排除，等这两块完成后加回。
>
> **并行施工中（子代理因额度中断，需要重派）**：
> - `server/component/**`（约 50 文件：CPU/GPU/内存/硬盘/网卡/升级/机器人/平板）
> - `server/{Proxy,PacketHandler,PacketSender,ComponentTracker,GuiHandler,PetVisibility,agent/**,command/**}` + `integration/**`
>   （注意 `integration/vanilla/**` 项目里还没有，需从 `mod_src\...\src\main\scala\li\cil\oc\integration\vanilla\` 复制；
>    且 `src/main/scala` 下的 `.java` **不会被编译**，要放到 `src/main/java` 下）
> - `client/**`（渲染 / GUI / 键位；`client/renderer/font/FontParserHex.java` 目前被 build.gradle 排除，依赖 gnu.trove）
>
> **下一步**：重派上述 3 个代理 → 完成后把对应包加进 `scala_ported_packages` → `gradlew build` 转绿 →
> `gradlew runClient` 验证「创造模式标签页可见 + 放下方块 + 打开物品栏悬停不崩」。

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
