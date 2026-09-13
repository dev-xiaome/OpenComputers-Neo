package li.cil.oc.common.item

import li.cil.oc.api
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 「扳手」（原 `li.cil.oc.common.item.Wrench`）。
 *
 * 1.21.1 迁移要点：
 *  - 移除了 `@Injectable.InterfaceList`（ASM coremod 注入第三方模组的扳手接口）。
 *    1.21.1 已无 coremod，且第三方模组集成整体不在本次移植范围，
 *    因此只保留 OC 自身的 [[li.cil.oc.api.internal.Wrench]] 实现与若干兼容方法
 *    （它们可能仍被旧调用点引用）。
 *  - `Item#setHarvestLevel` / `setMaxStackSize` 已删除：1.21.1 分别改由
 *    `Item.Properties#stacksTo` 与数据包标签（`mineable`）表达，堆叠上限在注册期指定。
 *  - `onItemUseFirst(stack, player, world, x, y, z, side, hitX, hitY, hitZ)` →
 *    [[traits.Delegate#onItemUseFirst]]（`BlockPosition` + `UseOnContext` 拆包）。
 *  - `Block#rotateBlock` / `Block#onNeighborBlockChange` 在 1.21.1 已不存在：
 *    前者由方块自身（`RotatedPillarBlock` 等）+ `BlockState` 属性表达，
 *    后者改为 `BlockState#updateNeighbourShapes` / `Level#blockUpdated`。
 *  - `player.swingItem()` → `player.swing(hand)`；`world.isRemote` → `world.isClientSide`。
 */
class Wrench(props: Item.Properties) extends Item(props) with traits.SimpleItem with api.internal.Wrench {

  override def onItemUseFirst(stack: ItemStack, player: Player, position: li.cil.oc.util.BlockPosition,
                              side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    position.world match {
      case Some(world) =>
        val pos = position.toChunkCoordinates
        // TODO(方块): 1.7.10 的 `Block#rotateBlock(world, x, y, z, side)` 在 1.21.1 已删除，
        // 需要在 `common/block` 移植后由 OC 自己的方块（实现旋转接口）提供旋转入口。
        // 目前仅做「能否构建 / 是否已加载」的判断，避免误触发。
        world.hasChunkAt(pos) && player.mayBuild
      case _ => false
    }
  }

  override def useWrenchOnBlock(player: Player, world: Level, x: Int, y: Int, z: Int, simulate: Boolean): Boolean = {
    if (!simulate) player.swing(InteractionHand.MAIN_HAND)
    true
  }

  // ----------------------------------------------------------------------- //
  // 以下为原文件里为第三方模组预留的兼容方法。1.21.1 不再注入这些接口，
  // 但部分旧调用点仍可能调用，故保留为普通方法（不引入任何第三方 import）。
  // ----------------------------------------------------------------------- //

  /** Applied Energistics 2。 */
  def canWrench(stack: ItemStack, player: Player, x: Int, y: Int, z: Int): Boolean = true

  /** BluePower。 */
  def damage(stack: ItemStack, damage: Int, player: Player, simulated: Boolean): Boolean = damage == 0

  /** BuildCraft。 */
  def canWrench(player: Player, x: Int, y: Int, z: Int): Boolean = true

  def wrenchUsed(player: Player, x: Int, y: Int, z: Int): Unit = player.swing(InteractionHand.MAIN_HAND)

  def canWrench(player: Player, entity: Entity): Boolean = true

  def wrenchUsed(player: Player, entity: Entity): Unit = player.swing(InteractionHand.MAIN_HAND)

  /** CoFH。 */
  def isUsable(stack: ItemStack, player: LivingEntity, x: Int, y: Int, z: Int): Boolean = true

  def toolUsed(stack: ItemStack, player: LivingEntity, x: Int, y: Int, z: Int): Unit = ()

  /** EnderIO。 */
  def canUse(stack: ItemStack, player: Player, x: Int, y: Int, z: Int): Boolean = true

  def used(stack: ItemStack, player: Player, x: Int, y: Int, z: Int): Unit = ()

  /** Mekanism。 */
  def canUseWrench(player: Player, x: Int, y: Int, z: Int): Boolean = true

  /** Project Red。 */
  def canUse(entityPlayer: Player, itemStack: ItemStack): Boolean = true

  /** 旧版 Project Red 螺丝刀。 */
  def damageScrewdriver(world: Level, player: Player): Unit = ()

  /** Project Red v4.7+ 螺丝刀。 */
  def damageScrewdriver(player: Player, stack: ItemStack): Unit = ()

  /** Railcraft：`EntityMinecart` 在 1.21.1 是 `AbstractMinecart`，这里改用通用 `Entity`。 */
  def canWhack(player: Player, stack: ItemStack, x: Int, y: Int, z: Int): Boolean = true

  def onWhack(player: Player, stack: ItemStack, x: Int, y: Int, z: Int): Unit = ()

  def canLink(player: Player, stack: ItemStack, cart: Entity): Boolean = false

  def onLink(player: Player, stack: ItemStack, cart: Entity): Unit = ()

  def canBoost(player: Player, stack: ItemStack, cart: Entity): Boolean = false

  def onBoost(player: Player, stack: ItemStack, cart: Entity): Unit = ()

  /** IndustrialCraft 2。 */
  def canBeStoredInToolbox(stack: ItemStack): Boolean = true
}
