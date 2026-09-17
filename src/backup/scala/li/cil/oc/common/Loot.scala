package li.cil.oc.common

import java.io
import java.util.concurrent.Callable

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.fs.FileSystem
import li.cil.oc.common.init.Registry
import li.cil.oc.util.Color
// `getTag()` / `hasTag()` / `setTag()` 由 util 侧的隐式类提供。
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModLoadingContext
import net.neoforged.neoforge.event.level.LevelEvent

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 战利品条目（原 1.7.10 的 `WeightedRandomChestContent` 子类）。
 *
 * 1.21.1 迁移要点：
 *  - `WeightedRandomChestContent` / `ChestGenHooks` 已随 1.7.10 的箱子战利品系统一起消失，
 *    1.21.1 的箱子内容改为**数据驱动 JSON**（`data/<ns>/loot_table/...`），运行期注入需要往
 *    `LootTableLoadEvent` 里的 `LootTable` 追加池（`LootTable#addPool`），或改用 data 侧的
 *    `neoforge:loot_modifier`。
 *  - 这里保留类名与「按 [[Loot.disksForSampling]] 随机抽一个软盘」的语义（[[generateChestContent]]），
 *    供注入接线与 JSON 转换复用。
 *
 * TODO(loot): 箱子注入尚未接线。接手者需要：
 *  1. 在 [[Loot.init]] 里挂 `LootTableLoadEvent` 监听（NeoForge 主事件总线），对
 *     [[Loot.containers]] 里列出的原版箱子战利品表追加一个池：
 *     `LootPool.lootPool().setRolls(ConstantValue.exactly(1))` + 每个
 *     `Loot.disksForSampling` 条目一个 `LootItem.lootTableItem(软盘)` +
 *     `SetComponentsFunction.setComponent(li.cil.oc.common.DataComponents.NBT, tag)`；
 *  2. 概率语义：1.7.10 的 `lootProbability` 是「与箱子里其它物品竞争时的相对权重」，
 *     1.21.1 的独立池没有可比较的总权重，因此需要改用
 *     `LootItemRandomChanceCondition.randomChance(...)` 之类显式概率，或干脆生成
 *     `data/opencomputers_neo/loot_table/` 下的 JSON（推荐，和配方一起做）。
 *  3. 注意顺序：`LootTableLoadEvent` 在数据包加载时触发，早于世界加载，
 *     届时只有 `globalDisks` 可用；`worldDisks`（存档目录里的自定义磁盘）需要
 *     用「延迟到抽取时再查表」的自定义 `LootPoolEntryType`，或者提示玩家重载数据包。
 */
class Loot {
  /** 原 `WeightedRandomChestContent` 的权重；现在只在文档意义上保留（见类注释的 TODO）。 */
  val weight: Int = Settings.get.lootProbability

  /**
   * 抽取一个战利品软盘（等价于 1.7.10 的 `generateChestContent` 里挑一个磁盘那一步）。
   *
   * 没有可抽取的磁盘时返回空数组（原实现会让箱子生成空内容）。
   */
  def generateChestContent(random: net.minecraft.util.RandomSource): Array[ItemStack] =
    if (Loot.disksForSampling.nonEmpty)
      Array(Loot.disksForSampling(random.nextInt(Loot.disksForSampling.length)).copy())
    else Array.empty[ItemStack]
}

object Loot {
  /**
   * 原 1.7.10 的 `ChestGenHooks.*_CHEST` 常量对应的 1.21.1 战利品表 id。
   *
   * TODO(loot): 与 [[Loot]] 类注释里的注入方案配套使用。
   */
  val containers = Array(
    "minecraft:chests/simple_dungeon",
    "minecraft:chests/desert_pyramid",
    "minecraft:chests/jungle_temple",
    "minecraft:chests/stronghold_library")

  val factories = mutable.Map.empty[String, Callable[FileSystem]]

  val globalDisks = mutable.ArrayBuffer.empty[(ItemStack, Int)]

  val worldDisks = mutable.ArrayBuffer.empty[(ItemStack, Int)]

  def disksForCycling = if (disksForCyclingClient.nonEmpty) disksForCyclingClient else disksForCyclingServer

  val disksForCyclingServer = mutable.ArrayBuffer.empty[ItemStack]

  val disksForCyclingClient = mutable.ArrayBuffer.empty[ItemStack]

  val disksForSampling = mutable.ArrayBuffer.empty[ItemStack]

  val disksForClient = mutable.ArrayBuffer.empty[ItemStack]

  def isLootDisk(stack: ItemStack): Boolean =
    stack != null && !stack.isEmpty &&
      api.Items.get(stack) == api.Items.get(Constants.ItemName.Floppy) &&
      stack.hasTag() && stack.getTag().contains(Settings.namespace + "lootFactory", Tag.TAG_STRING)

