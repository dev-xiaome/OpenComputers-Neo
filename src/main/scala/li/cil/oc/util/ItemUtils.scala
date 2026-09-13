package li.cil.oc.util

import java.util.Random

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Tier
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.{BlockItem, BucketItem, ItemStack}
import net.minecraft.world.item.crafting.{CraftingInput, Ingredient, Recipe, RecipeHolder, RecipeManager, RecipeType}
import net.neoforged.neoforge.server.ServerLifecycleHooks

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 物品工具。
 *
 * 1.21.1 迁移要点：
 *  - 配方系统重写：`CraftingManager.getInstance.getRecipeList` →
 *    `Level#getRecipeManager` + `RecipeManager#getAllRecipesFor(RecipeType.CRAFTING)`，
 *    配方为 `RecipeHolder[Recipe[CraftingInput]]`
 *  - `ShapedRecipes` / `ShapelessRecipes` / `ShapedOreRecipe` / `ShapelessOreRecipe`
 *    统一为“`Ingredient` 列表 + 输出物品栈”，因此不再需要按类型分支
 *  - `ItemStack#getHasSubtypes` / `getItemDamage` 已移除，
 *    改用 `ItemStack#isSameItem` / `isSameItemSameComponents`
 *  - `Item.itemRegistry` / `Block.blockRegistry` 已移除，改用 `BuiltInRegistries`
 *  - `stackSize` → `getCount()` / `setCount()`
 *  - `ItemBucket` → `BucketItem`
 */
object ItemUtils {
  def caseTier(stack: ItemStack) = {
    val descriptor = api.Items.get(stack)
    if (descriptor == api.Items.get(Constants.BlockName.CaseTier1)) Tier.One
    else if (descriptor == api.Items.get(Constants.BlockName.CaseTier2)) Tier.Two
    else if (descriptor == api.Items.get(Constants.BlockName.CaseTier3)) Tier.Three
    else if (descriptor == api.Items.get(Constants.BlockName.CaseCreative)) Tier.Four
    else if (descriptor == api.Items.get(Constants.ItemName.MicrocontrollerCaseTier1)) Tier.One
    else if (descriptor == api.Items.get(Constants.ItemName.MicrocontrollerCaseTier2)) Tier.Two
    else if (descriptor == api.Items.get(Constants.ItemName.MicrocontrollerCaseCreative)) Tier.Four
    else if (descriptor == api.Items.get(Constants.ItemName.DroneCaseTier1)) Tier.One
    else if (descriptor == api.Items.get(Constants.ItemName.DroneCaseTier2)) Tier.Two
    else if (descriptor == api.Items.get(Constants.ItemName.DroneCaseCreative)) Tier.Four
    else if (descriptor == api.Items.get(Constants.ItemName.ServerTier1)) Tier.One
    else if (descriptor == api.Items.get(Constants.ItemName.ServerTier2)) Tier.Two
    else if (descriptor == api.Items.get(Constants.ItemName.ServerTier3)) Tier.Three
    else if (descriptor == api.Items.get(Constants.ItemName.ServerCreative)) Tier.Four
    else if (descriptor == api.Items.get(Constants.ItemName.TabletCaseTier1)) Tier.One
    else if (descriptor == api.Items.get(Constants.ItemName.TabletCaseTier2)) Tier.Two
    else if (descriptor == api.Items.get(Constants.ItemName.TabletCaseCreative)) Tier.Four
    else Tier.None
  }

  def caseNameWithTierSuffix(name: String, tier: Int) = name + (if (tier == Tier.Four) "Creative" else (tier + 1).toString)

  /**
   * 取得某个物品的“拆解原料”。
   *
   * TODO(标签): 1.7.10 通过 `OreDictionary` 展开矿辞输入；1.21.1 中 `Ingredient`
   * 内部已统一由物品 / tag 构成（tag 即 `c:` 命名空间），这里从
   * `Ingredient#getItems` 中任取一个代表物品，与旧版 `resolveOreDictEntries` 行为一致。
   */
  def getIngredients(stack: ItemStack): Array[ItemStack] = getIngredientsImpl(stack) match {
    case Some(result) => result
    case _ => Array.empty
  }

