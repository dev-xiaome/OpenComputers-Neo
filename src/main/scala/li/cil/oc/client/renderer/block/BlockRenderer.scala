package li.cil.oc.client.renderer.block

/**
 * 方块渲染总调度（1.7.10 的 `ISimpleBlockRenderingHandler` 实现）。
 *
 * ==这个类为什么在 1.21.1 里只剩说明==
 * 1.7.10 里本对象实现了 `cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler`，
 * 由 `RenderingRegistry.registerBlockHandler(Settings.blockRenderId, BlockRenderer)`
 * 注册，再由 `RenderBlocks` 按「方块渲染 id」回调，承担两件事：
 *
 *  1. `renderWorldBlock`：按方块实体类型把世界里的方块几何分派给各协作类——
 *     装配机 → [[Assembler]]、线缆 → [[Cable]]、全息投影仪 → [[Hologram]]、
 *     键盘 → [[Keyboard]]、打印件 → [[Print]]、打印机 → [[Printer]]、
 *     机架 → [[Rack]]、网络分流器 → [[NetSplitter]]、交换器 → [[Transposer]]，
 *     其余走 `renderer.renderStandardBlock`；
 *  2. `renderInventoryBlock`：物品栏里的等价分派（`RenderHelper` /
 *     六面显式 `renderFaceXxx` / 机器人用 `RobotRenderer.renderChassis()`）。
 *
 * 另外还有一个 `PatchedRenderBlocks` 内部对象，覆写 `renderFaceXPos` /
 * `renderFaceZNeg` 临时翻转 `flipTexture`，用来绕开「自定义方块渲染器
 * 才出现」的贴图翻转 bug；`patchedRenderer` 负责把上游 `RenderBlocks` 的
 * 全部渲染状态字段复制到这个补丁渲染器上。
 *
 * ==1.21.1 的状态==
 * `ISimpleBlockRenderingHandler`、`RenderBlocks`、
 * `RenderingRegistry.registerBlockHandler`、`Settings.blockRenderId`
 * 这一整套机制在 1.21.1 **已被完全删除**。方块几何统一改由
 * blockstate json + 烘焙模型（`BakedModel`）表达，动态部分由
 * `BlockEntityRenderer` 承担。
 *
 * 因此本对象整体降级为「只留说明」，不再提供 `getRenderId` /
 * `renderWorldBlock` / `renderInventoryBlock` / `patchedRenderer` /
 * `renderFaceXxx` 等任何成员——它们引用的类都不存在了，保留空壳只会带来
 * 误导。实际的渲染落点见下：
 *
 *  | 1.7.10 分派目标 | 1.21.1 承担者 |
 *  | --- | --- |
 *  | `Assembler.render`（世界 / 物品栏） | 模型 json + `tileentity.AssemblerRenderer` |
 *  | `Cable.render` | multipart / 动态烘焙模型（邻居掩码） |
 *  | `Hologram.render` | 模型 json + `tileentity.HologramRenderer` |
 *  | `Keyboard.render` | 模型 json + blockstate variants 的旋转 |
 *  | `Print.render` | `tileentity.PrinterRenderer`（数据驱动几何） |
 *  | `Printer.render` | 模型 json + `tileentity.PrinterRenderer` |
 *  | `Rack.render` | 模型 json + `tileentity.RackRenderer` + `common.event.RackMountableRenderHandler` |
 *  | `NetSplitter.render` | 模型 json + `tileentity.NetSplitterRenderer` |
 *  | `Transposer.render` | 模型 json + `tileentity.TransposerRenderer` |
 *  | 默认 `renderStandardBlock` | 烘焙模型本身（无需代码） |
 *  | 机器人（`RobotProxy` / `RobotAfterimage`） | `tileentity.RobotRenderer` |
 *
 * 「物品栏 vs 世界」两套分支在 1.21.1 里也整体消失：烘焙模型不会像
 * `RenderBlocks` 那样漏画不可见面，物品栏直接复用方块模型
 * （`models/item/xxx.json` 的 `parent` 指向方块模型即可）。
 *
 * `Settings.blockRenderId` 字段在 1.21.1 里不再有任何含义
 * （见 `client/Proxy.clientSetup` 的说明），保留它只是为了避免改动 common 侧。
 */
object BlockRenderer {
  // 有意为空：本对象在 1.21.1 里没有任何可注册 / 可调用的行为。
}
