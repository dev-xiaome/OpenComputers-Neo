package li.cil.oc.client

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.api.detail.ManualAPI
import li.cil.oc.api.manual.ContentProvider
import li.cil.oc.api.manual.ImageProvider
import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.api.manual.PathProvider
import li.cil.oc.api.manual.TabIconRenderer
import li.cil.oc.common.GuiType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

import scala.annotation.tailrec
import scala.collection.mutable

/**
 * 游戏内手册（`li.cil.oc.api.detail.ManualAPI` 的 1.21.1 客户端实现）。
 *
 * ==1.7.10 -> 1.21.1 映射==
 *  - `Minecraft.getMinecraft.currentScreen` -> `Minecraft.getInstance().screen`
 *  - `FMLCommonHandler.instance.getCurrentLanguage` -> 见 [[currentLanguage]]
 *  - `player.getEntityWorld.isRemote` -> `player.level().isClientSide`
 *  - `player.openGui(...)` -> 直接 `Minecraft#setScreen`（手册是**纯客户端**界面，
 *    没有容器，因此不需要 `MenuProvider` / `MenuType`）
 *
 * 手册内容本身（markdown、图片）仍旧来自资源包：
 * `assets/opencomputers_neo/doc/<语言>/...`，由 integration 层注册的
 * `PathProvider` / `ContentProvider` / `ImageProvider` 提供，本对象只做路由与
 * `%LANGUAGE%` 变量替换，这部分逻辑与 1.7.10 完全一致。
 */
object Manual extends ManualAPI {
  final val LanguageKey = "%LANGUAGE%"

  final val FallbackLanguage = "en_US"

  class History(val path: String, var offset: Int = 0)

  class Tab(val renderer: TabIconRenderer, val tooltip: Option[String], val path: String)

  val tabs = mutable.Buffer.empty[Tab]

  val pathProviders = mutable.Buffer.empty[PathProvider]

  val contentProviders = mutable.Buffer.empty[ContentProvider]

  val imageProviders = mutable.Buffer.empty[(String, ImageProvider)]

  val history = new mutable.Stack[History]

  reset()

  override def addTab(renderer: TabIconRenderer, tooltip: String, path: String): Unit = {
    tabs += new Tab(renderer, Option(tooltip), path)
    if (tabs.length > 7) {
      OpenComputers.log.warn("Gosh I'm popular! Too many tabs were added to the OpenComputers in-game manual, so some won't be shown. In case this actually happens, let me know and I'll look into making them scrollable or something...")
    }
  }

  override def addProvider(provider: PathProvider): Unit = {
    pathProviders += provider
  }

  override def addProvider(provider: ContentProvider): Unit = {
    contentProviders += provider
  }

  override def addProvider(prefix: String, provider: ImageProvider): Unit = {
    imageProviders += (if (Strings.isNullOrEmpty(prefix)) "" else prefix + ":") -> provider
  }

  override def pathFor(stack: ItemStack): String = {
    for (provider <- pathProviders) {
      val path = try provider.pathFor(stack) catch {
        case t: Throwable =>
          OpenComputers.log.warn("A path provider threw an error when queried with an item.", t)
          null
      }
      if (path != null) return path
    }
    null
  }

  override def pathFor(world: Level, x: Int, y: Int, z: Int): String = {
    for (provider <- pathProviders) {
      val path = try provider.pathFor(world, x, y, z) catch {
        case t: Throwable =>
          OpenComputers.log.warn("A path provider threw an error when queried with a block.", t)
          null
      }
      if (path != null) return path
    }
    null
  }

  override def contentFor(path: String): java.lang.Iterable[String] = {
    val cleanPath = com.google.common.io.Files.simplifyPath(path)
    val language = try {
      currentLanguage
    } catch {
      case t: Throwable =>
        OpenComputers.log.warn("The game threw an error when querying current language.", t)
        FallbackLanguage
    }
    contentForWithRedirects(cleanPath.replace(LanguageKey, language)).
      orElse(contentForWithRedirects(cleanPath.replace(LanguageKey, FallbackLanguage))).
      orNull
  }

  override def imageFor(href: String): ImageRenderer = {
    for ((prefix, provider) <- Manual.imageProviders.reverse) {
      if (href.startsWith(prefix)) {
        val image = try provider.getImage(href.stripPrefix(prefix)) catch {
          case t: Throwable =>
            OpenComputers.log.warn("An image provider threw an error when queried.", t)
            null
        }
        if (image != null) return image
      }
    }
    null
  }

