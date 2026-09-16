package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.level.LevelReader
import net.neoforged.neoforge.common.extensions.IItemExtension


class FloppyDisk(props: Properties) extends Item(props) with traits.SimpleItem with traits.FileSystemLike with IItemExtension {
  // Necessary for anonymous subclasses used for loot disks.
  unlocalizedName = "floppydisk"

  val kiloBytes = Settings.get.floppySize
  
  override def doesSneakBypassUse(stack: ItemStack, level: LevelReader, pos: BlockPos, player: Player): Boolean = true
}
