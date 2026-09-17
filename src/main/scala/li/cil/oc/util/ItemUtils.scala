package li.cil.oc.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Tier
import net.minecraft.world.level.block.Block
import net.minecraft.world.item.Item
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.CompoundTag
import net.minecraft.tags.BlockTags
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.registries.BuiltInRegistries

import scala.collection.convert.ImplicitConversionsToScala._
import scala.collection.mutable

object ItemUtils {
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
    // 1.21.1 的 NbtIo.readCompressed 需要显式的 NbtAccounter（解压配额），
    // 旧的单参重载已移除。
    NbtIo.readCompressed(bais, NbtAccounter.unlimitedHeap())
  }

  def saveStack(stack: ItemStack): Array[Byte] = {
    // 1.21.1 的 ItemStack#save 是「返回编码结果」而不是就地写入传入的 tag，
    // 且空堆叠会抛 IllegalStateException，因此用 saveOptional 并取返回值。
    val tag = stack.saveOptional(RegistryAccessHelper.getOrEmpty).asInstanceOf[CompoundTag]
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
        input.getCount > 0 &&
        // Strip out buckets, because those are returned when crafting, and
        // we have no way of returning the fluid only (and I can't be arsed
        // to make it output fluids into fluiducts or such, sorry).
        !input.getItem.isInstanceOf[BucketItem]).toArray, outputSize)

    def getOutputSize(recipe: Recipe[_]) = recipe.getResultItem(RegistryAccessHelper.getOrEmpty).getCount

    def isInputBlacklisted(stack: ItemStack) = stack.getItem match {
      case item: BlockItem => Settings.get.disassemblerInputBlacklist.contains(BuiltInRegistries.BLOCK.getKey(item.getBlock))
      case item: Item => Settings.get.disassemblerInputBlacklist.contains(BuiltInRegistries.ITEM.getKey(item))
      case _ => false
    }

    // 1.21.1：getAllRecipesFor 的签名变为 getAllRecipesFor(RecipeType[T]): List[RecipeHolder[T]]，
    // 返回的是 RecipeHolder，需要 .value() 取出配方本体；输入类型由 Recipe 自身推导。
    val matching = manager.getAllRecipesFor(RecipeType.CRAFTING).asScala.
      map(_.value()).
      filter(recipe => !recipe.getResultItem(RegistryAccessHelper.getOrEmpty).isEmpty &&
        ItemStack.isSameItem(recipe.getResultItem(RegistryAccessHelper.getOrEmpty), stack))

    val (ingredients, count) = matching.collect {
      case recipe: CraftingRecipe =>
        val outputSize = getOutputSize(recipe)
        val (inputs, _) = getFilteredInputs(resolveOreDictEntries(recipe.getIngredients), outputSize)
        (inputs, outputSize)
    }.collectFirst {
      case (inputs, outputSize) if inputs.nonEmpty && !inputs.exists(isInputBlacklisted) &&
        !inputs.exists(input => ItemStack.isSameItem(input, stack)) => (inputs, outputSize)
    } match {
      case Some((inputs, outputSize)) => (inputs, outputSize)
      case _ => return Array.empty
    }

    // Merge equal items for size division by output size.
    val merged = mutable.ArrayBuffer.empty[ItemStack]
    for (ingredient <- ingredients) {
      merged.find(mergedStack => ItemStack.isSameItem(ingredient, mergedStack)) match {
        case Some(entry) => entry.grow(ingredient.getCount)
        case _ => merged += ingredient.copy()
      }
    }
    merged.foreach { s =>
      val divided = s.getCount / count
      s.setCount(if (divided > 0) divided else 1)
    }
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
