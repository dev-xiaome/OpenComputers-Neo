package li.cil.oc.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Tier
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.{BuiltInRegistries, Registries}
import net.minecraft.world.item.Item
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.{CraftingInput, CraftingRecipe, Ingredient, Recipe, RecipeManager, RecipeType, ShapedRecipe, ShapelessRecipe}
import net.minecraft.nbt.{CompoundTag, NbtAccounter, NbtIo}
import net.minecraft.tags.BlockTags
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.block.state.BlockState
import org.jspecify.annotations.Nullable

import scala.collection.convert.ImplicitConversionsToScala._
import scala.collection.mutable

object ItemUtils {
  @Nullable
  def getTag(stack: ItemStack): CompoundTag = {
    stack.get(DataComponents.CUSTOM_DATA) match {
      case data: CustomData => data.copyTag()
      case _ => null
    }
  }

  def getOrCreateTag(stack: ItemStack): CompoundTag = {
    stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
  }

  def getDisplayName(nbt: CompoundTag): Option[String] = {
    if (nbt.contains("display")) {
      val displayNbt = nbt.getCompound("display")
      if (displayNbt.contains("Name"))
        return Option(displayNbt.getString("Name"))
    }
    None
  }

  def setDisplayName(nbt: CompoundTag, name: String): Unit = {
    if (!nbt.contains("display")) {
      nbt.put("display", new CompoundTag())
    }
    nbt.getCompound("display").putString("Name", name)
  }

