package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item

/**
 * 「软盘」（原 `li.cil.oc.common.item.FloppyDisk`）。
 *
 * 1.21.1 迁移要点：
 *  - `registerIcons` / `icon(stack, pass)` 已删除：1.21.1 的贴图由模型 JSON + `ItemColor`
 *    决定，彩色软盘按 `oc:color` NBT 通过 `RegisterColorHandlersEvent.Item` 染色
 *    （客户端阶段，见 docs/PORTING.md）。因此这里不再持有 16 个 `IIcon`。
 *  - `doesSneakBypassUse` 已删除（1.21.1 由方块侧决定）。
 *  - `unlocalizedName` 固定为 `"FloppyDisk"`：战利品磁盘用匿名子类复用同一套语言条目，
 *    1.21.1 下这些子类都注册在 `floppy` / `lootDisk` / `openos` 等不同注册名上。
 */
class FloppyDisk(props: Item.Properties) extends Item(props) with traits.Delegate with traits.FileSystemLike {

  // Necessary for anonymous subclasses used for loot disks.
  override def unlocalizedName: String = "FloppyDisk"

  override val kiloBytes: Int = Settings.get.floppySize
}
