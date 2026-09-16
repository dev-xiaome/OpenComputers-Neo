package li.cil.oc.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.event.RackMountableRenderEvent
import li.cil.oc.client.renderer.tileentity.RenderUtil
import li.cil.oc.common.item.data.StackSerializer
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemDisplayContext
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge

/**
 * 机架挂载物的动态渲染：磁盘驱动器的盘片、服务器 / 终端服务器的指示灯。
 *
 * ==为什么在 `client` 包里（原为 `common.event.RackMountableRenderHandler`）==
 * 本处理器是**纯渲染**代码：它用 [[Textures]]、`Minecraft#getItemRenderer`、
 * `PoseStack` / `MultiBufferSource`，并且监听 `RackMountableRenderEvent`。
 * 1.21.1 要求 `common` 包不能有对 `client` 包的编译期引用
 * （否则整个 `client` 包会被拖进编译集，见 [[li.cil.oc.common.ClientHooks]]），
 * 因此整体搬到 `client` 包，由 [[ClientListeners]] 注册。
 *
 * **引用方注意**：`client.renderer.tileentity.RackRenderer` 里原来的
 * `import li.cil.oc.common.event.RackMountableRenderHandler` 需要改成
 * `import li.cil.oc.client.RackMountableRenderHandler`（对象名保持不变，调用点写法不变）。
 *
 * 1.21.1 迁移要点：
 *  - `@SubscribeEvent` → 显式 `addListener`；本处理器只在物理客户端注册。
 *  - 渲染不再有全局 `Tessellator` / `RenderManager`：覆盖层写进
 *    `MultiBufferSource`（`RackMountableRenderEvent.BlockEntity#renderOverlay`），
 *    盘片物品走 `ItemRenderer#renderStatic` + `PoseStack`。
 *  - 事件本身不携带 `PoseStack` / `MultiBufferSource`（`li.cil.oc.api` 已冻结），
 *    因此由 `client.renderer.tileentity.RackRenderer` 在 post 事件之前通过
 *    [[setRenderContext]] 把当前渲染上下文交给本处理器。
 *  - `ItemStack.loadItemStackFromNBT` → [[li.cil.oc.common.item.data.StackSerializer.loadItemStack]]。
 *  - `NBT.TAG_STRING` → `Tag.TAG_STRING`。
 *  - 贴图常量名对齐 [[Textures]] 的现状：`Textures.blockRackXxx` → `Textures.Block.RackXxx`，
 *    `Textures.Rack.diskDrive/server/terminal` → `Textures.Block.RackDiskDrive/RackServer/RackTerminal`。
 */
object RackMountableRenderHandler {
  lazy val DiskDriveMountable = api.Items.get(Constants.ItemName.DiskDriveMountable)

  lazy val Servers = Array(
    api.Items.get(Constants.ItemName.ServerTier1),
    api.Items.get(Constants.ItemName.ServerTier2),
    api.Items.get(Constants.ItemName.ServerTier3),
    api.Items.get(Constants.ItemName.ServerCreative)
  )

  lazy val TerminalServer = api.Items.get(Constants.ItemName.TerminalServer)

  /**
   * 当前渲染上下文：`() => (PoseStack, MultiBufferSource)`。
   *
   * 字段类型用 `Function0`（而不是直接存 `PoseStack`）是为了让本类在**专用服务端**上
   * 也不会因为字段类型引用客户端类而加载失败；本类本身只在客户端注册。
   */
  private var renderContext: () => (PoseStack, MultiBufferSource) = null

  /** 由 `RackRenderer` 在 post 事件之前设置当前渲染上下文。 */
  def setRenderContext(context: () => (PoseStack, MultiBufferSource)): Unit = renderContext = context

  /** 清除渲染上下文（渲染结束后调用，避免持有已失效的缓冲区）。 */
  def clearRenderContext(): Unit = renderContext = null

  /** 注册监听器；由 [[ClientListeners]] 调用一次。 */
  def initialize(): Unit = {
    if (FMLEnvironment.dist.isClient) {
      NeoForge.EVENT_BUS.addListener((e: RackMountableRenderEvent.BlockEntity) => onRackMountableRendering(e))
      NeoForge.EVENT_BUS.addListener((e: RackMountableRenderEvent.Block) => onRackMountableRendering(e))
    }
  }

