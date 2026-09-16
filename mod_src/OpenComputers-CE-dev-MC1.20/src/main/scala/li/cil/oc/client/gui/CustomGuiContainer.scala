package li.cil.oc.client.gui

import java.util
import com.mojang.blaze3d.systems.RenderSystem
import li.cil.oc.client.gui.widget.WidgetContainer
import li.cil.oc.util.RenderState
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.locale.Language
import com.mojang.blaze3d.vertex.Tesselator

import scala.jdk.CollectionConverters._

abstract class CustomGuiContainer[C <: AbstractContainerMenu](val inventoryContainer: C, inv: Inventory, title: Component)
  extends AbstractContainerScreen[C](inventoryContainer, inv, title) with WidgetContainer {

  override def windowX: Int = leftPos

  override def windowY: Int = topPos

  override def windowZ: Float = 0f

  override def isPauseScreen: Boolean = false

  protected def add[T](list: util.List[T], value: Any): Boolean = list.add(value.asInstanceOf[T])

  override def render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float): Unit = {
    this.renderBackground(guiGraphics)
    super.render(guiGraphics, mouseX, mouseY, partialTicks)
    this.renderTooltip(guiGraphics, mouseX, mouseY)
  }
}