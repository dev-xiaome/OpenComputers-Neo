package li.cil.oc.integration.jei

import java.util
import com.mojang.blaze3d.platform.InputConstants
import li.cil.oc.{Localization, OpenComputers, Settings, api}
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.{IFocusGroup, RecipeIngredientRole, RecipeType}
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

import scala.collection.convert.ImplicitConversionsToJava._
import scala.collection.convert.ImplicitConversionsToScala._

object ManualUsageHandler {
  val RecipeType: RecipeType[ManualUsageRecipe] =
    mezz.jei.api.recipe.RecipeType.create(
      OpenComputers.ID,
      "manual_usage",
      classOf[ManualUsageRecipe]
    )

  def getRecipes(registration: IRecipeRegistration): util.List[ManualUsageRecipe] =
    registration.getIngredientManager.getAllItemStacks.collect {
      case stack: ItemStack => api.Manual.pathFor(stack) match {
        case s: String => Option(new ManualUsageRecipe(stack, s))
        case _ => None
      }
    }.flatten.toList

  class ManualUsageRecipe(val stack: ItemStack, val path: String)

  object ManualUsageRecipeCategory extends IRecipeCategory[ManualUsageRecipe] {
    val recipeWidth = 160
    val recipeHeight = 125
    private val buttonX = (recipeWidth - 100) / 2
    private val buttonY = 10
    private val buttonWidth = 100
    private val buttonHeight = 20
    private var icon: IDrawable = _

    def initialize(guiHelper: IGuiHelper): Unit = {
      icon = guiHelper.drawableBuilder(
        ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/item/manual.png"),
        0, 0, 16, 16
      ).setTextureSize(16, 16).build()
    }

    override def getRecipeType: RecipeType[ManualUsageRecipe] = RecipeType
    override def getTitle: Component = Component.literal("OpenComputers Manual")
    override def getIcon: IDrawable = icon
    override def getWidth: Int = recipeWidth
    override def getHeight: Int = recipeHeight

    override def setRecipe(builder: IRecipeLayoutBuilder, recipe: ManualUsageRecipe, focuses: IFocusGroup): Unit = {
      builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(recipe.stack)
    }

    override def draw(recipe: ManualUsageRecipe, slots: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double): Unit = {
      val hovered = isOverButton(mouseX, mouseY)
      val fill = if (hovered) 0xFFA0A0A0 else 0xFF707070
      graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, fill)
      graphics.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, 0xFF202020)
      val text = Localization.localizeLater("nei.usage.oc.Manual")
      val font = Minecraft.getInstance.font
      graphics.drawCenteredString(font, text, buttonX + buttonWidth / 2, buttonY + 6, 0xFFFFFF)
    }

    override def handleInput(recipe: ManualUsageRecipe, mouseX: Double, mouseY: Double, input: InputConstants.Key): Boolean = {
      if (input.getType == InputConstants.Type.MOUSE && input.getValue == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverButton(mouseX, mouseY)) {
        val minecraft = Minecraft.getInstance
        if (minecraft.player != null) {
          minecraft.player.closeContainer()
          api.Manual.openFor(minecraft.player)
          api.Manual.navigate(recipe.path)
          true
        }
        else false
      }
      else false
    }

    private def isOverButton(mouseX: Double, mouseY: Double): Boolean =
      mouseX >= buttonX && mouseX < buttonX + buttonWidth && mouseY >= buttonY && mouseY < buttonY + buttonHeight
  }
}
