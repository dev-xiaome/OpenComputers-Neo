package li.cil.oc.common.component

import li.cil.oc.util

/**
 * TODO(client): 渲染层占位。
 *
 * 1.7.10 的 `li.cil.oc.client.renderer.font.TextBufferRenderData` 负责把
 * [[li.cil.oc.util.TextBuffer]] 的内容转换成可供 `TextBufferRenderCache` 使用的
 * 字形渲染数据（脏标记、字形缓存、视口裁剪）。1.21.1 的渲染层（`PoseStack` +
 * `VertexConsumer` + 烘焙字形图集）尚未移植，这里只保留数据契约，保证
 * [[li.cil.oc.common.component.TextBuffer]] 的结构与调用点与旧版一致。
 *
 * 等 `client` 渲染包移植完成后，本 trait 应被真正的渲染数据实现替换。
 */
trait TextBufferRenderData {
  /** 自上次渲染以来内容是否发生变化。 */
  def dirty: Boolean

  def dirty_=(value: Boolean): Unit

  /** 底层单元数据（字符 + 打包颜色）。 */
  def data: util.TextBuffer

  /** 当前视口尺寸（列 x 行），渲染时按此裁剪。 */
  def viewport: (Int, Int)
}

/**
 * TODO(client): 渲染层占位。
 *
 * 旧版会在这里调用字体图集把 [[TextBufferRenderData]] 烘焙成 GPU 纹理并返回
 * 该帧是否有更新。1.21.1 的贴图 / 图集方案完全不同，因此暂时只保留
 * 「字符像素尺寸」查询与一个空渲染入口。
 */
object TextBufferRenderCache {
  /** 单字符的像素尺寸；旧版来自 `FontRenderer` 的字形图集。 */
  object renderer {
    val charRenderWidth: Int = 6

    val charRenderHeight: Int = 9
  }

  /** 渲染入口。1.21.1 渲染管线未移植前不做任何事。 */
  def render(data: TextBufferRenderData): Unit = {}
}