  /**
   * 打开手册。
   *
   * 1.7.10 走 `player.openGui(OpenComputers, GuiType.Manual.id, ...)`；1.21.1 里
   * [[GuiType.Manual]] 属于「无容器界面」（`GuiType.Category.None`），不经过
   * `MenuProvider` / `MenuType`，直接由客户端的 [[GuiHandler]] 按 id 造出屏幕再
   * `Minecraft#setScreen`。屏幕本身（`gui.Manual`）由 GUI 层负责，这里只做接线，
   * 以免对它的构造器产生硬依赖。
   */
  override def openFor(player: Player): Unit = {
    if (player != null && player.level() != null && player.level().isClientSide) {
      try {
        GuiHandler.getClientGuiElement(GuiType.Manual.id, player, player.level(), 0, 0, 0) match {
          case screen: Screen => Minecraft.getInstance().setScreen(screen)
          case _ =>
            // TODO(client.gui.Manual): 手册屏幕尚未移植完成（`gui.Manual` 仍是 1.7.10 的
            //   `GuiScreen` 版本），此时 [[GuiHandler.getClientGuiElement]] 返回 null。
            //   等它移植好后这里会自动生效，无需再改本文件。
            OpenComputers.log.debug("The in-game manual screen is not available yet.")
        }
      }
      catch {
        case t: Throwable =>
          OpenComputers.log.warn("Failed opening the in-game manual.", t)
      }
    }
  }

  def reset(): Unit = {
    history.clear()
    history.push(new History(s"$LanguageKey/index.md"))
  }

  override def navigate(path: String): Unit = {
    Minecraft.getInstance().screen match {
      case manual: gui.Manual => manual.pushPage(path)
      case _ => history.push(new History(path))
    }
  }

  def makeRelative(path: String, base: String): String =
    if (path.startsWith("/")) path
    else {
      val splitAt = base.lastIndexOf('/')
      if (splitAt >= 0) base.splitAt(splitAt)._1 + "/" + path
      else path
    }

  @tailrec private def contentForWithRedirects(path: String, seen: List[String] = List.empty): Option[java.lang.Iterable[String]] = {
    if (seen.contains(path)) {
      // 1.7.10 用 `asJavaIterable`；这里直接拼一个 `java.util.ArrayList`，避免依赖
      // `CollectionConverters` 的隐式转换（它只提供 `asJava` / `asScala` 扩展方法）。
      val loop = new java.util.ArrayList[String]()
      loop.add("Redirection loop: ")
      seen.foreach(loop.add)
      loop.add(path)
      return Some(loop)
    }
    doContentLookup(path) match {
      case Some(content) =>
        // 注意：`content` 是 `java.lang.Iterable`，用 Java 的 `iterator()` 取首行，
        // 不能直接用 Scala 集合的 `headOption`。
        val iterator = content.iterator()
        val head = if (iterator.hasNext) Some(iterator.next()) else None
        head match {
          case Some(line) if line.toLowerCase.startsWith("#redirect ") =>
            contentForWithRedirects(makeRelative(line.substring("#redirect ".length), path), seen :+ path)
          case _ => Some(content)
        }
      case _ => None
    }
  }

  private def doContentLookup(path: String): Option[java.lang.Iterable[String]] = {
    for (provider <- contentProviders) {
      val lines = try provider.getContent(path) catch {
        case t: Throwable =>
          OpenComputers.log.warn("A content provider threw an error when queried.", t)
          null
      }
      if (lines != null) return Some(lines)
    }
    None
  }

  /**
   * 当前语言代码，格式与手册文档目录一致（`en_US` / `zh_CN` / ...）。
   *
   * 1.7.10 的 `FMLCommonHandler#getCurrentLanguage` 直接返回这种大小写形式；
   * 1.21.1 的 `LanguageManager#getSelected` 返回全小写（`en_us`），因此这里把
   * 地区部分转成大写，否则 `%LANGUAGE%` 会始终匹配不到资源、退化成英文。
   */
  private def currentLanguage: String = {
    val selected = Minecraft.getInstance().getLanguageManager.getSelected
    if (selected == null || selected.isEmpty) FallbackLanguage
    else selected.split('_') match {
      case Array(lang, region) => lang + "_" + region.toUpperCase(java.util.Locale.ROOT)
      case _ => selected
    }
  }
}
