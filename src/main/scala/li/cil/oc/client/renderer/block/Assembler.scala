package li.cil.oc.client.renderer.block

/**
 * 装配机（Assembler）的静态方块几何。
 *
 * ==1.7.10 状态==
 * 本对象是 `ISimpleBlockRenderingHandler` 的协作类，被
 * `client.renderer.block.BlockRenderer` 的 `renderWorldBlock` /
 * `renderInventoryBlock` 调用，用 `RenderBlocks#setRenderBounds` +
 * `renderStandardBlock` 逐个体素拼出「底座 + 中间细颈 + 顶盖」三段外形。
 *
 * ==1.21.1 状态==
 * 1.21.1 **完全取消了 `ISimpleBlockRenderingHandler` / `RenderBlocks` /
 * `RenderingRegistry.registerBlockHandler`** 这套机制，方块几何改由
 * blockstate json + 烘焙模型（BakedModel）表达，因此本对象在 1.21.1
 * **没有注册点**。这里只保留类名与原有绘制入口的方法名（便于按 1.7.10
 * 结构检索代码），方法体降级为空实现；原方法里那些只存在于 1.7.10 的参数
 * 类型（`RenderBlocks`、`Block`、`metadata`）已整体删除——1.21.1 侧没有
 * 任何调用方，保留它们只会引入已删除的类。
 *
 * ==1.21.1 应该由谁承担==
 *  - 静态外形（底座 / 细颈 / 顶盖）：拆成三个 element 的模型 json，
 *    放在 `assets/opencomputers_neo/models/block` 目录下（文档注释里不能
 *    写出连续两星的目录写法，否则会被 Scala 当成嵌套注释），由
 *    `blockstates/assembler.json` 引用；需要按朝向切换的变体用
 *    `variants` 表达。
 *  - 装配中的发光顶面 / 侧面：属于**动态**状态，由
 *    `client/renderer/tileentity/AssemblerRenderer` 承担（该文件由另一个
 *    代理移植，本文件不改它）。1.7.10 里那两段
 *    `setOverrideBlockTexture(Textures.Assembler.iconTopOn / iconSideOn)`
 *    对应 `Textures.Block.AssemblerTopOn` / `Textures.Block.AssemblerSideOn`，
 *    1.21.1 应当在 `BlockEntityRenderer` 里用
 *    `RenderUtil.sprite(...)` 取精灵后交给 `RenderUtil.drawSpriteQuad`。
 */
object Assembler {

  /**
   * 原方法：在**世界里**画装配机方块的三段体素。
   *
   * 1.21.1 由 `blockstates/assembler.json` + 烘焙模型承担静态部分，
   * 动态发光面由 `client.renderer.tileentity.AssemblerRenderer` 承担。
   */
  def render(): Unit = {
    // TODO(blk): 1.21.1 无 ISimpleBlockRenderingHandler 注册点，保留签名的空实现。
  }
}
