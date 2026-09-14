package li.cil.oc.server.driver

import com.google.common.base.Strings
import li.cil.oc.api.driver
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

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
   * ==1.21.1 移植要点==
   * 1.7.10 版依次尝试了三种来源，其中两种依赖已经不存在或不可靠的 API，这里按如下方式降级：
   *  1. `NamedBlock#preferredName` —— 不变，优先级最高。
   *  2. `world.getTileEntity` + `IInventory#getInventoryName` —— 1.21.1 没有 `IInventory`，
   *     也没有「方块实体的显示名」这种约定，**整体删除**。
   *  3. `world.getBlock` + `Item#getItemFromBlock` + `ItemStack#getUnlocalizedName` —— 1.21.1 改为
   *     `world.getBlockState(pos).getBlock` → `new ItemStack(block)`（`Block` 即 `ItemLike`），
   *     名字取 `Item#getDescriptionId`（`block.minecraft.xxx`），再去掉命名空间前缀。
   *  4. `BlockEntity.classToNameMap`（1.7.10 的类 → 注册名映射）—— 1.21.1 已移除，**整体删除**。
   *
   * 兜底仍是 `"component"`，与 1.7.10 行为一致。
   */
  private def tryGetName(world: Level, x: Int, y: Int, z: Int, environments: Seq[ManagedEnvironment]): String = {
    environments.collect {
      case named: NamedBlock => named
    }.sortBy(_.priority).lastOption match {
      case Some(named) => return named.preferredName
      case _ => // No preferred name.
    }
    try {
      val pos = new BlockPos(x, y, z)
      val state = world.getBlockState(pos)
      if (!state.isAir) {
        // 1.21.1：`world.getBlock(x, y, z)` → `getBlockState(pos).getBlock`；
        // `Item.getItemFromBlock(block)` → `block.asItem()` / `Block#getCloneItemStack`；
        // `ItemStack#getUnlocalizedName` → `Item#getDescriptionId`（形如 `block.minecraft.xxx`）。
        val stack = (try Option(state.getBlock.getCloneItemStack(world, pos, state)).getOrElse(ItemStack.EMPTY) catch {
          case _: Throwable => ItemStack.EMPTY
        }) match {
          case s if s.isEmpty => new ItemStack(state.getBlock)
          case s => s
        }
        if (!stack.isEmpty) {
          val name = stack.getItem.getDescriptionId
          if (!Strings.isNullOrEmpty(name)) {
            return name.stripPrefix("block.").stripPrefix("item.")
          }
        }
      }
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
