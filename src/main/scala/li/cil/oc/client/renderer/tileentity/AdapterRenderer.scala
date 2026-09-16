package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction

/**
 * 适配器（Adapter）方块实体渲染器：给每个「敞开」的面叠一层活动指示灯贴图。
 *
 * ==与 1.7.10 版的对应关系==
 *  - `TileEntitySpecialRenderer` 换成 `BlockEntityRenderer`，**无参构造**（client 包的
 *    `Proxy` 用 `() => new AdapterRenderer` 注册）。
 *  - `glTranslated(x + 0.5, y + 0.5, z + 0.5)` 换成 `pose.translate(0.5, 0.5, 0.5)`：
 *    1.21.1 的 `PoseStack` 入场原点已经是方块角，方块坐标参数不再需要。
 *  - `glScaled(1.0025, -1.0025, 1.0025)` 换成 `pose.scale(s, -s, s)`，**y 轴必须保持取负**：
 *    原来的四角坐标就是按「y 被镜像」的空间写的，把负号去掉会让覆盖层上下颠倒到别的面，
 *    并且三角形绕序反过来、被背面剔除（整层光效直接看不见）。
 *  - `RenderState.disableLighting/makeItBlend`、`glPushAttrib/glPopAttrib` 整体删除：
 *    1.21.1 没有固定管线状态要保存，混合 / 深度写入由 `RenderType` 描述，
 *    自发光则把 `RenderUtil.fullBright` 写进顶点光照。
 *  - `Tessellator#startDrawingQuads/addVertexWithUV/draw` 换成一次
 *    `RenderUtil.drawSpriteQuad`（内部是 `PoseStack` 加 `VertexConsumer`）。
 *  - `Textures.Adapter.iconOn`（`IIcon`）换成 `Textures.Block.AdapterOn`（`ResourceLocation`）
 *    加 `RenderUtil.sprite`；精灵可能为 `null`，`drawSpriteQuad` 内部已判空。
 *
 * ==UV 与 RenderType 的取舍==
 *  - 贴图 `adapteron` 是**动画方块贴图**（16x192，纵向 12 帧，frametime 2），所以只能走
 *    方块图集：`RenderType.cutout()` 加 `TextureAtlasSprite`。不能选 `entityCutout` 这类
 *    指向独立贴图文件的 RenderType（它们要求贴图不在图集里）。
 *  - 该贴图带二值 alpha（逐像素核验过：只有 0 与 255，没有半透明像素），因此 `cutout()`
 *    与原来的 `RenderState.makeItBlend` 观感完全一致；用 `solid()` 反而会把透明像素
 *    渲染成黑块。
 *  - 整张贴图铺满一个面：1.7.10 的 `icon.getMinU/getMaxU` 只覆盖图集的**当前动画帧**，
 *    `TextureAtlasSprite#getU0/getU1` 语义相同（也按帧返回），故直接用 `drawSpriteQuad`。
 *  - `adapteron` **不是**旋转对称贴图，所以 DOWN / UP 两面原来那套「贴图整体旋转
 *    180 度 / 90 度」的 UV 顺序必须原样保留。1.7.10 里这两面的四角 UV 不是
 *    `drawQuad` 认识的标准顺序，于是把顶点列表**循环移位**到「第一个顶点正好对应贴图
 *    `(U0, V1)`」的位置：循环移位既不改变四边形本身、也不改变绕序，但能让
 *    `drawSpriteQuad` 算出的 UV 与原来的 `addVertexWithUV` 逐角一致。
 */
class AdapterRenderer extends BlockEntityRenderer[tileentity.Adapter] {

  /** 等价于 1.7.10 的 `GL11.glScaled(1.0025, ...)`：把覆盖层顶出方块表面，避开 z-fighting。 */
  private final val OverlayScale = 1.0025f

  override def render(t: tileentity.Adapter, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (t == null) return
    // 原实现的门槛：只要有任意一面敞开就画覆盖层（关闭的面由方块本体模型负责）。
    if (!t.openSides.contains(true)) return

    val vc = buffer.getBuffer(RenderType.cutout())
    val sideActivity = RenderUtil.sprite(Textures.Block.AdapterOn)

    pose.pushPose()
    // 与原实现逐句对应：glTranslated(x + 0.5, y + 0.5, z + 0.5) 换成 0.5 平移，
    // glScaled(1.0025, -1.0025, 1.0025) 换成等比缩放（y 取负），
    // glTranslatef(-0.5, -0.5, -0.5) 把缩放中心挪回方块中心。
    pose.translate(0.5, 0.5, 0.5)
    pose.scale(OverlayScale, -OverlayScale, OverlayScale)
    pose.translate(-0.5, -0.5, -0.5)

    // 下面（-Y）：原四角是 (0,1,0)(1,1,0)(1,1,1)(0,1,1)，y 取负后即方块底面；
    // 这里循环移位到 UV 为 (U0, V1) 的那个角（原来是第三个角 (1,1,1)）打头。
    if (t.isSideOpen(Direction.DOWN)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, RenderUtil.fullBright, overlay)
    }

    // 上面（+Y）：原四角是 (0,0,0)(0,0,1)(1,0,1)(1,0,0)，y 取负后即方块顶面；
    // 循环移位到 UV 为 (U0, V1) 的那个角（原来是第四个角 (1,0,0)）打头。
    if (t.isSideOpen(Direction.UP)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, RenderUtil.fullBright, overlay)
    }

    // 北面（-Z）：原四角顺序本来就是标准顺序，直接照抄。
    if (t.isSideOpen(Direction.NORTH)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        1, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, RenderUtil.fullBright, overlay)
    }

    // 南面（+Z）
    if (t.isSideOpen(Direction.SOUTH)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1, RenderUtil.fullBright, overlay)
    }

    // 西面（-X）
    if (t.isSideOpen(Direction.WEST)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, RenderUtil.fullBright, overlay)
    }

    // 东面（+X）
    if (t.isSideOpen(Direction.EAST)) {
      RenderUtil.drawSpriteQuad(pose, vc, sideActivity,
        1, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, RenderUtil.fullBright, overlay)
    }

    pose.popPose()
  }
}
