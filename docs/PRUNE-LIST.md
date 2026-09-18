# 裁剪清单：OCCE 相对 OC 原版多出的内容

**基准**：`mod_src/OpenComputers-master-MC1.7.10/src/main/scala/li/cil/oc/Constants.scala`（OC 原版 1.7.10）
**目标**：`src/main/scala/li/cil/oc/Constants.scala` 回到 **38 方块 / 119 物品**

> 名字均为 `final val X = "<value>"` 里的 **value**（当前树里的写法，已小写）。

## 要删除的方块（19）

```
case4            hologram3        projector
holoscreen1      holoscreen2      holoscreen3      holoscreen4
screen4
backflatscreen1  backflatscreen2  backflatscreen3  backflatscreen4
frontflatscreen1 frontflatscreen2 frontflatscreen3 frontflatscreen4
tape_drive       audio_cable      speaker
```

## 要删除的物品（29）

```
apu3              audiocard1        chip4             componentbus4
cpu4              graphicscard4     quadgraphicscard   hdd4
ssd1              ssd2              ssd3               microcontrollercase3
dronecase3        tabletcase3       server4            ram7
ram8              ramcreative       capacitormountable netheritesilicon
stickypistonupgrade                 navigationcard     rackkvm
tape              tape_copper       tape_gold          tape_diamond
tape_nether_star  tape_steel
```

## 每个条目要连带清理的东西

1. **注册行**：`common/init/OCBlocks.scala` / `OCItems.scala` 里的 `BLOCKS.register(...)` / `ITEMS.register(...)`
2. **实现类**：`common/block/**`、`common/blockentity/**`、`common/item/**`、`common/menu/**`、`common/component/**`
3. **driver / template / integration**：`integration/**`、`common/template/**` 里的对应条目
4. **客户端**：`client/gui/**`、`client/renderer/**` 的渲染器/模型/GUI
5. **资源**：
   - `assets/opencomputers_neo/blockstates/*.json`、`models/block|item/*.json`、`textures/**`
   - `assets/opencomputers_neo/lang/*.json` 的对应翻译键
6. **数据**：`data/opencomputers_neo/recipe/**`、`loot_table/**`、`tags/**`、`structure/**`
7. **datagen 源码**：`src/data/java/li/cil/oc/data/**`（`OCBlockLoot`、`OCBlockStateProvider`、`OCItemModelProvider`、`OCRecipeProvider`、`OCItemTagsProvider` 等）

## 注意

- **不要**改类名 `OpenComputers` —— 那是后续单独任务。
- 保留 1.7.10 就有的东西，即使它在当前树里叫别的名字（`accesspoint`→`access_point` 之类是改名，不是新增）。
- 删完后要保证引用它的配方/tag/进度/datagen 也一起清掉，否则 `runData` 会报 `Missing loottable` / 未注册物品。
- 验证命令（请**不要**并行跑，避免 Gradle 锁冲突，由主流程统一验证）：
  ```
  $env:JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8'
  cd 'D:\Workspace\Mods\1.21.1\OpenComputers Neo'
  .\gradlew.bat compileScala --console=plain
  .\gradlew.bat runData --console=plain
  ```
