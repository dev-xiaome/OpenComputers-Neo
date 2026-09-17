package li.cil.oc.common.tileentity.traits

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.Node
import net.minecraft.nbt.CompoundTag

/**
 * 屏幕 / 机器人屏幕使用的文本缓冲区宿主
 * （对应 1.7.10 的 `common.tileentity.traits.TextBuffer`）。
 *
 * 1.21.1 迁移要点：
 *  - `updateEntity()` → [[TileEntity.tick]]（覆写时先调 `super.tick()`）。
 *  - `@SideOnly(Side.CLIENT)` 删除（NeoForge 的 `RuntimeDistCleaner` 会对类级
 *    `@OnlyIn` 直接抛异常）。
 *  - 缓冲区的创建方式与 1.7.10 **完全一致**：用屏幕物品查驱动，让驱动按宿主等级
 *    决定返回 [[li.cil.oc.common.component.TextBuffer]]（1 级屏幕）还是
 *    [[li.cil.oc.common.component.Screen]]（2/3 级屏幕，多出触摸模式回调），
 *    见 `integration/opencomputers/DriverScreen.scala`。
 *  - 等级表的取值加了下标钳制：1.21.1 每个等级是独立方块，`Screen#tier` 由方块反查，
 *    配置表被改短时不应数组越界。
 */
trait TextBuffer extends Environment {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  /** 屏幕等级（0 起），由具体方块实体提供。 */
  def tier: Int

  /** 等级在配置表中的下标（做钳制，避免越界）。 */
  private def tierIndex: Int = tier max 0 min (Settings.screenResolutionsByTier.length - 1)

  /** 缓冲区的最大分辨率（列, 行），取自配置的等级表。 */
  def maxResolution: (Int, Int) = Settings.screenResolutionsByTier(tierIndex)

  /** 缓冲区的最大颜色深度，取自配置的等级表。 */
  def maxColorDepth: api.internal.TextBuffer.ColorDepth =
    Settings.screenDepthsByTier(tier max 0 min (Settings.screenDepthsByTier.length - 1))

  /**
   * 文本缓冲区（真实实现，由物品驱动创建）。
   *
   * 与 1.7.10 一致：查 [[api.Driver.driverFor]]，把新建的环境当缓冲区用；
   * 驱动缺失时（理论上不会发生，`DriverScreen` 在 `ModOpenComputers` 里注册）
   * 兜底直接 new 一个，避免屏幕因空指针彻底不可用。
   */
  lazy val buffer: api.internal.TextBuffer = {
    val screenItem = api.Items.get(Constants.BlockName.ScreenTier1).createItemStack(1)
    val fromDriver = Option(api.Driver.driverFor(screenItem, getClass)).
      flatMap(driver => Option(driver.createEnvironment(screenItem, this))).
      collect { case textBuffer: api.internal.TextBuffer => textBuffer }
    val textBuffer = fromDriver.getOrElse(new li.cil.oc.common.component.TextBuffer(this))
    val (maxWidth, maxHeight) = maxResolution
    textBuffer.setMaximumResolution(maxWidth, maxHeight)
    textBuffer.setMaximumColorDepth(maxColorDepth)
    textBuffer
  }

  override def node: Node = buffer.node

  /** 该方块实体是否带真实屏幕（1.7.10 由 Screen 组件层判断）。 */
  def hasScreen: Boolean = true

  /** 缓冲区是否已通电（原 `buffer.getPowerState`）。 */
  def isActive: Boolean = buffer.getPowerState

  override def tick(): Unit = {
    super.tick()
    if (isClient || isConnected) {
      buffer.update()
    }
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    buffer.load(nbt)
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    buffer.save(nbt)
  }

  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    buffer.load(nbt)
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    buffer.save(nbt)
  }
}