  def getHarvestLevel(state: BlockState): Int = {
    if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) 3
    else if (state.is(BlockTags.NEEDS_IRON_TOOL)) 2
    else if (state.is(BlockTags.NEEDS_STONE_TOOL)) 1
    else 0
  }

  def getHarvestTool(state: BlockState): String = {
    if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) "pickaxe"
    else if (state.is(BlockTags.MINEABLE_WITH_AXE)) "axe"
    else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) "shovel"
    else if (state.is(BlockTags.MINEABLE_WITH_HOE)) "hoe"
    else null
  }

  def caseTier(stack: ItemStack): Int = {
    val descriptor = api.Items.get(stack)
    if (descriptor == api.Items.get(Constants.BlockName.CaseTier1)) Tier.One
    else if (descriptor == api.Items.get(Constants.BlockName.CaseTier2)) Tier.Two
    else if (descriptor == api.Items.get(Constants.BlockName.CaseTier3)) Tier.Three
    else if (descriptor == api.Items.get(Constants.BlockName.CaseTier4)) Tier.Four
    else if (descriptor == api.Items.get(Constants.BlockName.CaseCreative)) Tier.Five
    else if (descriptor == api.Items.get(Constants.ItemName.MicrocontrollerCaseTier1)) Tier.One
    else if (descriptor == api.Items.get(Constants.ItemName.MicrocontrollerCaseTier2)) Tier.Two
    else if (descriptor == api.Items.get(Constants.ItemName.MicrocontrollerCaseTier3)) Tier.Three
    else if (descriptor == api.Items.get(Constants.ItemName.MicrocontrollerCaseCreative)) Tier.Five
    else if (descriptor == api.Items.get(Constants.ItemName.DroneCaseTier1)) Tier.One
    else if (descriptor == api.Items.get(Constants.ItemName.DroneCaseTier2)) Tier.Two
    else if (descriptor == api.Items.get(Constants.ItemName.DroneCaseTier3)) Tier.Three
    else if (descriptor == api.Items.get(Constants.ItemName.DroneCaseCreative)) Tier.Five
    else if (descriptor == api.Items.get(Constants.ItemName.ServerTier1)) Tier.One
    else if (descriptor == api.Items.get(Constants.ItemName.ServerTier2)) Tier.Two
    else if (descriptor == api.Items.get(Constants.ItemName.ServerTier3)) Tier.Three
    else if (descriptor == api.Items.get(Constants.ItemName.ServerTier4)) Tier.Four
    else if (descriptor == api.Items.get(Constants.ItemName.ServerCreative)) Tier.Five
    else if (descriptor == api.Items.get(Constants.ItemName.TabletCaseTier1)) Tier.One
    else if (descriptor == api.Items.get(Constants.ItemName.TabletCaseTier2)) Tier.Two
    else if (descriptor == api.Items.get(Constants.ItemName.TabletCaseTier3)) Tier.Three
    else if (descriptor == api.Items.get(Constants.ItemName.TabletCaseCreative)) Tier.Five
    else Tier.None
  }

  def caseNameWithTierSuffix(name: String, tier: Int): String = name + (if (tier == Tier.Five) "creative" else (tier + 1).toString)

  def loadTag(data: Array[Byte]): CompoundTag = {
    val bais = new ByteArrayInputStream(data)
    NbtIo.readCompressed(bais, NbtAccounter.unlimitedHeap())
  }

  def saveStack(stack: ItemStack, provider: HolderLookup.Provider): Array[Byte] = {
    val tag = new CompoundTag()
    val provider = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer.registryAccess()
    stack.save(provider, tag)
    saveTag(tag)
  }

  def saveTag(tag: CompoundTag): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    NbtIo.writeCompressed(tag, baos)
    baos.toByteArray
  }

  def getIngredients(manager: RecipeManager, stack: ItemStack): Array[ItemStack] = try {
    def getFilteredInputs(inputs: Iterable[ItemStack], outputSize: Int) = (inputs.filter(input =>
      !input.isEmpty &&
        input.getCount / outputSize > 0 &&
        // Strip out buckets, because those are returned when crafting, and
        // we have no way of returning the fluid only (and I can't be arsed
        // to make it output fluids into fluiducts or such, sorry).
        !input.getItem.isInstanceOf[BucketItem]).toArray, outputSize)

    def getOutputSize(recipe: Recipe[?]) = recipe.getResultItem(net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer.registryAccess()).getCount

    def isInputBlacklisted(stack: ItemStack) = stack.getItem match {
      case item: BlockItem => Settings.get.disassemblerInputBlacklist.contains(BuiltInRegistries.BLOCK.getKey(item.getBlock))
      case item: Item => Settings.get.disassemblerInputBlacklist.contains(BuiltInRegistries.ITEM.getKey(item))
      case _ => false
    }

    val (ingredients, count) = manager.getAllRecipesFor[CraftingInput, CraftingRecipe](RecipeType.CRAFTING)
      .map(_.value)
      .filter(recipe => !recipe.getResultItem(null).isEmpty && ItemStack.isSameItem(recipe.getResultItem(null), stack))
      .collect {
        case recipe: ShapedRecipe => getFilteredInputs(resolveOreDictEntries(recipe.getIngredients), getOutputSize(recipe))
        case recipe: ShapelessRecipe => getFilteredInputs(resolveOreDictEntries(recipe.getIngredients), getOutputSize(recipe))
      }.collectFirst {
        case (inputs, outputSize) if !inputs.exists(isInputBlacklisted) => (inputs, outputSize)
      } match {
        case Some((inputs, outputSize)) => (inputs, outputSize)
        case _ => return Array.empty
      }

    // Avoid positive feedback loops.
    if (ingredients.exists(ingredient => ItemStack.isSameItem(ingredient, stack))) {
      return Array.empty[ItemStack]
    }
    // Merge equal items for size division by output size.
    val merged = mutable.ArrayBuffer.empty[ItemStack]
    for (ingredient <- ingredients) {
      merged.find(mergedStack => ItemStack.isSameItem(ingredient, mergedStack)) match {
        case Some(entry) => entry.grow(ingredient.getCount)
        case _ => merged += ingredient.copy()
      }
    }
    merged.foreach(s => s.setCount(s.getCount / count))
    // Split items up again to 'disassemble them individually'.
    val distinct = mutable.ArrayBuffer.empty[ItemStack]
    for (ingredient <- merged) {
      val size = ingredient.getCount max 1
      ingredient.setCount(1)
      for (i <- 0 until size) {
        distinct += ingredient.copy()
      }
    }
    distinct.toArray
  }
  catch {
    case t: Throwable =>
      OpenComputers.log.warn("Whoops, something went wrong when trying to figure out an item's parts.", t)
      Array.empty[ItemStack]
  }

  private lazy val rng = new Random()

  private def resolveOreDictEntries[T](entries: Iterable[Ingredient]) = entries.collect {
    case ing: Ingredient if ing.getItems.nonEmpty => ing.getItems()(rng.nextInt(ing.getItems.length))
  }

}
