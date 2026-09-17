package li.cil.oc.client.renderer.block

/**
 * 打印机（Printer）的静态方块几何。
 *
 * ==1.7.10 状态==
 * `Printer.render(block, x, y, z, renderer)` 用 `RenderBlocks` 拼出打印机外形：
 *  - 底部整块 `0..1 × 0..8/16 × 0..1`；
 *  - 四个角柱 `0..3/16 × 8/16..1 × 0..3/16` 之类，共 4 个；
 *  - 顶部四条围栏 `3/16..13/16 × 13/16..1 × 0..3/16` 之类，共 4 个。
 * 也就是「一个矮箱体 + 四根立柱 + 顶部一圈开口的围栏」，中间空出来放纸。
 * 另有物品栏分支：同一套体素，六面显式补画。
 *
 * ==1.21.1 状态==
 * 1.21.1 取消了 `ISimpleBlockRenderingHandler` / `RenderBlocks`，方块几何
 * 改由 blockstate json + 烘焙模型表达，因此本对象没有注册点。
 *
 * ==1.21.1 应该由谁承担==
 *  - 打印机静态外形：`models/block/printer.json`，共 9 个 element
 *    （1 个底板 + 4 个角柱 + 4 个顶栏），`blockstates/printer.json` 引用它。
 *    朝向用 `variants` 的 `y` 旋转表达。
 *  - 纸面 / 打印中的动态内容（纸、墨盒、打印头动画）属于动态状态，由
 *    `client/renderer/tileentity/PrinterRenderer` 承担（该文件由另一个代理移植）：
 *    那里应当用 `RenderUtil.drawSpriteQuad` 画纸面，并注意 1.7.10 里
 *    `RenderBlocks.flipTexture` 的翻转效果在 1.21.1 要靠顶点 UV 顺序
 *    （或 `RenderUtil.drawQuad` 的 `u0/v0/u1/v1` 参数）显式表达。
 */
object Printer {

  /**
   * 原 `render(block, x, y, z, renderer)`：世界里画打印机外形。
   *
   * 1.21.1 由 `blockstates/printer.json` + `models/block/printer.json` 承担；
   * 纸面与打印动画由 `client.renderer.tileentity.PrinterRenderer` 承担。
   */
  def render(): Unit = {
    // TODO(blk): 1.21.1 无 ISimpleBlockRenderingHandler 注册点，保留签名的空实现。
  }
}
