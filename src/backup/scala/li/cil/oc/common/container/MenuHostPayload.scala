package li.cil.oc.common.container

import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.network.{FriendlyByteBuf, RegistryFriendlyByteBuf}
import net.minecraft.world.item.ItemStack

/**
 * 「打开界面」包里承载宿主信息的统一载荷。
 *
 * ==为什么需要它==
 * 1.21.1 打开容器的链路被拆成两半：
 *  - 服务端 `player.openMenu(MenuProvider)`；
 *  - 客户端收到原版的 `ClientboundOpenScreenPacket` 后调用
 *    `MenuType#create(windowId, inventory, payload)` 重建容器。
 *
 * 原版只保证把 `MenuType` + 窗口 id 送过去，**业务数据**必须由
 * `MenuProvider#writeClientSideData(Player, FriendlyByteBuf)` 自己写、由
 * [[net.neoforged.neoforge.network.IContainerFactory]] 自己读。这里把「宿主是什么」
 * 统一编码成一个字节的操作码 + 变长参数，写和读严格配对放在同一个文件里，
 * 避免两边格式漂移。
 *
 * ==操作码==
 *  - [[Block]]：方块实体宿主，载荷是方块坐标。绝大多数 OC 机器走这条。
 *  - [[Entity]]：实体宿主（无人机），载荷是实体 id。
 *  - [[RackSlot]]：机架里的可插拔组件，载荷是机架坐标 + 槽位号。
 *  - [[ItemInHand]]：主手物品宿主（数据库升级 / 服务器 / 软盘驱动器 / 平板），
 *    载荷是物品堆叠本身。
 *
 * ==与 1.7.10 的对照==
 * 1.7.10 里 `IGuiHandler#getClientGuiElement` 能直接拿到 `world / x / y / z`，
 * 因为坐标是原版 `S3FPacketCustomPayload` 的一部分；这里改成自己写。
 */
object MenuHostPayload {
  /** 方块实体宿主。 */
  final val Block = 0

  /** 实体宿主。 */
  final val Entity = 1

  /** 机架插槽里的可插拔组件。 */
  final val RackSlot = 2

  /** 玩家主手里的物品。 */
  final val ItemInHand = 3

  /** 读不出任何宿主时（例如容器在客户端已失效）写这个值。 */
  final val Invalid = -1

  // ----------------------------------------------------------------------- //
  // 写
  // ----------------------------------------------------------------------- //

  /** 写方块实体宿主。 */
  def writeBlock(buf: FriendlyByteBuf, pos: BlockPos): Unit = {
    buf.writeByte(Block)
    buf.writeBlockPos(pos)
  }

  /** 写实体宿主。 */
  def writeEntity(buf: FriendlyByteBuf, entityId: Int): Unit = {
    buf.writeByte(Entity)
    buf.writeVarInt(entityId)
  }

  /** 写机架插槽宿主。 */
  def writeRackSlot(buf: FriendlyByteBuf, pos: BlockPos, slot: Int): Unit = {
    buf.writeByte(RackSlot)
    buf.writeBlockPos(pos)
    buf.writeVarInt(slot)
  }

  /**
   * 写主手物品宿主。
   *
   * 1.21.1 的 `ItemStack` 不能在原始 `FriendlyByteBuf` 上直接写（需要
   * `RegistryAccess` 才能解析数据组件），所以这里折中成手工写 NBT：
   * `count` + `ItemStack#save` 的 `CompoundTag`（`RegistryFriendlyByteBuf`
   * 提供的 `registryAccess()` 用于序列化数据组件）。
   */
  def writeItemInHand(buf: RegistryFriendlyByteBuf, stack: ItemStack): Unit = {
    buf.writeByte(ItemInHand)
    writeItem(buf, stack)
  }

  /** 只写物品堆叠本体（不写操作码），供 [[writeItemInHand]] 复用。 */
  def writeItem(buf: RegistryFriendlyByteBuf, stack: ItemStack): Unit = {
    if (stack == null || stack.isEmpty) {
      buf.writeVarInt(0)
      buf.writeNbt(new CompoundTag())
    }
    else {
      buf.writeVarInt(stack.getCount)
      buf.writeNbt(stack.save(buf.registryAccess()))
    }
  }

  /** 写「没有宿主」，让客户端工厂干净地失败而不是读到垃圾数据。 */
  def writeInvalid(buf: FriendlyByteBuf): Unit = buf.writeByte(Invalid)

  // ----------------------------------------------------------------------- //
  // 读
  // ----------------------------------------------------------------------- //

  /**
   * 读操作码；读到 [[Invalid]]（或流已耗尽）时返回 `None`。
   *
   * 注意：`FriendlyByteBuf#readByte` 在流耗尽时抛 `IndexOutOfBoundsException`，
   * 这里一并吞掉，转成 `None` —— 客户端多读一个字节总比崩溃好。
   */
  def readOp(buf: FriendlyByteBuf): Option[Int] = {
    if (buf.readableBytes() <= 0) None
    else {
      val op = try buf.readByte().toInt catch { case _: Throwable => Invalid }
      if (op == Invalid) None else Some(op)
    }
  }

  /** 读方块坐标。 */
  def readBlockPos(buf: FriendlyByteBuf): BlockPos = buf.readBlockPos()

  /** 读实体 id。 */
  def readEntityId(buf: FriendlyByteBuf): Int = buf.readVarInt()

  /** 读机架槽位。 */
  def readSlot(buf: FriendlyByteBuf): Int = buf.readVarInt()

  /**
   * 读物品堆叠；失败时返回 [[net.minecraft.world.item.ItemStack#EMPTY]]。
   *
   * `ItemStack#save` 的对称操作是 `ItemStack#parseOptional(RegistryAccess, Tag)`，
   * 返回的是 `Optional[ItemStack]`。
   */
  def readItem(buf: RegistryFriendlyByteBuf): ItemStack = {
    val count = buf.readVarInt()
    val tag = buf.readNbt()
    if (count <= 0 || tag == null || tag.isEmpty) ItemStack.EMPTY
    else {
      val parsed = ItemStack.parse(buf.registryAccess(), tag)
      if (parsed.isPresent) {
        val stack = parsed.get()
        stack.setCount(count)
        stack
      }
      else ItemStack.EMPTY
    }
  }

  /** 判断一个 NBT 是不是「空」（1.21.1 的 `CompoundTag#isEmpty`）。 */
  def isEmptyTag(tag: Tag): Boolean = tag == null || (tag match {
    case compound: CompoundTag => compound.isEmpty
    case _ => false
  })

  /** 便捷：从任意 [[RegistryAccess]] 造的缓冲区里读物品（供工具方法使用）。 */
  def itemFrom(access: RegistryAccess, tag: CompoundTag): ItemStack =
    if (tag == null || tag.isEmpty) ItemStack.EMPTY
    else {
      val parsed = ItemStack.parse(access, tag)
      if (parsed.isPresent) parsed.get() else ItemStack.EMPTY
    }
}
