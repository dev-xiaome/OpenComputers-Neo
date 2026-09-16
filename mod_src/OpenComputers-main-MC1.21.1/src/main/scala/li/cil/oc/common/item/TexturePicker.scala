package li.cil.oc.common.item

import li.cil.oc.Localization
import net.minecraft.client.Minecraft
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.InteractionResult
import net.neoforged.neoforge.client.model.data.ModelData
import net.neoforged.neoforge.common.extensions.IItemExtension

class TexturePicker(props: Properties) extends Item(props) with traits.SimpleItem with IItemExtension {
  override def useOn(ctx: UseOnContext): InteractionResult = {
    if (ctx.getLevel.isClientSide) {
      val pos = ctx.getClickedPos
      val model = Minecraft.getInstance.getBlockRenderer.getBlockModel(ctx.getLevel.getBlockState(ctx.getClickedPos))
      val be = ctx.getLevel.getBlockEntity(pos)
      val particle = model.getParticleIcon(if (be == null) ModelData.EMPTY else be.getModelData)
      if (ctx.getPlayer != null) {
        ctx.getPlayer.sendSystemMessage(Localization.Chat.TextureName(particle.contents.name.toString))
      }
    }

    InteractionResult.sidedSuccess(ctx.getLevel.isClientSide)
  }
}
