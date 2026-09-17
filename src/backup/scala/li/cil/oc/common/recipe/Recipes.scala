package li.cil.oc.common.recipe

import com.typesafe.config.Config
import li.cil.oc.OpenComputers
import li.cil.oc.common.item.traits.Delegate
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.level.block.Block

import scala.collection.mutable

/**
 * OC 自有配方的注册入口（对应 1.7.10 的 `common.recipe.Recipes`）。
 *
 * 1.21.1 迁移说明（重要）：
 *  - 原实现在**运行期**解析 `user.recipes` / `default.recipes`（HOCON），再调用
 *    `GameRegistry.addRecipe` 逐条注册配方。1.21.1 的配方全部改为**数据驱动 JSON**
 *    （`data/opencomputers_neo/recipe/` 下的 json 文件），因此本文件里
 *    `parseIngredient` / `parseFluidIngredient` / `findItem` / `findBlock` /
 *    `getObjectWithoutFallback` / `tryGetType` / `tryGetId` / `validateBlockId` /
 *    `validateItemId` / `hide` 以及 `init()` 里那一整套 HOCON 解析与
 *    `OreDictionary` / `GameRegistry.addRecipe` 调用**已经全部删除**；
 *  - 矿辞（OreDictionary）在 1.21.1 由物品/方块 tag 取代（`c:` 命名空间），
 *    也不需要运行期注册；
 *  - 保留下来的只有「注册入口」这一层 API 表面，供尚未移植/尚未改写的调用方继续编译
 *    （例如 `common/Proxy.scala` 的 `tryRegisterNugget`）。它们现在只是占位实现。
 *
 * TODO(recipe): `.recipes` → JSON 的批量转换由另一项工作负责。转换完成后：
 *  1. `list` / `oreDictEntries` / `recipeHandlers` / `registerRecipeHandler` /
 *     `addBlock` / `addSubItem` / `addItem` / `addStack` / `addRecipe` 可整体删除；
 *  2. `hadErrors` **必须保留**——`common/EventHandler.scala` 用它决定是否向玩家提示
 *     「配方有误」，转换后请改为在数据包加载/校验失败时置位；
 *  3. `common/Proxy.scala` 的 `tryRegisterNugget` 会跟着一起删掉。
 */
object Recipes {
  /** 原「名称 → 输出堆叠」表；HOCON 解析删除后不再被消费。 */
  val list = mutable.LinkedHashMap.empty[ItemStack, String]

  /** 原「矿辞名 → 代表堆叠」表；1.21.1 用 tag 取代矿辞，不再被消费。 */
  val oreDictEntries = mutable.LinkedHashMap.empty[String, ItemStack]

  /** 是否有配方注册失败（用于给玩家提示，见 `EventHandler.playerLoggedIn`）。 */
  var hadErrors = false

  /**
   * 原「配方类型名 → 处理函数」表。
   *
   * 保留类型签名（第二个参数仍是 `(ItemStack, Config) => Unit`）是为了让尚未改写的
   * `integration/opencomputers` 注册代码仍能编译；HOCON 解析已删除，因此这里注册进去的
   * 处理函数不会再被调用。
   */
  val recipeHandlers = mutable.LinkedHashMap.empty[String, (ItemStack, Config) => Unit]

  // ----------------------------------------------------------------------- //
  // 注册入口（占位）
  // ----------------------------------------------------------------------- //

  /** TODO(recipe): 原为 HOCON 配方处理器的注册点，随 HOCON 解析一起废弃。 */
  def registerRecipeHandler(name: String, recipe: (ItemStack, Config) => Unit): Unit = {
    recipeHandlers += name -> recipe
  }

  /** TODO(recipe): 1.21.1 的方块注册在 `common.init.Registry.Blocks`，配方走数据包 JSON。 */
  def addBlock(instance: Block, name: String, oreDict: String*): Block = {
    OpenComputers.log.debug(s"Recipes.addBlock('$name') is a no-op in 1.21.1; blocks are registered by Registry.Blocks and recipes are data-driven JSON.")
    instance
  }

  /** TODO(recipe): 见 [[addBlock]]。 */
  def addSubItem[T <: Delegate](delegate: T, name: String, oreDict: String*): T = {
    OpenComputers.log.debug(s"Recipes.addSubItem('$name') is a no-op in 1.21.1; items are registered by Registry.Items and recipes are data-driven JSON.")
    delegate
  }

  /** TODO(recipe): 见 [[addBlock]]。 */
  def addItem(instance: Item, name: String, oreDict: String*): Item = {
    OpenComputers.log.debug(s"Recipes.addItem('$name') is a no-op in 1.21.1; items are registered by Registry.Items and recipes are data-driven JSON.")
    instance
  }

  /** TODO(recipe): 见 [[addBlock]]。 */
  def addStack(stack: ItemStack, name: String, oreDict: String*): ItemStack = {
    list += stack -> name
    stack
  }

  /**
   * 原「登记一个待解析配方的输出」入口。
   *
   * 1.21.1 不再有「先登记、后统一解析」这一步，保留它只为兼容调用方。
   */
  def addRecipe(stack: ItemStack, name: String): Unit = {
    list += stack -> name
  }

  /**
   * 原「统一注册全部配方」入口。
   *
   * TODO(recipe): 数据包 JSON 化之后这里不再需要做任何事；如果你看到这条日志，
   * 说明还有代码指望 OC 在运行期注册配方，请把它改成 `data/opencomputers_neo/recipe/` 下的 JSON。
   */
  def init(): Unit = {
    OpenComputers.log.debug("Recipes.init() is a no-op in 1.21.1; OC recipes are data-driven JSON under data/opencomputers_neo/recipe/.")
  }

  /** 原配方解析异常；HOCON 解析删除后仅作为兼容类型保留。 */
  class RecipeException(message: String) extends RuntimeException(message)

}
