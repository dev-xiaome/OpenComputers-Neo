# 移植进度

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
| `li.cil.oc.util` | 🔄 | 移植完成，正在清最后约 25 个编译错误 |
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

## 待办（按顺序）

1. `li.cil.oc.util` 全部编译通过
2. `li.cil.oc.common`：方块 / 物品 / 注册 / 网络包（1.21.1 风格重写注册层）
3. `li.cil.oc.server`：`fs` → `machine`（含 Lua）→ `component`
4. `li.cil.oc.client`：方块实体渲染器、GUI、键位
5. jarJar 打包运行时依赖（scala-library、typesafe config、luaj）

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