  private def getIngredientsImpl(stack: ItemStack): Option[Array[ItemStack]] = try {
    def getFilteredInputs(inputs: Iterable[ItemStack], outputSize: Int) = (inputs.filter(input =>
      input != null &&
      !input.isEmpty &&
      input.getCount / outputSize > 0 &&
      // Strip out buckets, because those are returned when crafting, and
      // we have no way of returning the fluid only (and I can't be arsed
      // to make it output fluids into fluiducts or such, sorry).
      !input.getItem.isInstanceOf[BucketItem]).toArray, outputSize)

    def isInputBlacklisted(stack: ItemStack) = stack.getItem match {
      case item: BlockItem =>
        Settings.get.disassemblerInputBlacklist.contains(BuiltInRegistries.BLOCK.getKey(item.getBlock).toString)
      case item: net.minecraft.world.item.Item =>
        Settings.get.disassemblerInputBlacklist.contains(BuiltInRegistries.ITEM.getKey(item).toString)
      case _ => false
    }

    val (manager, registries) = recipeManager match {
      case Some(value) => value
      case _ => return None
    }

    val matching = manager.getAllRecipesFor(RecipeType.CRAFTING).asScala.
      map(holder => holder.asInstanceOf[RecipeHolder[Recipe[CraftingInput]]]).
      filter(holder => !holder.value().getResultItem(registries).isEmpty).
      filter(holder => ItemStack.isSameItem(holder.value().getResultItem(registries), stack)).
      map(holder => getFilteredInputs(resolveIngredients(holder.value().getIngredients), holder.value().getResultItem(registries).getCount))

    val (ingredients, count) = matching.collectFirst {
      case (inputs, outputSize) if !inputs.exists(isInputBlacklisted) => (inputs, outputSize)
    } match {
      case Some(entry) => entry
      case _ => return None
    }

    // Avoid positive feedback loops.
    if (ingredients.exists(ingredient => ItemStack.isSameItem(ingredient, stack))) {
      return None
    }
    // Merge equal items for size division by output size.
    val merged = mutable.ArrayBuffer.empty[ItemStack]
    for (ingredient <- ingredients) {
      merged.find(sameItemIgnoringCount(_, ingredient)) match {
        case Some(entry) => entry.grow(ingredient.getCount)
        case _ => merged += ingredient.copy()
      }
    }
    merged.foreach(entry => entry.setCount(entry.getCount / count))
    // Split items up again to 'disassemble them individually'.
    val distinct = mutable.ArrayBuffer.empty[ItemStack]
    for (ingredient <- merged) {
      val size = ingredient.getCount max 1
      ingredient.setCount(1)
      for (i <- 0 until size) {
        distinct += ingredient.copy()
      }
    }
    Some(distinct.toArray)
  }
  catch {
    case t: Throwable =>
      OpenComputers.log.warn("Whoops, something went wrong when trying to figure out an item's parts.", t)
      None
  }

  private lazy val rng = new Random()

  /**
   * 配方管理器与注册表访问器。
   *
   * 客户端优先使用当前关卡（单机时即为集成服务端的配方表），
   * 否则回退到 `ServerLifecycleHooks` 提供的当前服务端；
   * 两者都不可用时返回 `None`，拆解功能退化为“无原料”。
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

  /** 把 `Ingredient` 列表展开为具体物品（每个原料任取一个代表物品）。 */
  private def resolveIngredients(ingredients: java.util.List[Ingredient]): Iterable[ItemStack] =
    ingredients.asScala.flatMap(resolveIngredient)

  private def resolveIngredient(ingredient: Ingredient): Option[ItemStack] = {
    val items = ingredient.getItems
    if (items == null || items.isEmpty) None
    else Option(items(rng.nextInt(items.length))).filter(!_.isEmpty)
  }

  /** 忽略数量的同类物品判断（等价于旧版 `ItemStack#isItemEqual`）。 */
  private def sameItemIgnoringCount(a: ItemStack, b: ItemStack): Boolean =
    a != null && b != null && !a.isEmpty && !b.isEmpty && ItemStack.isSameItem(a, b)

}
