package li.cil.oc.integration.vanilla

import li.cil.oc.OpenComputers

/**
 * 原版配方的注册入口。
 *
 * 1.7.10 在运行期解析 HOCON（`user.recipes` / `default.recipes`），再通过
 * `GameRegistry.addRecipe` / `FurnaceRecipes.smelting` 手工注册 `shaped` / `shapeless` /
 * `furnace` 三类配方（依赖 `OreDictionary`、`CraftingManager`、`ShapedRecipes`）。
 *
 * 1.21.1 的配方系统完全改为**数据驱动**：配方是 `data/<namespace>/recipe/` 下的 json 文件，
 * 由 `RecipeManager` 在数据包加载时解析，没有任何「初始化阶段注册」的时机
 * （`FMLCommonSetupEvent` 早于数据包加载，且 `RecipeManager` 会被整份替换）。
 * 因此这里不再注册任何配方，只保留类名与 `init()` 入口，避免调用方编译不过。
 *
 * TODO(port): `shaped` / `shapeless` / `furnace` 三类配方需要转换到
 * `src/main/resources/data/opencomputers_neo/recipe/` 下的 JSON 文件（或由数据生成器产出）；
 * 矿辞（OreDictionary）改用 `c:` 命名空间的物品/方块 tag。转换完成后，
 * 本对象与 [[ModVanilla]] 里的 `RecipeHandler.init()` 调用都可以整体删除。
 */
object RecipeHandler {
  def init(): Unit = {
    OpenComputers.log.debug("RecipeHandler.init() is a no-op in 1.21.1; " +
      "vanilla-integration recipes (shaped/shapeless/furnace) must be data-driven JSON " +
      "under data/opencomputers_neo/recipe/.")
  }
}
