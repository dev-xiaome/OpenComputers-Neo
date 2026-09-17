package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.item.data.DriveData
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

/**
 * 磁盘驱动器 / 软盘界面的「托管模式」窗口
 * （基准：1.20 CE 的 `client/gui/Drive.scala`，对应 1.7.10 的 `client.gui.Drive`）。
 *
 * 界面本体只是一个固定尺寸的底图加三个开关按钮，真正的数据在**物品**里：
 *  - 「被管理 / 未被管理」写进 [DriveData] 的 `unmanaged` 标记（由服务端决定文件系统归属）；
 *  - 「只读锁定」把当前玩家名写进 [DriveData] 的锁记录。
 * 三个动作都是「客户端先发包，再就地改本地物品 NBT」，与 1.7.10 完全一致
 * （本地先改是为了立刻刷新按钮状态，服务端稍后会广播权威值）。
 *
 * ==1.7.10 到 1.21.1 的迁移要点==
 *  - `GuiScreen` 换成 1.21.1 的 [net.minecraft.client.gui.screens.Screen]，
 *    由 [[traits.Window]] 混入（居中 + 底图），因此 `guiLeft` / `guiTop` /
 *    `xSize` / `windowHeight` 的语义与旧版逐字对应；
 *  - `initGui()` 换成 [[init]]，`add(buttonList, button)` 换成
 *    [[net.minecraft.client.gui.screens.Screen#addRenderableWidget]]；
 *  - 按钮回调不再由 `id` 分发（1.21.1 没有 `GuiButton` 的 id 分发），
 *    改为给每个 [[ImageButton]] 挂 `actionPerformed` 函数字段；
 *  - `fontRendererObj.drawSplitString(text, x, y, width, color)` 换成
 *    [[GuiGraphics#drawWordWrap]]（参数顺序与语义相同，文字参数改为 [Component]）；
 *  - `mc.thePlayer` 换成 `playerInventory.player`（构造时已注入玩家背包）。
 *
 * ==与 1.20 CE 的差异==
 *  1. 基类用本项目的 [[traits.Window]]（它已经在内部继承 `Screen`），不再显式写
 *     `extends screens.Screen(...) with traits.Window`；
 *  2. 底图常量用本项目 [[Textures]] 的扁平命名（`guiDrive` / `guiButtonDriveMode`），
 *     对应 CE 的 `Textures.GUI.Drive` / `Textures.GUI.ButtonDriveMode`；
 *  3. 按钮回调按本项目 [[ImageButton]] 的「函数字段」约定挂载（见上）。
 */
class Drive(playerInventory: Inventory, val driveStack: () => ItemStack) extends traits.Window {
  /** 旧版 `ySize` 覆写：这个窗口比默认的 166 矮。 */
  override val windowHeight = 120

  override def backgroundImage = Textures.guiDrive

  protected var managedButton: ImageButton = _
  protected var unmanagedButton: ImageButton = _
  protected var lockedButton: ImageButton = _

  /**
   * 按当前物品 NBT 刷新三个开关按钮的显示状态。
   *
   * 与 1.20 CE 一致：`toggled` 决定按钮画贴图的左半还是右半（[[ImageButton#canToggle]]），
   * 已锁定时把锁定按钮置灰（`active = false`）——`active` 就是旧版的 `enabled`。
   */
  def updateButtonStates(): Unit = {
    val data = new DriveData(driveStack())
    unmanagedButton.toggled = data.isUnmanaged
    managedButton.toggled = !unmanagedButton.toggled
    lockedButton.toggled = data.isLocked
    lockedButton.active = !data.isLocked
  }

  override protected def init(): Unit = {
    super.init()
    // 与旧版一致：这个界面里要让鼠标脱离原版的视角捕获与按键绑定。
    minecraft.mouseHandler.releaseMouse()
    KeyMapping.releaseAll()

    // 坐标与 1.20 CE 逐字相同（`leftPos` / `topPos` 在本项目里叫 `guiLeft` / `guiTop`）。
    managedButton = new ImageButton(0, guiLeft + 11, guiTop + 11, 74, 18,
      Textures.guiButtonDriveMode, text = Localization.Drive.Managed, textColor = 0x608060, canToggle = true)
    managedButton.actionPerformed = _ => {
      ClientPacketSender.sendDriveMode(unmanaged = false)
      DriveData.setUnmanaged(driveStack(), unmanaged = false)
      updateButtonStates()
    }

    unmanagedButton = new ImageButton(1, guiLeft + 91, guiTop + 11, 74, 18,
      Textures.guiButtonDriveMode, text = Localization.Drive.Unmanaged, textColor = 0x608060, canToggle = true)
    unmanagedButton.actionPerformed = _ => {
      ClientPacketSender.sendDriveMode(unmanaged = true)
      DriveData.setUnmanaged(driveStack(), unmanaged = true)
      updateButtonStates()
    }

    lockedButton = new ImageButton(2, guiLeft + 11, guiTop + windowHeight - 42, 44, 18,
      Textures.guiButtonDriveMode, text = Localization.Drive.ReadOnlyLock, textColor = 0x608060, canToggle = true)
    lockedButton.actionPerformed = _ => {
      ClientPacketSender.sendDriveLock()
      DriveData.lock(driveStack(), playerInventory.player)
      updateButtonStates()
    }

    addRenderableWidget(managedButton)
    addRenderableWidget(unmanagedButton)
    addRenderableWidget(lockedButton)

    updateButtonStates()
  }

  override def render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, dt: Float): Unit = {
    // 底图与按钮由 [[traits.Window#render]] 与 Screen 负责，这里只补两段说明文字。
    super.render(graphics, mouseX, mouseY, dt)
    graphics.drawWordWrap(font, Component.literal(Localization.Drive.Warning),
      guiLeft + 11, guiTop + 37, xSize - 20, 0x404040)
    graphics.drawWordWrap(font, Component.literal(Localization.Drive.LockWarning),
      guiLeft + 61, guiTop + windowHeight - 48, xSize - 68, 0x404040)
  }
}
