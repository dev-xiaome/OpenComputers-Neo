package li.cil.oc.common.item.data

import li.cil.oc.util.ItemStackNBTExtensions._
import li.cil.oc.{Constants, Settings}
import li.cil.oc.common.item.data.DebugCardData.RichAccessContext
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/**
 * Debug 卡数据（原 1.7.10 的 `DebugCardData`）。
 *
 * 1.21.1 迁移要点：
 *  - 原文件依赖 `li.cil.oc.server.component.DebugCard.AccessContext`（阶段 5 才移植）。
 *    但 `AccessContext` 只是 `(player: String, nonce: String)` 两个字段，且
 *    [[li.cil.oc.Settings.AccessContext]] 已经定义了同一个 case class，
 *    因此这里直接改用 [[li.cil.oc.Settings.AccessContext]]，`load` / `remove` 就地实现。
 *    `server.component.DebugCard` 移植时应删掉自己那份 `AccessContext`，改用 `Settings` 里的。
 */
class DebugCardData extends ItemData(Constants.ItemName.DebugCard) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var access: Option[Settings.AccessContext] = None

  override def load(nbt: CompoundTag): Unit = {
    access = DebugCardData.loadAccess(dataTag(nbt))
  }

  override def save(nbt: CompoundTag): Unit = {
    val tag = dataTag(nbt)
    DebugCardData.removeAccess(tag)
    access.foreach(_.save(tag))
  }

  private def dataTag(nbt: CompoundTag): CompoundTag = {
    if (!nbt.contains(Settings.namespace + "data")) {
      nbt.put(Settings.namespace + "data", new CompoundTag())
    }
    nbt.getCompound(Settings.namespace + "data")
  }
}

object DebugCardData {

  /** 等价于原 `server.component.DebugCard.AccessContext.load(nbt)`。 */
  def loadAccess(nbt: CompoundTag): Option[Settings.AccessContext] = {
    if (nbt.contains(Settings.namespace + "player")) {
      Some(Settings.AccessContext(
        nbt.getString(Settings.namespace + "player"),
        nbt.getString(Settings.namespace + "accessNonce")))
    }
    else None
  }

  /** 等价于原 `server.component.DebugCard.AccessContext.remove(nbt)`。 */
  def removeAccess(nbt: CompoundTag): Unit = {
    nbt.remove(Settings.namespace + "player")
    nbt.remove(Settings.namespace + "accessNonce")
  }

  /** 等价于原 `AccessContext#save(nbt)`（`Settings.AccessContext` 是纯 case class，没有方法）。 */
  implicit class RichAccessContext(private val ctx: Settings.AccessContext) extends AnyVal {
    def save(nbt: CompoundTag): Unit = {
      nbt.putString(Settings.namespace + "player", ctx.player)
      nbt.putString(Settings.namespace + "accessNonce", ctx.nonce)
    }
  }
}
