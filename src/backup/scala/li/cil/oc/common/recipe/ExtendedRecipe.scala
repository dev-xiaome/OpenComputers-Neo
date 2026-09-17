package li.cil.oc.common.recipe

import java.util.UUID

import li.cil.oc.{Constants, Settings}
import li.cil.oc.api
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.common.item.data.{DroneData, MicrocontrollerData, PrintData, RobotData, TabletData}
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ItemStackNBTExtensions._
import li.cil.oc.util.{Color, ExtendedNBT, ItemColorizer, SideTracker}
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.{ItemStack, Items}

import scala.jdk.CollectionConverters._
import scala.util.control.Breaks._

/**
 * 合成结果的「NBT 后处理」（对应 1.7.10 的 `common.recipe.ExtendedRecipe`）。
 *
 * 1.21.1 迁移说明（重要）：
 *  - 原实现挂在 `ExtendedShapedOreRecipe` / `ExtendedShapelessOreRecipe` 的
 *    `getCraftingResult` 上。这两个 Forge 类（`ShapedOreRecipe` / `ShapelessOreRecipe`）
 *    在 1.21.1 已经不存在：普通的「有序 / 无序 + 矿辞」配方改用原版
 *    `minecraft:crafting_shaped` / `minecraft:crafting_shapeless` 的数据驱动 JSON
 *    （矿辞由 `Ingredient` 的 tag 形式覆盖），而合成结果的动态部分由
 *    `Recipe#assemble(CraftingInput, HolderLookup.Provider)` 产出。
 *  - 因此这里把后处理逻辑做成**独立函数** [[addNBTToResult]]，由自定义配方实现
 *    （见 [[ColorizeRecipe]] 这类 `CustomRecipe`）或将来接在 `ItemCraftedEvent` 上调用。
 *    参数用 `CraftingInput` 取代 `InventoryCrafting`，用显式的 `shapeless` 标志取代
 *    「`recipe.isInstanceOf[ExtendedShapelessOreRecipe]`」这种类型判断。
 *  - 其他映射：`stack.stackSize` → `getCount`/`setCount`；`ItemStack#writeToNBT` →
 *    `save(RegistryAccess, CompoundTag)`；`new ItemStack(Blocks.iron_block)` →
 *    `new ItemStack(Items.IRON_BLOCK)`（1.21.1 物品与方块共用 `Items` 注册表）；
 *    `NBTTagList#func_150296_c` → `CompoundTag#getAllKeys`；`hasNoTags` → `isEmpty`。
 */
object ExtendedRecipe {
  private lazy val drone = api.Items.get(Constants.ItemName.Drone)
  private lazy val eeprom = api.Items.get(Constants.ItemName.EEPROM)
  private lazy val luaBios = api.Items.get(Constants.ItemName.LuaBios)
  private lazy val mcu = api.Items.get(Constants.BlockName.Microcontroller)
  private lazy val navigationUpgrade = api.Items.get(Constants.ItemName.NavigationUpgrade)
  private lazy val linkedCard = api.Items.get(Constants.ItemName.LinkedCard)
  private lazy val floppy = api.Items.get(Constants.ItemName.Floppy)
  private lazy val hdds = Array(
    api.Items.get(Constants.ItemName.HDDTier1),
    api.Items.get(Constants.ItemName.HDDTier2),
    api.Items.get(Constants.ItemName.HDDTier3)
  )
  private lazy val cpus = Array(
    api.Items.get(Constants.ItemName.CPUTier1),
    api.Items.get(Constants.ItemName.CPUTier2),
    api.Items.get(Constants.ItemName.CPUTier3),
    api.Items.get(Constants.ItemName.APUTier1),
    api.Items.get(Constants.ItemName.APUTier2)
  )
  private lazy val robot = api.Items.get(Constants.BlockName.Robot)
  private lazy val tablet = api.Items.get(Constants.ItemName.Tablet)
  private lazy val print = api.Items.get(Constants.BlockName.Print)
  // 1.7.10 用 `display.Lore` 写「禁止自动合成」的提示；1.21.1 改为 `lore` 数据组件。
  private lazy val disabled = {
    val stack = new ItemStack(Items.DIRT)
    stack.set(DataComponents.LORE, new ItemLore(java.util.List.of(
      Component.literal("Autocrafting of this item is disabled to avoid exploits."))))
    stack
  }

