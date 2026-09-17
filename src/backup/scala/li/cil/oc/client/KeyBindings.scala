package li.cil.oc.client

import com.mojang.blaze3d.platform.InputConstants
import li.cil.oc.OpenComputers
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import org.lwjgl.glfw.GLFW

import scala.collection.mutable

/**
 * 客户端按键绑定与「按键是否按下 / 按键显示名」查询。
 *
 * ==1.7.10 -> 1.21.1 映射==
 *  - `net.minecraft.client.settings.KeyBinding` -> [[net.minecraft.client.KeyMapping]]
 *  - `GameSettings.getKeyDisplayString(code)` -> `KeyMapping#getTranslatedKeyMessage`
 *    （返回 `Component`，取 `getString` 得到显示名）
 *  - `org.lwjgl.input.Keyboard` / `Mouse`（LWJGL2）-> `com.mojang.blaze3d.platform.InputConstants`
 *    + `org.lwjgl.glfw.GLFW`（LWJGL3）
 *  - `mc.gameSettings.keyBindSneak` -> `mc.options.keyShift`
 *
 * `keyBindingChecks` / `keyBindingNameGetters` 是给 `integration` 层扩展用的钩子
 * （1.7.10 里 integration 会往里塞自定义判断，例如合成键）。1.21.1 的 integration 层
 * 目前尚未移植，但**公开形状原样保留**，将来接入时不用改调用方。
 *
 * 注意：默认实现刻意使用「轮询物理按键状态」而不是 `KeyMapping#isDown`，因为
 * `isDown` 只在没有界面打开时才被刷新，而 [[showExtendedTooltips]] 需要在于 GUI 内
 * （物品 tooltip）也生效——这与 1.7.10 直接轮询 `Keyboard.isKeyDown` 的行为一致。
 */
object KeyBindings {
  /** 可扩展的「按键是否按下」判定链，默认只有原版实现。 */
  val keyBindingChecks: mutable.ArrayBuffer[KeyMapping => Boolean] = mutable.ArrayBuffer(isKeyBindingPressedVanilla _)

  /** 可扩展的「按键显示名」获取链，默认只有原版实现。 */
  val keyBindingNameGetters: mutable.ArrayBuffer[KeyMapping => Option[String]] = mutable.ArrayBuffer(getKeyBindingNameVanilla _)

  def showExtendedTooltips: Boolean = isKeyBindingPressed(extendedTooltip)

  def showMaterialCosts: Boolean = isKeyBindingPressed(materialCosts)

  def isPastingClipboard: Boolean = isKeyBindingPressed(clipboardPaste)

  def getKeyBindingName(keyBinding: KeyMapping): String = keyBindingNameGetters.iterator.map(_(keyBinding)).collectFirst {
    case Some(name) => name
  }.getOrElse("???")

  def isKeyBindingPressed(keyBinding: KeyMapping): Boolean =
    keyBinding != null && keyBindingChecks.forall(_(keyBinding))

  /**
   * 原版实现：直接查询物理按键 / 鼠标按键状态。
   *
   * 1.7.10 的 `getKeyCode < 0` 表示鼠标按键（`Mouse.isButtonDown(code + 100)`）；
   * 1.21.1 用 `InputConstants.Key#getType == InputConstants.Type.MOUSE` 区分。
   */
  def isKeyBindingPressedVanilla(keyBinding: KeyMapping): Boolean = try {
    val key = keyBinding.getKey
    val window = Minecraft.getInstance().getWindow
    if (key == null || window == null) false
    else if (key.getType == InputConstants.Type.MOUSE) GLFW.glfwGetMouseButton(window.getWindow, key.getValue) == InputConstants.PRESS
    else InputConstants.isKeyDown(window.getWindow, key.getValue)
  }
  catch {
    case _: Throwable => false
  }

  /** 原版实现：1.7.10 的 `GameSettings.getKeyDisplayString` -> `KeyMapping#getTranslatedKeyMessage`。 */
  def getKeyBindingNameVanilla(keyBinding: KeyMapping): Option[String] = try {
    if (keyBinding == null) None
    else Option(keyBinding.getTranslatedKeyMessage).map(_.getString).filter(_.nonEmpty)
  }
  catch {
    case _: Throwable => None
  }

  /** 扩展提示键：1.7.10 的 `gameSettings.keyBindSneak` -> 1.21.1 的 `options.keyShift`。 */
  def extendedTooltip: KeyMapping = {
    val mc = Minecraft.getInstance()
    if (mc == null || mc.options == null) null else mc.options.keyShift
  }

  /** 扩展提示键的显示名（供 tooltip 文案 `tooltip.TooLong` 使用）。 */
  def extendedTooltipName: String = getKeyBindingName(extendedTooltip)

  /** 材料成本提示键（默认左 Alt，等价 1.7.10 的 `Keyboard.KEY_LMENU`）。 */
  val materialCosts = new KeyMapping("key.materialCosts", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, OpenComputers.Name)

  /** 剪贴板粘贴键（默认 Insert，等价 1.7.10 的 `Keyboard.KEY_INSERT`）。 */
  val clipboardPaste = new KeyMapping("key.clipboardPaste", InputConstants.Type.KEYSYM, InputConstants.KEY_INSERT, OpenComputers.Name)

  /**
   * 注册两个 [[net.minecraft.client.KeyMapping]]。
   *
   * 1.7.10 是 `ClientRegistry.registerKeyBinding(...)`；1.21.1 改为监听 mod 事件总线上的
   * `RegisterKeyMappingsEvent`（它是 `IModBusEvent`，**不能**挂 `NeoForge.EVENT_BUS`）。
   * 由 `client.Proxy.initialize(modBus)` 里的监听器转调到这里。
   */
  def register(event: RegisterKeyMappingsEvent): Unit = {
    event.register(materialCosts)
    event.register(clipboardPaste)
  }
}
