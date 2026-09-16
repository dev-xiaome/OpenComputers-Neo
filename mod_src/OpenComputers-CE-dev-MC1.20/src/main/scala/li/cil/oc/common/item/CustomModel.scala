package li.cil.oc.common.item

import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraftforge.client.event.ModelEvent

trait CustomModel {
  @OnlyIn(Dist.CLIENT)
  def getModelLocation(stack: ItemStack): ModelResourceLocation

  @OnlyIn(Dist.CLIENT)
  def registerModelLocations(): Unit = {}

  @OnlyIn(Dist.CLIENT)
  def bakeModels(event: ModelEvent.RegisterAdditional): Unit = {}
}
