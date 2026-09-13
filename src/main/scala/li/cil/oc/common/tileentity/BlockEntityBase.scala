package li.cil.oc.common.tileentity

import li.cil.oc.OpenComputersNeo
import li.cil.oc.common.tileentity.traits.TileEntity
import net.minecraft.core.{BlockPos, HolderLookup}
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.{ClientGamePacketListener, ClientboundBlockEntityDataPacket}
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.block.state.BlockState

/**
 * OpenComputers 方块实体的公共基类。
 *
 * 1.7.10 的 `traits.TileEntity` 是一个直接继承 `net.minecraft.tileentity.TileEntity`
 * 的 trait；1.21.1 的 `BlockEntity` 需要构造参数，Scala trait 无法把它们传给父类，
 * 因此拆成：
 *
 *  - [[li.cil.oc.common.tileentity.traits.TileEntity]]：API 表面 + 可覆写钩子（自类型 `BlockEntity`）
 *  - 本类：真正的 `BlockEntity` 子类，把 1.21.1 的生命周期/存档回调转发到钩子
 *
 * 具体方块实体只需：
 * {{{
 *   class Adapter(pos: BlockPos, state: BlockState)
 *     extends BlockEntityBase(Registry.getBlockEntityType(Constants.BlockName.Adapter), pos, state)
 *       with traits.Environment with traits.ComponentInventory
 * }}}
 *
 * 注意：`dispose()` 在区块卸载时会被 `onChunkUnloaded()` 与 `setRemoved()` 各调用一次
 * （与 1.7.10 的 `onChunkUnload()` + `invalidate()` 行为一致），实现必须幂等。
 */
abstract class BlockEntityBase(beType: BlockEntityType[_], pos: BlockPos, state: BlockState)
  extends BlockEntity(beType, pos, state) with TileEntity {

  /** 等价于 1.7.10 的 `validate()`：方块实体加入世界（或区块载入）时调用一次。 */
  override def onLoad(): Unit = {
    super.onLoad()
    initialize()
  }

  /** 等价于 1.7.10 的 `invalidate()`。 */
  override def setRemoved(): Unit = {
    super.setRemoved()
    dispose()
  }

  override def onChunkUnloaded(): Unit = {
    super.onChunkUnloaded()
    dispose()
  }

  // ----------------------------------------------------------------------- //
  // 存档 / 同步
  // ----------------------------------------------------------------------- //

  override def loadAdditional(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadAdditional(nbt, provider)
    // 服务端从磁盘读档走 writeToNBTForServer 的对应分支；客户端收到同步包走客户端分支。
    if (isServer) {
      readFromNBTForServer(nbt)
    }
    else {
      readFromNBTForClient(nbt)
    }
  }

  override def saveAdditional(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveAdditional(nbt, provider)
    if (isServer) {
      writeToNBTForServer(nbt)
    }
  }

  /**
   * 方块实体同步包（等价于 1.7.10 的 `getDescriptionPacket`）。
   *
   * 与 1.7.10 一致：只写入客户端需要的数据，空标签不发送（返回 `null` 会让
   * NeoForge 跳过本次同步）。
   */
  override def getUpdateTag(provider: HolderLookup.Provider): CompoundTag = {
    val nbt = new CompoundTag()
    // See comment on BlockEntityBase.savingForClients.
    val previous = BlockEntityBase.savingForClients
    BlockEntityBase.savingForClients = true
    try {
      try writeToNBTForClient(nbt) catch {
        case e: Throwable =>
          OpenComputersNeo.log.warn("There was a problem writing a block entity update tag. Please report this if you see it!", e)
      }
      nbt
    } finally {
      BlockEntityBase.savingForClients = previous
    }
  }

  override def getUpdatePacket(): Packet[ClientGamePacketListener] =
    ClientboundBlockEntityDataPacket.create(this)

}

object BlockEntityBase {

  /**
   * 由方块反查它对应的方块实体类型。
   *
   * 1.21.1 的 `BlockEntity` 构造需要自己的 `BlockEntityType`，而 OC 的方块实体构造函数
   * 只接收 `(pos, state)`；注册层按「一个方块一个方块实体类型、类型名 = 方块注册名」登记
   * （见 `Registry.Blocks.initBlocks()`），因此这里用方块的注册路径反查即可，
   * 不需要在每个方块实体里硬编码名字。
   */
  def typeOf(block: net.minecraft.world.level.block.Block): BlockEntityType[_] =
    if (block == null) null
    else {
      val key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block)
      if (key == null) null else li.cil.oc.common.init.Registry.getBlockEntityType(key.getPath)
    }

  /**
   * 是否正在为「客户端同步包」写出 NBT。
   *
   * TODO(server.component): 对应原 `li.cil.oc.common.SaveHandler.savingForClients`。
   * 原字段定义在尚未移植的 `common/SaveHandler.scala` 里，且只有 `server.component.FileSystem`
   * 与 `server.machine.Machine` 使用；移植这两个包时请改为读写本字段。
   */
  var savingForClients = false
}