  def registerLootDisk(name: String, color: Int, factory: Callable[FileSystem], doRecipeCycling: Boolean): ItemStack = {
    val mod = activeModId

    OpenComputers.log.debug(s"Registering loot disk '$name' from mod $mod.")

    val modSpecificName = mod + ":" + name

    val data = new CompoundTag()
    data.putString(Settings.namespace + "fs.label", name)

    val nbt = new CompoundTag()
    nbt.put(Settings.namespace + "data", data)

    // Store this top level, so it won't get wiped on save.
    nbt.putString(Settings.namespace + "lootFactory", modSpecificName)
    nbt.putInt(Settings.namespace + "color", color max 0 min 15)

    val stack = Registry.createItemStack(Constants.ItemName.Floppy, 1)
    if (stack == null) {
      // 只有在物品还没登记时才会走到这里（正常流程里 `Loot.init` 跑在注册表冻结之后）。
      OpenComputers.log.warn(s"Failed registering loot disk '$name': the floppy item is not registered.")
      return ItemStack.EMPTY
    }
    stack.setTag(nbt)

    Loot.factories += modSpecificName -> factory

    if (doRecipeCycling) {
      Loot.disksForCyclingServer += stack
    }

    stack.copy()
  }

  def init(): Unit = {
    // TODO(loot): 原实现在这里把 `new Loot()` 通过 `ChestGenHooks.addItem` 塞进四个原版箱子；
    // 1.21.1 没有运行期箱子注册表，改走 `LootTableLoadEvent`（见 `Loot` 类注释的 TODO）。
    // 接线完成后在这里挂上主事件总线监听，并删掉本行注释。

    val list = new java.util.Properties()
    val listStream = getClass.getResourceAsStream("/assets/" + Settings.resourceDomain + "/loot/loot.properties")
    if (listStream == null) {
      OpenComputers.log.warn("Could not find the loot disk descriptor file; no global loot disks will be registered.")
      return
    }
    try {
      list.load(listStream)
    }
    finally {
      listStream.close()
    }
    parseLootDisks(list, globalDisks, external = false)
  }

  /** 世界加载时读取存档目录下的自定义战利品描述文件（等价于原 `WorldEvent.Load` 处理）。 */
  @SubscribeEvent
  def initForWorld(e: LevelEvent.Load): Unit = e.getLevel match {
    case level: ServerLevel if level.dimension() == Level.OVERWORLD =>
      worldDisks.clear()
      disksForSampling.clear()
      val path = new io.File(level.getServer.getWorldPath(LevelResource.ROOT).toFile, Settings.savePath + "loot/")
      if (path.exists && path.isDirectory) {
        val listFile = new io.File(path, "loot.properties")
        if (listFile.exists && listFile.isFile) {
          try {
            val listStream = new io.FileInputStream(listFile)
            val list = new java.util.Properties()
            try {
              list.load(listStream)
            }
            finally {
              listStream.close()
            }
            parseLootDisks(list, worldDisks, external = true)
          }
          catch {
            case t: Throwable => OpenComputers.log.warn("Failed opening loot descriptor file in saves folder.", t)
          }
        }
      }
      for (entry <- globalDisks if !worldDisks.contains(entry)) {
        worldDisks += entry
      }
      for ((stack, count) <- worldDisks) {
        for (i <- 0 until count) {
          disksForSampling += stack
        }
      }
    case _ =>
  }

  private def parseLootDisks(list: java.util.Properties, acc: mutable.ArrayBuffer[(ItemStack, Int)], external: Boolean): Unit = {
    for (key <- list.stringPropertyNames.asScala) {
      val value = list.getProperty(key)
      try value.split(":") match {
        case Array(name, count, color) =>
          acc += ((createLootDisk(name, key, external, Some(Color.dyes.indexOf(color))), count.toInt))
        case Array(name, count) =>
          acc += ((createLootDisk(name, key, external), count.toInt))
        case _ =>
          acc += ((createLootDisk(value, key, external), 1))
      }
      catch {
        case t: Throwable => OpenComputers.log.warn("Bad loot descriptor: " + value, t)
      }
    }
  }

  def createLootDisk(name: String, path: String, external: Boolean, color: Option[Int] = None): ItemStack = {
    val callable = if (external) new Callable[FileSystem] {
      override def call(): FileSystem = api.FileSystem.asReadOnly(api.FileSystem.fromSaveDirectory("loot/" + path, 0, false))
    } else new Callable[FileSystem] {
      override def call(): FileSystem = api.FileSystem.fromClass(OpenComputers.getClass, Settings.resourceDomain, "loot/" + path)
    }
    val stack = registerLootDisk(path, color.getOrElse(8), callable, doRecipeCycling = true)
    if (stack == null || stack.isEmpty) return ItemStack.EMPTY
    // 1.7.10 的 `ItemStack#setStackDisplayName`：1.21.1 改为写 `custom_name` 数据组件。
    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name))
    if (!external) {
      // 等价于原 `Items.registerStack(stack, path)`：登记描述符并追加到创造模式标签页。
      Registry.Items.registerStack(path, stack)
    }
    stack
  }

  /**
   * 当前正在初始化的 mod id（等价于原 `Loader.instance.activeModContainer.getModId`）。
   *
   * NeoForge 不再暴露 `Loader#activeModContainer`，最接近的是
   * [[net.neoforged.fml.ModLoadingContext#getActiveContainer]]；取不到时退化为本 mod 的 id。
   */
  private def activeModId: String = try {
    val container = ModLoadingContext.get().getActiveContainer
    if (container == null || container.getModId == null) OpenComputers.ID else container.getModId
  }
  catch {
    case _: Throwable => OpenComputers.ID
  }
}
