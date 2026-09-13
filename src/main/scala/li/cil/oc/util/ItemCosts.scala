package li.cil.oc.util

import java.util

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.api
import net.minecraft.core.HolderLookup
import net.minecraft.world.item.{Item, ItemStack, Items}
import net.minecraft.world.item.crafting.{CraftingInput, Recipe, RecipeHolder, RecipeManager, RecipeType}
import net.minecraft.world.level.block.{Block, Blocks}
import net.neoforged.neoforge.server.ServerLifecycleHooks

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 物品的材料成本推算（用于拆解器 / 提示）。
 *
 * 1.21.1 迁移要点：
 *  - 配方系统重写：`CraftingManager.getInstance.getRecipeList` →
 *    `RecipeManager#getAllRecipesFor(RecipeType.CRAFTING)`；
 *    `FurnaceRecipes.smelting.getSmeltingList` → `RecipeType.SMELTING`
 *  - `ShapedRecipes` / `ShapelessRecipes` / `ShapedOreRecipe` / `ShapelessOreRecipe`
 *    统一为“`Ingredient` 列表 + 输出物品栈”，不再按类型分支
 *  - `OreDictionary.WILDCARD_VALUE` 已移除，`fuzzyEquals` 改用
 *    `ItemStack#isSameItem`（同物品即视为同类，忽略组件/计数）
 *  - `getUnlocalizedName` → `getDescriptionId`，`getDisplayName` → `getHoverName`
 *  - TODO(标签): 1.7.10 的矿辞输入已由 `Ingredient`（物品或 tag）取代；
 *    这里逐条展开 `Ingredient#getItems`，tag 输入取第一个代表物品
 */
object ItemCosts {
  private final val Timeout = 500

  private val cache = mutable.Map.empty[ItemStackWrapper, Iterable[(ItemStack, Double)]]

  private var started = 0L

  cache += new ItemStackWrapper(api.Items.get(Constants.ItemName.IronNugget).createItemStack(1)) -> Iterable((new ItemStack(Items.IRON_INGOT), 1.0 / 9.0))

  def terminate(item: Item): Unit = cache += new ItemStackWrapper(new ItemStack(item)) -> mutable.Iterable((new ItemStack(item), 1))

  /** 旧版带 metadata 的重载；1.21.1 的物品不再用 metadata 区分，`meta` 被忽略。 */
  def terminate(item: Item, meta: Int): Unit = terminate(item)

  def terminate(block: Block): Unit = cache += new ItemStackWrapper(new ItemStack(block)) -> mutable.Iterable((new ItemStack(block), 1))

  terminate(Blocks.CLAY)
  terminate(Blocks.COBBLESTONE)
  terminate(Blocks.GLASS)
  terminate(Blocks.OAK_PLANKS)
  terminate(Blocks.SAND)  terminate(Blocks.STONE)
  terminate(Items.BLAZE_ROD)
  terminate(Items.BUCKET)
  terminate(Items.CLAY_BALL)
  terminate(Items.COAL)
  terminate(Items.DIAMOND)
  // 1.21.1 的 16 种染料已是独立物品（旧版是指定 metadata 的单一 `dye`）。
  for (dye <- Seq(
    Items.WHITE_DYE, Items.ORANGE_DYE, Items.MAGENTA_DYE, Items.LIGHT_BLUE_DYE,
    Items.YELLOW_DYE, Items.LIME_DYE, Items.PINK_DYE, Items.GRAY_DYE,
    Items.LIGHT_GRAY_DYE, Items.CYAN_DYE, Items.PURPLE_DYE, Items.BLUE_DYE,
    Items.BROWN_DYE, Items.GREEN_DYE, Items.RED_DYE, Items.BLACK_DYE)) {
    terminate(dye)
  }
  terminate(Items.EMERALD)
  terminate(Items.ENDER_PEARL)
  terminate(Items.ENDER_EYE)
  terminate(Items.GHAST_TEAR)
  terminate(Items.GLOWSTONE_DUST)
  terminate(Items.GOLD_INGOT)
  terminate(Items.IRON_INGOT)
  terminate(Items.QUARTZ)
  terminate(Items.NETHER_STAR)
  terminate(Items.PAPER)
  terminate(Items.REDSTONE)
  terminate(Items.STRING)
  terminate(Items.SLIME_BALL)
  terminate(Items.STICK)

  def hasCosts(stack: ItemStack): Boolean = {
    // TODO(标签): 1.7.10 会先用 `Mods.CraftingCosts.isAvailable` 判断是否有外部成本计算模组，
    // 该集成包尚未移植，这里直接使用内置逻辑。
    val ingredients = computeIngredients(stack)
    ingredients.size > 0 && (ingredients.size > 1 || !fuzzyEquals(ingredients.head._1, stack))
  }

  def addTooltip(stack: ItemStack, tooltip: util.List[String]): Unit = {
    tooltip.add(Localization.Tooltip.Materials)
    for ((ingredient, count) <- computeIngredients(stack)) {
      val line = math.ceil(count).toInt + "x " + ingredient.getHoverName.getString
      tooltip.add(line)
    }
  }

