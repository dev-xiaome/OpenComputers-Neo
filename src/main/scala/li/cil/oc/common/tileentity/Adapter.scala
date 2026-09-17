package li.cil.oc.common.tileentity

import li.cil.oc.server.{PacketSender => ServerPacketSender}
import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network._
import li.cil.oc.common.Slot
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.{CompoundTag, ListTag, Tag}
import net.minecraft.sounds.{SoundEvents, SoundSource}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 适配器（原 1.7.10 `common.tileentity.Adapter`）：把相邻方块的 `api.driver.SidedBlock`
 * 驱动暴露成组件，从而让计算机能访问非 OC 方块。
 *
 * 纹理：全部面 = Adapter（本方块为完整立方体，仅正面有一个插槽纹理）。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `IInventory#getSizeInventory` → `IItemHandler#getSlots`、`isItemValidForSlot` → `isItemValid`。
 *  - `ForgeDirection` → `Direction`（`Direction.VALID_DIRECTIONS` → `Direction.values()`，
 *    `side.offsetX/Y/Z` → `BlockPos#relative`）。
 *  - `world.getTileEntity(x, y, z)` → `world.getBlockEntity(pos)`。
 *  - `world.markBlockForUpdate` / `notifyBlocksOfNeighborChange` / `playSoundEffect` 按 §2 映射表替换。
 */
class Adapter(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.ComponentInventory with traits.OpenSides with Analyzable with internal.Adapter with DeviceInfo {

  val node = api.Network.newNode(this, Visibility.Network).create()

  private val blocks = Array.fill[Option[(ManagedEnvironment, api.driver.SidedBlock)]](6)(None)

  private val updatingBlocks = mutable.ArrayBuffer.empty[ManagedEnvironment]

  private val blocksData = Array.fill[Option[BlockData]](6)(None)

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Bus,
    DeviceAttribute.Description -> "Adapter",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Multiplug Ext.1"
  )

  // 1.7.10 的 `scala.collection.convert.WrapAsJava._` 提供隐式转换；
  // 1.21.1（Scala 2.13）改为显式 `.asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  override def defaultState = true

  override def setSideOpen(side: Direction, value: Boolean): Unit = {
    super.setSideOpen(side, value)
    if (isServer) {
      // 服务端发专用 AdapterState 包（对齐 OCCE），避免为开/关一面同步整个方块实体。
      ServerPacketSender.sendAdapterState(this)
      world.playSound(null, x + 0.5, y + 0.5, z + 0.5, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS,
        0.5f, world.random.nextFloat() * 0.25f + 0.7f)
      notifyNeighbors()
      neighborChanged(side)
    }
    else {
      markBlockForUpdate()
    }
  }

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    (blocks collect {
      case Some((environment, _)) => environment.node
    }) ++
      (componentEnvironments collect {
        case Some(environment) => environment.node
      })
  }

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = isServer

  override def tick(): Unit = {
    super.tick()
    if (updatingBlocks.nonEmpty) {
      for (block <- updatingBlocks) {
        block.update()
      }
    }
  }

  def neighborChanged(d: Direction): Unit = {
    if (node != null && node.network != null) {
      val neighborPos = blockPos.relative(d)
      world.getBlockEntity(neighborPos) match {
        case _: traits.Environment =>
        // Don't provide adaption for our stuffs. This is mostly to avoid
        // cables and other non-functional stuff popping up in the adapter
        // due to having a power interface. Might revisit this at some point,
        // but the only 'downside' is that it can't be used to manipulate
        // inventories, which I actually consider a plus :P
        case _ =>
          Option(api.Driver.driverFor(world, neighborPos.getX, neighborPos.getY, neighborPos.getZ, d)) match {
            case Some(newDriver) if isSideOpen(d) => blocks(d.ordinal()) match {
              case Some((oldEnvironment, driver)) =>
                if (newDriver != driver) {
                  // This is... odd. Maybe moved by some other mod? First, clean up.
                  blocks(d.ordinal()) = None
                  updatingBlocks -= oldEnvironment
                  blocksData(d.ordinal()) = None
                  node.disconnect(oldEnvironment.node)

                  // Then rebuild - if we have something.
                  val environment = newDriver.createEnvironment(world, neighborPos.getX, neighborPos.getY, neighborPos.getZ, d)
                  if (environment != null) {
                    blocks(d.ordinal()) = Some((environment, newDriver))
                    if (environment.canUpdate) {
                      updatingBlocks += environment
                    }
                    blocksData(d.ordinal()) = Some(new BlockData(environment.getClass.getName, new CompoundTag()))
                    node.connect(environment.node)
                  }
                } // else: the more things change, the more they stay the same.
              case _ =>
                if (!isSideOpen(d)) {
                  return
                }
                // A challenger appears. Maybe.
                val environment = newDriver.createEnvironment(world, neighborPos.getX, neighborPos.getY, neighborPos.getZ, d)
                if (environment != null) {
                  blocks(d.ordinal()) = Some((environment, newDriver))
                  if (environment.canUpdate) {
                    updatingBlocks += environment
                  }
                  blocksData(d.ordinal()) match {
                    case Some(data) if data.name == environment.getClass.getName =>
                      environment.load(data.data)
                    case _ =>
                  }
                  blocksData(d.ordinal()) = Some(new BlockData(environment.getClass.getName, new CompoundTag()))
                  node.connect(environment.node)
                }
            }
            case _ => blocks(d.ordinal()) match {
              case Some((environment, driver)) =>
                // We had something there, but it's gone now...
                node.disconnect(environment.node)
                environment.save(blocksData(d.ordinal()).get.data)
                Option(environment.node).foreach(_.remove())
                blocks(d.ordinal()) = None
                updatingBlocks -= environment
              case _ => // Nothing before, nothing now.
            }
          }
      }
    }
  }

  def neighborChanged(): Unit = {
    if (node != null && node.network != null) {
      for (d <- Direction.values()) {
        neighborChanged(d)
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      neighborChanged()
    }
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      updatingBlocks.clear()
    }
  }

  // ----------------------------------------------------------------------- //

  override def getSlots = 1

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = (slot, Option(Driver.driverFor(stack, getClass))) match {
    case (0, Some(driver)) => driver.slot(stack) == Slot.Upgrade
    case _ => false
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)

    val blocksNbt = nbt.getList(Settings.namespace + "adapter.blocks", Tag.TAG_COMPOUND)
    (0 until (blocksNbt.size() min blocksData.length)).
      map(blocksNbt.getCompound).
      zipWithIndex.
      foreach {
        case (blockNbt, i) =>
          if (blockNbt.contains("name") && blockNbt.contains("data")) {
            blocksData(i) = Some(new BlockData(blockNbt.getString("name"), blockNbt.getCompound("data")))
          }
      }
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)

    val blocksNbt = new ListTag()
    for (i <- blocks.indices) {
      val blockNbt = new CompoundTag()
      blocksData(i) match {
        case Some(data) =>
          blocks(i) match {
            case Some((environment, _)) => environment.save(data.data)
            case _ =>
          }
          blockNbt.putString("name", data.name)
          blockNbt.put("data", data.data)
        case _ =>
      }
      blocksNbt.add(blockNbt)
    }
    nbt.put(Settings.namespace + "adapter.blocks", blocksNbt)
  }

  // ----------------------------------------------------------------------- //

  private class BlockData(val name: String, val data: CompoundTag)

}