  def onRackMountableRendering(e: RackMountableRenderEvent.BlockEntity): Unit = {
    if (renderContext == null) return
    val (pose, buffer) = renderContext()
    if (buffer == null) return

    if (e.data != null && DiskDriveMountable == api.Items.get(e.rack.getStackInSlot(e.mountable))) {
      // 磁盘驱动器。
      if (e.data.contains("disk")) {
        val stack = StackSerializer.loadItemStack(e.data.getCompound("disk"))
        if (stack != null && !stack.isEmpty) {
          val level = e.rack.world()
          pose.pushPose()
          pose.scale(1, -1, 1)
          pose.translate(10 / 16f, -(3.5f + e.mountable * 3f) / 16f, 1 / 16f)
          pose.mulPose(Axis.XN.rotationDegrees(90))

          // 1.21.1 的打包亮度布局与 1.7.10 一致：低 16 位方块光、高 16 位天空光。
          val brightness = level.getLightBrightnessForSkyBlocks(BlockPosition(e.rack).offset(e.rack.facing), 0)

          // 原实现用一个临时 `EntityItem` + `RenderManager` 渲染盘片；
          // 1.21.1 直接调用物品渲染器，语义相同。
          Minecraft.getInstance.getItemRenderer.renderStatic(
            stack, ItemDisplayContext.FIXED, brightness, OverlayTexture.NO_OVERLAY, pose, buffer, level, 0)
          pose.popPose()
        }
      }

      if (System.currentTimeMillis() - e.data.getLong("lastAccess") < 400 &&
        e.rack.world().getRandom.nextDouble() > 0.1) {
        RenderState.disableLighting()
        RenderState.makeItBlend()

        // 注意（紫黑方格根因）：`RackMountableRenderEvent#renderOverlay` 内部用的是
        // `RenderType.entityCutout(texture)` 并写出 0..1 的贴图内 UV，
        // 也就是要**完整贴图文件路径**（含 `textures/` 前缀与 `.png`）。
        // `Textures.Block.Xxx` 是给图集精灵查询用的路径，直接传进去只会得到 missingno。
        e.renderOverlay(buffer, Textures.blockFile("diskdrivemountableactivity"))

        RenderState.enableLighting()
      }
    }
    else if (e.data != null && Servers.contains(api.Items.get(e.rack.getStackInSlot(e.mountable)))) {
      // 服务器。
      RenderState.disableLighting()
      RenderState.makeItBlend()

      if (e.data.getBoolean("isRunning")) {
        e.renderOverlay(buffer, Textures.blockFile("serverfronton"))
      }
      if (e.data.getBoolean("hasErrored") && RenderUtil.shouldShowErrorLight(e.rack.hashCode * (e.mountable + 1))) {
        e.renderOverlay(buffer, Textures.blockFile("serverfronterror"))
      }
      if (System.currentTimeMillis() - e.data.getLong("lastFileSystemAccess") < 400 &&
        e.rack.world().getRandom.nextDouble() > 0.1) {
        e.renderOverlay(buffer, Textures.blockFile("serverfrontactivity"))
      }
      if ((System.currentTimeMillis() - e.data.getLong("lastNetworkActivity") < 300 &&
        System.currentTimeMillis() % 200 > 100) && e.data.getBoolean("isRunning")) {
        e.renderOverlay(buffer, Textures.blockFile("serverfrontnetworkactivity"))
      }

      RenderState.enableLighting()
    }
    else if (e.data != null && TerminalServer == api.Items.get(e.rack.getStackInSlot(e.mountable))) {
      // 终端服务器。
      RenderState.disableLighting()
      RenderState.makeItBlend()

      e.renderOverlay(buffer, Textures.blockFile("terminalserverfronton"))
      val countConnected = e.data.getList("keys", Tag.TAG_STRING).size()

      if (countConnected > 0) {
        val u0 = 7 / 16f
        val u1 = u0 + (2 * countConnected - 1) / 16f
        e.renderOverlay(buffer, Textures.blockFile("terminalserverfrontpresence"), u0, u1)
      }

      RenderState.enableLighting()
    }
  }

  def onRackMountableRendering(e: RackMountableRenderEvent.Block): Unit = {
    // 同 `BlockEntity` 分支：正面覆盖贴图同样要求完整贴图文件路径。
    if (DiskDriveMountable == api.Items.get(e.rack.getStackInSlot(e.mountable))) {
      // 磁盘驱动器。
      e.setFrontTextureOverride(Textures.blockFile("diskdrivemountable"))
    }
    else if (Servers.contains(api.Items.get(e.rack.getStackInSlot(e.mountable)))) {
      // 服务器。
      e.setFrontTextureOverride(Textures.blockFile("serverfront"))
    }
    else if (TerminalServer == api.Items.get(e.rack.getStackInSlot(e.mountable))) {
      // 终端服务器。
      e.setFrontTextureOverride(Textures.blockFile("terminalserverfront"))
    }
  }
}
