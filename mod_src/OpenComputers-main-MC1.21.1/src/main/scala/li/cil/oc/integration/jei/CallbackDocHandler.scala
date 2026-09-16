package li.cil.oc.integration.jei

import java.util
import com.google.common.base.Strings
import li.cil.oc.{OpenComputers, Settings, api}
import li.cil.oc.server.machine.Callbacks
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.{IFocusGroup, RecipeIngredientRole, RecipeType}
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.{Component, Style}
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

import scala.collection.convert.ImplicitConversionsToJava._
import scala.collection.convert.ImplicitConversionsToScala._
import scala.collection.mutable

object CallbackDocHandler {
  private val DocPattern = """(?s)^function(\\(.*?\\).*?) -- (.*)$""".r
  private val VexPattern = """(?s)^function(\\(.*?\\).*?); (.*)$""".r
  val RecipeType: RecipeType[CallbackDocRecipe] =
    mezz.jei.api.recipe.RecipeType.create(
      OpenComputers.ID,
      "callback_doc",
      classOf[CallbackDocRecipe]
    )

  def getRecipes(registration: IRecipeRegistration): util.List[CallbackDocRecipe] =
    registration.getIngredientManager.getAllItemStacks.collect {
      case stack: ItemStack =>
        val callbacks = api.Driver.environmentsFor(stack).flatMap(getCallbacks).toBuffer
        if (callbacks.nonEmpty) {
          val pages = mutable.Buffer.empty[String]
          val lastPage = callbacks.toArray.sorted.foldLeft("") { (last, doc) =>
            if (last.linesIterator.length + 2 + doc.linesIterator.length > 12) {
              last.linesIterator.grouped(12).map(_.mkString("\n")).foreach(pages += _)
              doc
            }
            else if (last.nonEmpty) last + "\n\n" + doc
            else doc
          }
          lastPage.linesIterator.grouped(12).map(_.mkString("\n")).foreach(pages += _)
          Option(pages.map(page => new CallbackDocRecipe(stack, page)))
        }
        else None
    }.flatten.flatten.toList

  private def getCallbacks(env: Class[_]) = if (env != null) {
    Callbacks.fromClass(env).map { case (name, callback) =>
      val doc = callback.annotation.doc
      if (Strings.isNullOrEmpty(doc)) name
      else {
        val (signature, documentation) = doc match {
          case DocPattern(head, tail) => (name + head, tail)
          case VexPattern(head, tail) => (name + head, tail)
          case _ => (name, doc)
        }
        wrap(signature, 160).map(ChatFormatting.BLACK.toString + _).mkString("\n") +
          ChatFormatting.RESET + "\n" +
          wrap(documentation, 152).map("  " + _).mkString("\n")
      }
    }
  }
  else Seq.empty

  protected def wrap(line: String, width: Int): util.List[String] = {
    val list = new util.ArrayList[String]()
    Minecraft.getInstance.font.getSplitter.splitLines(line, width, Style.EMPTY, true, (_: Style, start: Int, end: Int) => list.add(line.substring(start, end)))
    list
  }

  class CallbackDocRecipe(val stack: ItemStack, val page: String)

  object CallbackDocRecipeCategory extends IRecipeCategory[CallbackDocRecipe] {
    val recipeWidth = 160
    val recipeHeight = 125
    private var icon: IDrawable = _

    def initialize(guiHelper: IGuiHelper): Unit = {
      icon = new DrawableAnimatedIcon(
        ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "textures/item/tablet_on.png"),
        0, 0, 16, 16, 16, 32,
        guiHelper.createTickTimer(20, 1, true), 0, 16
      )
    }

    override def getRecipeType: RecipeType[CallbackDocRecipe] = RecipeType
    override def getTitle: Component = Component.literal("OpenComputers API")
    override def getIcon: IDrawable = icon
    override def getWidth: Int = recipeWidth
    override def getHeight: Int = recipeHeight

    override def setRecipe(builder: IRecipeLayoutBuilder, recipe: CallbackDocRecipe, focuses: IFocusGroup): Unit = {
      builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(recipe.stack)
    }

    override def draw(recipe: CallbackDocRecipe, slots: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double): Unit = {
      val font = Minecraft.getInstance.font
      for ((text, line) <- recipe.page.linesIterator.zipWithIndex) {
        graphics.drawString(font, text, 4, 4 + line * (font.lineHeight + 1), 0x333333, false)
      }
    }
  }
}
