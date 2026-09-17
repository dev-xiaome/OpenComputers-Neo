package li.cil.oc.server.driver

import com.google.common.base.Strings
import li.cil.oc.api.driver
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

// TODO Remove blocks in OC 1.7.
class CompoundBlockDriver(val sidedBlocks: Array[driver.SidedBlock], val blocks: Array[driver.Block]) extends driver.SidedBlock {
  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction) = {
    val list = sidedBlocks.map {
      driver => Option(driver.createEnvironment(world, x, y, z, side)) match {
        case Some(environment) => (driver.getClass.getName, environment)
        case _ => null
      }
    } ++ blocks.map {
      driver => Option(driver.createEnvironment(world, x, y, z)) match {
        case Some(environment) => (driver.getClass.getName, environment)
        case _ => null
      }
    } filter (_ != null)
    if (list.isEmpty) null
    else new CompoundBlockEnvironment(cleanName(tryGetName(world, x, y, z, list.map(_._2))), list.toSeq: _*)
  }

  override def worksWith(world: Level, x: Int, y: Int, z: Int, side: Direction) = sidedBlocks.forall(_.worksWith(world, x, y, z, side)) && blocks.forall(_.worksWith(world, x, y, z))

  override def equals(obj: Any) = obj match {
    case multi: CompoundBlockDriver if multi.sidedBlocks.length == sidedBlocks.length && multi.blocks.length == blocks.length => sidedBlocks.intersect(multi.sidedBlocks).length == sidedBlocks.length && blocks.intersect(multi.blocks).length == blocks.length
    case _ => false
  }

  /**
   * 推导适配器（Adapter）旁那个方块对外的组件名。
   *
   * 与 OCCE 的 `tryGetName` 保持同样的优先级顺序（1.7.10 的顺序也一致）：
   *  1. `NamedBlock#preferredName` —— 优先级最高。
   *  2. 方块物品的本地化键（1.7.10 是 `ItemStack#getUnlocalizedName`，1.21.1 是
   *     `Item#getDescriptionId`，形如 `block.minecraft.xxx`）。
   *  3. 方块实体类型注册名（1.7.10 是 `TileEntity.classToNameMap`，1.20 是
   *     `ForgeRegistries.BLOCK_ENTITY_TYPES`，1.21.1 是 `BuiltInRegistries.BLOCK_ENTITY_TYPE`）。
   *     **不能省略这一步**：很多方块（例如各种机器的机壳）没有可用的物品名，
   *     只能靠方块实体类型名区分。
   *  4. 兜底 `"component"`，与 1.7.10 行为一致。
   *
   * 已删除的无对应 API：`IInventory#getInventoryName`（1.21.1 没有 `IInventory`，
   * 也没有「方块实体显示名」这种约定）。
   */
  private def tryGetName(world: Level, x: Int, y: Int, z: Int, environments: Seq[ManagedEnvironment]): String = {
    environments.collect {
      case named: NamedBlock => named
    }.sortBy(_.priority).lastOption match {
      case Some(named) => return named.preferredName
      case _ => // No preferred name.
    }
    val pos = new BlockPos(x, y, z)
    try {
      val state = world.getBlockState(pos)
      if (!state.isAir) {
        // 1.21.1：`world.getBlock(x, y, z)` → `getBlockState(pos).getBlock`；
        // `Item.getItemFromBlock(block)` → `new ItemStack(block)`（`Block` 即 `ItemLike`）；
        // `ItemStack#getUnlocalizedName` → `Item#getDescriptionId`（形如 `block.minecraft.xxx`）。
        val stack = new ItemStack(state.getBlock)
        if (!stack.isEmpty) {
          val name = stack.getItem.getDescriptionId
          if (!Strings.isNullOrEmpty(name)) {
            return name.stripPrefix("tile.").stripPrefix("block.").stripPrefix("item.")
          }
        }
      }
    } catch {
      case _: Throwable =>
    }
    try world.getBlockEntity(pos) match {
      case blockEntity: BlockEntity =>
        // 1.7.10 的 `TileEntity.classToNameMap` 已被移除，改为查方块实体类型注册表。
        val key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType)
        if (key != null) {
          return key.toString
        }
      case _ =>
    } catch {
      case _: Throwable =>
    }
    "component"
  }

  private def cleanName(name: String) = {
    val safeStart = if (name.matches( """^[^a-zA-Z_]""")) "_" + name else name
    val identifier = safeStart.replaceAll( """[^\w_]""", "_").trim
    if (Strings.isNullOrEmpty(identifier)) "component"
    else identifier.toLowerCase
  }
}
