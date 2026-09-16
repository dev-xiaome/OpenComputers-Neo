package li.cil.oc.client.renderer.block

/**
 * 交换器 / 转置器（Transposer）的静态方块几何。
 *
 * ==1.7.10 状态==
 * `Transposer.render(block, x, y, z, renderer)` 用 `RenderBlocks` 拼出一个
 * 「镂空立方体」：八个角各一块 7×7×7 的立方体，八条棱的中点各一块补丁
 * （`0..5/16 × 7/16..9/16 × 0..5/16` 这类），最后在正中间放一块
 * `1/16..15/16` 的内核。整体看上去像一个被掏空、只剩骨架和核心的方块。
 * 另有物品栏分支：同一套体素，六面显式补画。
 *
 * ==1.21.1 状态==
 * 1.21.1 取消了 `ISimpleBlockRenderingHandler` / `RenderBlocks`，方块几何
 * 改由 blockstate json + 烘焙模型表达，因此本对象没有注册点。
 *
 * ==1.21.1 应该由谁承担==
 *  - 静态外形：`models/block/transposer.json`，共 17 个 element
 *    （8 个角 + 8 个棱中点补丁 + 1 个内核）+ `blockstates/transposer.json`。
 *  - 工作时的高亮内核 / 物品流动指示：属于动态状态，由
 *    `client/renderer/tileentity/TransposerRenderer` 承担（由另一个代理移植），
 *    那里应当用 `RenderUtil.drawCube` / `RenderUtil.drawSpriteQuad` 配合
 *    `Textures.Block.TransposerOn` 绘制。
 *  - 物品栏模型：`item/transposer.json`。
 */
object Transposer {

  /**
   * 原 `render(block, x, y, z, renderer)`：世界里画交换器的镂空骨架 + 内核。
   *
   * 1.21.1 由 `models/block/transposer.json` 承担静态部分；动态高亮由
   * `client.renderer.tileentity.TransposerRenderer` 承担。
   */
  def render(): Unit = {
    // TODO(blk): 1.21.1 无 ISimpleBlockRenderingHandler 注册点，保留签名的空实现。
  }
}
