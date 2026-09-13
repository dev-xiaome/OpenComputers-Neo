# 移植进度

> 目标：OpenComputers 1.8.10（MC 1.7.10 / Forge，Scala 2.11 + Java）
> → OpenComputers Neo（MC 1.21.1 / NeoForge 21.1.244，Scala 2.13.14 + Java 21）

## 已完成

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 项目骨架 | ✅ | `li.cil.oc.OpenComputersNeo`（Scala class + `@Mod`）、创造模式标签页、typesafe config 接入 |
| 构建工具链 | ✅ | 打通 Scala 2.13 + ModDevGradle 联合编译（`compileScala` 依赖 `compileJava`，Java 不得引用 Scala） |
| Gradle 分发 | ✅ | 改用 aliyun 镜像（`services.gradle.org` 在本机不可达） |
| 资源迁移 | ✅ | `assets/opencomputers` → `assets/open_computers_neo`；`.lang` → `.json`；`textures/blocks|items` → `block|item` |
| 批量重写脚本 | ✅ | `tools/port-rewrite.ps1`（import / 符号 / NBT 方法名），`tools/lang-to-json.ps1` |
| `li.cil.oc.api` | 🔄 | IMC / prefab / event / internal 分包移植中 |
| `li.cil.oc.util` | 🔄 | `ExtendedNBT`、`ExtendedItemStack`、`Rarity`、`ItemStackWrapper` 已手工完成，其余分包移植中 |
| 物品 NBT 方案 | ✅ | 自定义数据组件 `open_computers_neo:nbt`（`li.cil.oc.common.DataComponents`）+ `li.cil.oc.util.ItemNBT` + Scala 隐式类 |

## 关键设计决策

1. **语言**：实现层保留 Scala 2.13（原 2.11），API 层保留 Java。
   理由：60k 行 Scala 逻辑可原样搬运后按编译错误逐点修正，比整体改写为 Java 成本低得多。
2. **Java 不引用 Scala**：`compileJava` 先于 `compileScala`。需要双向调用时通过 Java 侧定义接口/常量解决。
3. **主类用 Scala class**：NeoForge 需要可反射实例化的 mod 类，Scala `object` 不行。
4. **物品 NBT**：1.21.1 `ItemStack` 无 tag，改用自定义 `DataComponentType<CompoundTag>`，
   并用 Scala 隐式类补回 `getTag()/setTag()/hasTag()`，从而让原代码基本无需改动。
5. **ASM coremod 全部移除**：`li.cil.oc.common.asm` + `TransformerLoader` 删除，改用 NeoForge 原生机制。
6. **Lua 架构**：待接入。原项目内嵌 `li.cil.repack.org.luaj`（OC-LuaJ 分支）。
   计划：优先用 Maven Central 的 `org.luaj:luaj-jse`，全局把 `li.cil.repack.org.luaj` 替换为 `org.luaj`；
   若 API 差异过大，则从 `MightyPirates/OC-LuaJ` 源码构建并 jarJar。

## 待办（按顺序）

1. `li.cil.oc.api` 全部编译通过
2. `li.cil.oc.util` 全部编译通过
3. `li.cil.oc.common`：方块 / 物品 / 注册 / 网络包（1.21.1 风格重写注册层）
4. `li.cil.oc.server`：`fs` → `machine`（含 Lua）→ `component`
5. `li.cil.oc.client`：方块实体渲染器、GUI、键位
6. 其它模组集成（`li.cil.oc.integration`）——最后做，非核心的整包省略
7. jarJar 打包运行时依赖（scala-library、typesafe config、luaj）

## 常用命令

```powershell
# 编译（注意：同一时间只能有一个 gradle 进程）
.\gradlew.bat build --console=plain
# 启动客户端
.\gradlew.bat runClient
# 启动服务端
.\gradlew.bat runServer
# 批量重写新搬过来的源码
.\tools\port-rewrite.ps1 -Root "src\main\scala"
```