  /**
   * 给合成结果补上原版配方表达不了的动态数据。
   *
   * @param craftedStack 配方产出的结果堆叠（会被就地修改并返回）
   * @param input        合成输入（对应原 `InventoryCrafting`）
   * @param shapeless    该配方是否为**无序**配方（对应原 `recipe.isInstanceOf[ExtendedShapelessOreRecipe]`）；
   *                     「复制软盘 / 复制 EEPROM」这类操作只在无序配方下生效，避免误伤有序配方
   */
  def addNBTToResult(craftedStack: ItemStack, input: CraftingInput, shapeless: Boolean): ItemStack = {
    if (craftedStack == null || craftedStack.isEmpty) return craftedStack
    val craftedItemName = api.Items.get(craftedStack)
    val inputs = getItems(input)
    // 对应原 `recipe.getRecipeSize`：非空槽位数量。
    val recipeSize = input.ingredientCount()

    if (craftedItemName == navigationUpgrade) {
      Option(api.Driver.driverFor(craftedStack)).foreach(driver =>
        for (stack <- inputs) {
          if (stack.getItem == Items.FILLED_MAP) {
            // Store information of the map used for crafting in the result.
            val nbt = driver.dataTag(craftedStack)
            nbt.setNewCompoundTag(Settings.namespace + "map", (tag: CompoundTag) => tag.merge(ExtendedNBT.encodeStack(stack)))
          }
        })
    }

    if (craftedItemName == linkedCard) {
      if (weAreBeingCalledFromAppliedEnergistics2) return disabled.copy()
      if (SideTracker.isServer()) {
        Option(api.Driver.driverFor(craftedStack)).foreach(driver => {
          val nbt = driver.dataTag(craftedStack)
          nbt.putString(Settings.namespace + "tunnel", UUID.randomUUID().toString)
        })
      }
    }

    if (cpus.contains(craftedItemName)) {
      // TODO(server.machine): 原为 `LuaStateFactory.setDefaultArch(craftedStack)`，
      // 给 CPU 写入默认架构。`li.cil.oc.server.machine.luac` 尚未移植（原生 Lua 暂不移植，
      // 见 docs/PROGRESS.md 第 7 条），等该包进入编译集后再接上；
      // 目前 LuaJ 架构由 `Proxy.preInit` 里的 `api.Machine.LuaArchitecture` 兜底。
    }

    if (craftedItemName == floppy || hdds.contains(craftedItemName)) {
      if (!craftedStack.hasTag()) {
        craftedStack.setTag(new CompoundTag())
      }
      val nbt = craftedStack.getTag()
      if (recipeSize == 1) {
        // Formatting / loot to normal disk conversion, only keep coloring.
        val colorKey = Settings.namespace + "color"
        for (stack <- inputs) {
          if (api.Items.get(stack) != null && (api.Items.get(stack) == floppy || api.Items.get(stack).name == "lootDisk") && stack.hasTag()) {
            val oldData = stack.getTag()
            if (oldData.contains(colorKey) && oldData.getInt(colorKey) != Color.dyes.indexOf("dyeLightGray")) {
              nbt.put(colorKey, oldData.get(colorKey).copy())
            }
          }
        }
        if (nbt.isEmpty) {
          craftedStack.setTag(null)
        }
      }
      else if (inputs.forall(stack => api.Items.get(stack) == floppy)) {
        // Copy operation.
        for (stack <- inputs) {
          if (api.Items.get(stack) == floppy && stack.hasTag()) {
            val oldData = stack.getTag()
            for (oldTagName <- oldData.getAllKeys.asScala) {
              nbt.put(oldTagName, oldData.get(oldTagName).copy())
            }
          }
        }
      }
    }

    if (craftedItemName == print && shapeless && recipeSize == 2) {
      // First, copy old data.
      val data = new PrintData(craftedStack)
      for (stack <- inputs) {
        if (api.Items.get(stack) == print) {
          data.load(stack)
        }
      }

      // Then apply new data. 1.21.1 里方块与物品统一在 `Items` 注册表。
      val beaconBlocks = Array(
        new ItemStack(Items.IRON_BLOCK),
        new ItemStack(Items.GOLD_BLOCK),
        new ItemStack(Items.EMERALD_BLOCK),
        new ItemStack(Items.DIAMOND_BLOCK)
      )

      val glowstoneDust = new ItemStack(Items.GLOWSTONE_DUST)
      val glowstone = new ItemStack(Items.GLOWSTONE)
      for (stack <- inputs) {
        if (beaconBlocks.exists(_.is(stack.getItem))) {
          if (data.isBeaconBase) {
            // Crafting wouldn't change anything, prevent accidental resource loss.
            return ItemStack.EMPTY
          }
          data.isBeaconBase = true
        }
        if (glowstoneDust.is(stack.getItem)) {
          if (data.lightLevel == 15) {
            // Crafting wouldn't change anything, prevent accidental resource loss.
            return ItemStack.EMPTY
          }
          data.lightLevel = math.min(15, data.lightLevel + 1)
        }
        if (glowstone.is(stack.getItem)) {
          if (data.lightLevel == 15) {
            // Crafting wouldn't change anything, prevent accidental resource loss.
            return ItemStack.EMPTY
          }
          data.lightLevel = math.min(15, data.lightLevel + 4)
        }
      }

      // Finally apply modified data.
      data.save(craftedStack)
    }

    // EEPROM copying.
    if (craftedItemName == eeprom && craftedStack.getCount == 2 && shapeless && recipeSize == 2) breakable {
      for (stack <- inputs) {
        if (api.Items.get(stack) == eeprom && stack.hasTag()) {
          val copy = stack.getTag().copy().asInstanceOf[CompoundTag]
          // Erase node address, just in case.
          copy.getCompound(Settings.namespace + "data").getCompound("node").remove("address")
          craftedStack.setTag(copy)
          break()
        }
      }
    }

    // Swapping EEPROM in devices.
    recraft(craftedStack, inputs, mcu, stack => new MCUDataWrapper(stack))
    recraft(craftedStack, inputs, drone, stack => new DroneDataWrapper(stack))
    recraft(craftedStack, inputs, robot, stack => new RobotDataWrapper(stack))
    recraft(craftedStack, inputs, tablet, stack => new TabletDataWrapper(stack))

    craftedStack
  }

