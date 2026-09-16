package li.cil.oc.client.renderer.block

/**
 * 全息投影仪（Hologram）的静电力学基座几何。
 *
 * ==1.7.10 状态==
 * 两个重载都用 `RenderBlocks#setRenderBounds` + `renderStandardBlock`
 * 拼出「投影仪本体」：
 *  - 世界分支：中心 8×3×8 的底台，四周围一圈 2 像素厚、高 `7/16` 的墙，
 *    墙内侧再嵌四条 2 像素厚、高 `3/16`、离地 `2/16` 的内衬；
 *  - 物品栏分支：同一套体素，但每个面逐个 `renderFaceXxx` 显式补画。
 *
 * ==1.21.1 状态==
 * 1.21.1 取消了 `ISimpleBlockRenderingHandler` / `RenderBlocks`，方块几何改由
 * blockstate json + 烘焙模型表达，因此本对象没有注册点。
 *
 * ==1.21.1 应该由谁承担==
 *  - 基座外形：`models/block/hologram.json`（底台 1 个 element + 四面墙
 *    4 个 element + 内衬 4 个 element，共 9 个 element）+
 *    `blockstates/hologram.json`。带 tier 的变体（Tier1 / Tier2）可用
 *    `variants` 或不同模型文件区分。
 *  - **全息投影内容本身**（上一层的彩色立方体 / 体素）不是静态几何，
 *    由 `client/renderer/tileentity/HologramRenderer` 承担
 *    （同目录的 `HologramRendererFallback` 在 1.21.1 已不需要，
 *    因为不再有 `GLContext.getCapabilities.OpenGL15` 分支）。
 *    那里应当用 `RenderUtil.drawCube` / `RenderUtil.drawQuad` 配合
 *    `Textures.Block.HologramEffect` 绘制。
 *  - 物品栏模型：`item/hologram.json`。
 */
object Hologram {

  /**
   * 原 `render(block, metadata, x, y, z, renderer)`：世界里画投影仪基座。
   *
   * 1.21.1 由 `blockstates/hologram.json` + 烘焙模型承担；
   * 投影内容由 `client.renderer.tileentity.HologramRenderer` 承担。
   */
  def render(): Unit = {
    // TODO(blk): 1.21.1 无 ISimpleBlockRenderingHandler 注册点，保留签名的空实现。
  }
}
