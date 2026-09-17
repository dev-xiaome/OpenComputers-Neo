package li.cil.oc.common.recipe

import com.mojang.serialization.MapCodec
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Loot
import li.cil.oc.common.init.Registry
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.{CraftingBookCategory, CraftingInput, CustomRecipe, RecipeSerializer}
import net.minecraft.world.level.Level
import net.neoforged.neoforge.registries.DeferredHolder

import scala.jdk.CollectionConverters._

/**
 * 「用扳手把战利品软盘换一个」的自定义合成配方（原 1.7.10 的 `LootDiskCyclingRecipe`）。
 *
 * 1.21.1 迁移要点：
 *  - `IRecipe` → [[net.minecraft.world.item.crafting.CustomRecipe]]（从而 `getType()` 仍是
 *    `RecipeType.CRAFTING`，见 [[ColorizeRecipe]] 的说明）；
 *  - `getCraftingResult` → `assemble`，没有结果时返回 `ItemStack.EMPTY`（原为 `null`）；
 *  - 配方没有任何字段，因此序列化器用 `MapCodec.unit` / `StreamCodec.unit`：
 *    {{{
 *      { "type": "opencomputers_neo:lootcycler" }
 *    }}}
 */
class LootDiskCyclingRecipe extends CustomRecipe(CraftingBookCategory.MISC) {
  override def matches(input: CraftingInput, level: Level): Boolean = {
    val stacks = collectStacks(input)
    stacks.length == 2 && stacks.exists(Loot.isLootDisk) && stacks.exists(LootDiskCyclingRecipe.isWrench)
  }

  override def assemble(input: CraftingInput, registries: HolderLookup.Provider): ItemStack = {
    val lootDiskStacks = Loot.disksForCycling
    collectStacks(input).find(Loot.isLootDisk) match {
      case Some(lootDisk) if lootDiskStacks.nonEmpty =>
        val lootFactoryName = getLootFactoryName(lootDisk)
        val oldIndex = lootDiskStacks.indexWhere(s => getLootFactoryName(s) == lootFactoryName)
        val newIndex = (oldIndex + 1) % lootDiskStacks.length
        lootDiskStacks(newIndex).copy()
      case _ => ItemStack.EMPTY
    }
  }

  def getLootFactoryName(stack: ItemStack): String =
    if (stack != null && stack.hasTag()) stack.getTag().getString(Settings.namespace + "lootFactory") else ""

  override def canCraftInDimensions(width: Int, height: Int): Boolean = width * height >= 2

  override def getSerializer: RecipeSerializer[_] = LootDiskCyclingRecipe.SERIALIZER.get()

  private def collectStacks(input: CraftingInput): Seq[ItemStack] =
    input.items().asScala.filter(stack => stack != null && !stack.isEmpty).toSeq
}

object LootDiskCyclingRecipe {
  /** 配方序列化器（JSON 的 `type` 字段指向它；配方类型仍是 `RecipeType.CRAFTING`）。 */
  val SERIALIZER: DeferredHolder[RecipeSerializer[_], RecipeSerializer[_]] =
    Registry.registerRecipeSerializer[LootDiskCyclingRecipe]("lootcycler", () => new Serializer)

  /**
   * 是否是扳手。
   *
   * TODO(integration.util.Wrench): 原实现是 `integration.util.Wrench.isWrench(stack)`，
   * 它同时接受 OC 自带扳手与第三方通过 IMC（`registerWrenchToolCheck`）注册的判定。
   * `li.cil.oc.integration.util.Wrench` 尚未纳入编译集（`common/block/` 下的文件也是同样处理），
   * 这里先用 [[li.cil.oc.api.internal.Wrench]] 标记接口兜住 OC 自带扳手；
   * 等 integration 包移植完成后改为 `Wrench.isWrench(stack) || isOcWrench(stack)`。
   */
  def isWrench(stack: ItemStack): Boolean =
    stack != null && !stack.isEmpty && stack.getItem.isInstanceOf[api.internal.Wrench]

  class Serializer extends RecipeSerializer[LootDiskCyclingRecipe] {
    override def codec(): MapCodec[LootDiskCyclingRecipe] = MapCodec.unit(new LootDiskCyclingRecipe())

    override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, LootDiskCyclingRecipe] =
      StreamCodec.unit(new LootDiskCyclingRecipe())
  }

}