  /** 合成输入里的非空堆叠（对应原 `getItems(InventoryCrafting)`）。 */
  def getItems(input: CraftingInput): Seq[ItemStack] =
    input.items().asScala.filter(stack => stack != null && !stack.isEmpty).toSeq

  private def recraft(craftedStack: ItemStack, inputs: Seq[ItemStack], descriptor: ItemInfo, dataFactory: (ItemStack) => ItemDataWrapper): Unit = {
    if (api.Items.get(craftedStack) == descriptor) {
      // Find old Microcontroller.
      inputs.find(stack => api.Items.get(stack) == descriptor) match {
        case Some(oldMcu) =>
          val data = dataFactory(oldMcu)

          // Remove old EEPROM.
          val oldRom = data.components.filter(stack => api.Items.get(stack) == eeprom)
          data.components = data.components.diff(oldRom)

          // Insert new EEPROM.
          for (stack <- inputs) {
            if (api.Items.get(stack) == eeprom) {
              data.components :+= stack.copyWithCount(1)
            }
          }

          data.save(craftedStack)
        case _ =>
      }
    }
  }

  /**
   * 是否由 AE2 的样板终端调用。
   *
   * TODO(integration): 原实现是 `Mods.AppliedEnergistics2.isAvailable && 堆栈里含
   * `appeng.container.implementations.ContainerPatternTerm``。`li.cil.oc.integration` 整体
   * 尚未移植（见 docs/PROGRESS.md 第 8 条），这里恒为 `false`，等 AE2 集成移植后恢复。
   */
  private def weAreBeingCalledFromAppliedEnergistics2 = false

  private trait ItemDataWrapper {
    def components: Array[ItemStack]

    def components_=(value: Array[ItemStack]): Unit

    def save(stack: ItemStack): Unit
  }

  private class MCUDataWrapper(val stack: ItemStack) extends ItemDataWrapper {
    val data = new MicrocontrollerData(stack)

    override def components = data.components

    override def components_=(value: Array[ItemStack]) = data.components = value

    override def save(stack: ItemStack) = data.save(stack)
  }

  private class DroneDataWrapper(val stack: ItemStack) extends ItemDataWrapper {
    val data = new DroneData(stack)

    override def components = data.components

    override def components_=(value: Array[ItemStack]) = data.components = value

    override def save(stack: ItemStack) = data.save(stack)
  }

  private class RobotDataWrapper(val stack: ItemStack) extends ItemDataWrapper {
    val data = new RobotData(stack)

    override def components = data.components

    override def components_=(value: Array[ItemStack]) = data.components = value

    override def save(stack: ItemStack) = data.save(stack)
  }

  private class TabletDataWrapper(val stack: ItemStack) extends ItemDataWrapper {
    val data = new TabletData(stack)

    var components = data.items.collect { case Some(item) => item }

    override def save(stack: ItemStack) = {
      data.items = components.map(stack => Option(stack))
      data.save(stack)
    }
  }

}
