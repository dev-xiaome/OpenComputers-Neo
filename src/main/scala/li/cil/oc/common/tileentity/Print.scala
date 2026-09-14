package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import li.cil.oc.util.ExtendedAABB
import li.cil.oc.util.ExtendedAABB._
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.{SoundEvents, SoundSource}
import net.minecraft.world.level.block.state.BlockState

/**
 * 3D 打印件方块实体（原 1.7.10 `common.tileentity.Print`）。
 *
 * 打印件的形状（`data.stateOff` / `data.stateOn`）由 3D 打印机写入，本类只负责：
 *  - 保存 / 同步 [[data]] 与 [[state]]（激活状态）；
 *  - 由红石输入或玩家交互（[[activate]]）切换 [[state]]；
 *  - 按 [[li.cil.oc.common.tileentity.traits.Rotatable.facing]] 计算出用于渲染与碰撞的
 *    [[boundsOff]] / [[boundsOn]]。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）；
 *    原 `extends traits.TileEntity` 在 1.21.1 是 [[BlockEntityBase]]。
 *  - `world.playSoundEffect(x, y, z, "random.click", v, p)` → `Level#playSound` +
 *    [[net.minecraft.sounds.SoundEvents]]（`random.click` = 木质按钮点击音）。
 *  - `world.markBlockForUpdate(x, y, z)` → [[li.cil.oc.common.tileentity.traits.TileEntity#markBlockForUpdate]]。
 *  - `world.scheduleBlockUpdate(x, y, z, block, block.tickRate(world))` → `Level#scheduleTick`
 *    （1.21.1 的 `Block` 已没有 `tickRate`，见 [[buttonResetDelay]] 的 TODO）。
 *  - `AABB#func_111270_a`（并集）→ `AABB#minmax`。
 *  - `new java.lang.Integer(i)` 在 Java 9+ 已废弃 → `Integer.valueOf`。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）。
 */
class Print(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.RedstoneAware with traits.Rotatable {

  val data = new PrintData()

  var boundsOff = ExtendedAABB.unitBounds
  var boundsOn = ExtendedAABB.unitBounds
  var state = false

  /**
   * 按钮模式下自动复位所需的刻数。
   *
   * TODO(common.block.Print): 原为 `block.tickRate(world)`；1.21.1 的 `Block` 已移除
   * `tickRate`，`common.block.Print` 里约定为 20。方块层移植完成后请改为从方块读取，
   * 若方块侧改用 `BlockState` 属性描述该延迟，也在这里一并改掉。
   */
  private val buttonResetDelay = 20

  _isOutputEnabled = true

  def activate(): Boolean = {
    if (data.hasActiveState) {
      if (!state || !data.isButtonMode) {
        toggleState()
        return true
      }
    }
    false
  }

  private def buildValueSet(value: Int): util.Map[AnyRef, AnyRef] = {
    val map: util.Map[AnyRef, AnyRef] = new util.HashMap[AnyRef, AnyRef]()
    (0 until 6).foreach {
      side => map.put(Integer.valueOf(side), Integer.valueOf(value))
    }
    map
  }

  def toggleState(): Unit = {
    state = !state
    playClick()
    markBlockForUpdate()
    if (data.emitRedstoneWhenOn) {
      setOutput(buildValueSet(if (state) data.redstoneLevel else 0))
    }
    if (state && data.isButtonMode) {
      scheduleReset()
    }
  }

  override def canUpdate: Boolean = false

  private def playClick(): Unit =
    world.playSound(null, x + 0.5, y + 0.5, z + 0.5, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS,
      0.3F, if (state) 0.6F else 0.5F)

  /** 原 `world.scheduleBlockUpdate(x, y, z, block, block.tickRate(world))`。 */
  private def scheduleReset(): Unit =
    world.scheduleTick(blockPos, block, buttonResetDelay)

  override def onRedstoneInputChanged(args: RedstoneChangedEventArgs): Unit = {
    super.onRedstoneInputChanged(args)
    if (!data.emitRedstone && data.hasActiveState) {
      state = args.newValue > 0
      playClick()
      markBlockForUpdate()
      if (state && data.isButtonMode) {
        scheduleReset()
      }
    }
  }

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    data.load(nbt.getCompound("data"))
    state = nbt.getBoolean("state")
    updateBounds()
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.setNewCompoundTag("data", data.save)
    nbt.putBoolean("state", state)
  }

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解（客户端读同步标签时才会调用）。
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    data.load(nbt.getCompound("data"))
    state = nbt.getBoolean("state")
    updateBounds()
    markBlockForUpdate()
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.setNewCompoundTag("data", data.save)
    nbt.putBoolean("state", state)
  }

  def updateBounds(): Unit = {
    boundsOff = data.stateOff.drop(1).foldLeft(data.stateOff.headOption.fold(ExtendedAABB.unitBounds)(_.bounds))((a, b) => a.minmax(b.bounds))
    if (boundsOff.volume == 0) boundsOff = ExtendedAABB.unitBounds
    else boundsOff = boundsOff.rotateTowards(facing)
    boundsOn = data.stateOn.drop(1).foldLeft(data.stateOn.headOption.fold(ExtendedAABB.unitBounds)(_.bounds))((a, b) => a.minmax(b.bounds))
    if (boundsOn.volume == 0) boundsOn = ExtendedAABB.unitBounds
    else boundsOn = boundsOn.rotateTowards(facing)

    if (data.emitRedstoneWhenOff) {
      setOutput(buildValueSet(data.redstoneLevel))
    }
  }

  override def onRotationChanged(): Unit = {
    super.onRotationChanged()
    updateBounds()
  }
}