  protected def computeIngredients(what: ItemStack): Iterable[(ItemStack, Double)] = cache.synchronized {
    started = System.currentTimeMillis()
    recipeManager match {
      case Some((manager, registries)) => computeIngredientsWith(what, manager, registries)
      case _ => Iterable.empty
    }
  }

  private def computeIngredientsWith(what: ItemStack, manager: RecipeManager, registries: HolderLookup.Provider): Iterable[(ItemStack, Double)] = {
    def deflate(list: Iterable[(ItemStack, Double)]): Iterable[(ItemStack, Double)] = {
      val counts = mutable.Map.empty[ItemStack, Double]
      for (entry <- list) {
        counts.find {
          case (key, value) => fuzzyEquals(key, entry._1)
        } match {
          case Some((key, value)) => counts.update(key, value + entry._2)
          case _ => counts += entry._1 -> entry._2
        }
      }
      counts
    }

    /** 在合成 / 熔炼配方中查找目标物品的原料。 */
    def findRecipe(stack: ItemStack): Option[(Iterable[ItemStack], Int)] = {
      def resultOf(recipe: Recipe[CraftingInput]): ItemStack = recipe.getResultItem(registries)

      val crafting = manager.getAllRecipesFor(RecipeType.CRAFTING).asScala.
        map(holder => holder.asInstanceOf[RecipeHolder[Recipe[CraftingInput]]]).
        find(holder => !resultOf(holder.value()).isEmpty && fuzzyEquals(stack, resultOf(holder.value())))
      crafting match {
        case Some(holder) =>
          val recipe = holder.value()
          Some((recipe.getIngredients.asScala.flatMap(_.getItems.headOption), resultOf(recipe).getCount))
        case _ =>
          val smelting = manager.getAllRecipesFor(RecipeType.SMELTING).asScala.
            find(holder => !holder.value().getResultItem(registries).isEmpty && fuzzyEquals(stack, holder.value().getResultItem(registries)))
          smelting.map(holder => (holder.value().getIngredients.asScala.flatMap(_.getItems.headOption), holder.value().getResultItem(registries).getCount))
      }
    }

    def accumulate(input: Any, path: Seq[ItemStack] = Seq.empty): Iterable[(ItemStack, Double)] = {
      val passed = System.currentTimeMillis() - started
      if (passed > Timeout) Iterable.empty
      else input match {
        case stack: ItemStack =>
          cache.find {
            case (key, value) => fuzzyEquals(key.inner, stack)
          } match {
            case Some((_, value)) => value
            case _ =>
              if (path.exists(value => fuzzyEquals(value, stack))) {
                Iterable((stack, 1.0))
              }
              else {
                findRecipe(stack) match {
                  case Some((ingredients, output)) =>
                    val scaled = deflate(ingredients.flatMap(accumulate(_, path :+ stack)).map {
                      case (ingredient, count) => (ingredient.copy(), count / output)
                    }).toArray.sortBy(_._1.getDescriptionId)
                    cache += new ItemStackWrapper(stack.copy()) -> scaled
                    scaled
                  case _ => Iterable((stack, 1.0))
                }
              }
          }
        case list: util.ArrayList[ItemStack]@unchecked if !list.isEmpty =>
          var result = Iterable.empty[(ItemStack, Double)]
          for (stack <- list if result.isEmpty) {
            cache.find {
              case (key, value) => fuzzyEquals(key.inner, stack)
            } match {
              case Some((_, value)) => result = value
              case _ =>
            }
          }
          if (result.isEmpty) {
            result = accumulate(list.get(0), path)
          }
          result
        case _ => Iterable.empty
      }
    }

    accumulate(what)
  }

  /**
   * 配方管理器与注册表访问器（客户端优先用当前关卡，其次用当前服务端）。
   *
   * TODO(标签): 1.21.1 的配方改为数据驱动，取不到配方时成本推算会退化为“无原料”。
   */
  private def recipeManager: Option[(RecipeManager, HolderLookup.Provider)] = {
    val client = net.minecraft.client.Minecraft.getInstance
    if (client != null && client.level != null) {
      return Some((client.level.getRecipeManager, client.level.registryAccess()))
    }
    val server = ServerLifecycleHooks.getCurrentServer
    if (server != null) {
      return Some((server.getRecipeManager, server.registryAccess()))
    }
    None
  }

  // In case you'd like to use this class for your items and your items use
  // NBT data in the item stack to differentiate them uncomment the last part.
  // We don't use this in OC because the NBT of items can change dynamically,
  // for example by components being assigned an address, which will break the
  // equals check.
  private def fuzzyEquals(stack1: ItemStack, stack2: ItemStack) =
    stack1 == stack2 || (stack1 != null && stack2 != null &&
      !stack1.isEmpty && !stack2.isEmpty &&
      // 1.21.1 没有 `OreDictionary.WILDCARD_VALUE`：同物品即视为同类（忽略组件与计数）。
      ItemStack.isSameItem(stack1, stack2))
}
