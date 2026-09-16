package li.cil.oc.common.blockentity

import li.cil.oc.api
import li.cil.oc.api.network.Visibility
import li.cil.oc.Constants
import li.cil.oc.client.renderer.block.CableModel
import li.cil.oc.util.{Color, ItemColorizer}
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.{DyeColor, ItemStack}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.api.distmarker.{Dist, OnlyIn}
import net.minecraftforge.client.model.data.ModelData

class Cable(pos: BlockPos, state: BlockState) extends BlockEntity(BlockEntityTypes.CABLE.get(), pos, state) with traits.Environment with traits.NotAnalyzable with traits.Colored {

  val node = api.Network
    .newNode(this, Visibility.None)
    .create()

  private var connectionColorInitialized = false

  setColor(Color.rgbValues(DyeColor.LIGHT_GRAY))

  def isConnectionColorInitialized: Boolean =
    connectionColorInitialized

  @OnlyIn(Dist.CLIENT)
  override def getModelData: ModelData =
    ModelData.builder()
      .`with`(CableModel.CABLE_PROPERTY, this)
      .build()

  def createItemStack(): ItemStack = {
    val stack = api.Items.get(Constants.BlockName.Cable).createItemStack(1)

    if (getColor != Color.rgbValues(DyeColor.LIGHT_GRAY)) {
      ItemColorizer.setColor(stack, getColor)
    }

    stack
  }

  def fromItemStack(stack: ItemStack): Unit = {
    val color =
      if (ItemColorizer.hasColor(stack)) {
        ItemColorizer.getColor(stack)
      }
      else {
        Color.rgbValues(DyeColor.LIGHT_GRAY)
      }

    setColor(color)
    connectionColorInitialized = true
    setChanged()
  }

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    connectionColorInitialized = true
  }

  override def controlsConnectivity: Boolean = true

  override def consumesDye: Boolean = true

  override protected def onColorChanged(): Unit = {
    super.onColorChanged()

    if (getLevel != null && isServer) {
      api.Network.joinOrCreateNetwork(this)
    }
  }
}