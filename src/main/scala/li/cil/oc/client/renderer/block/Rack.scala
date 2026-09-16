package li.cil.oc.client.renderer.block

/**
 * 机架（Rack）的静态方块几何。
 *
 * ==1.7.10 状态==
 * `Rack.render(rack, x, y, z, block, renderer)` 用 `RenderBlocks` 拼机架外形：
 *  - 顶部与底部各一块满宽平板（`0..1 × 0..2/16 × 0..1` 与 `0..1 × 14/16..1 × 0..1`）；
 *  - 四个侧面：朝前（`rack.facing`）的那一面按 4 个槽位逐个判断
 *    `rack.getStackInSlot(i) != null`，有卡的话画一块 `3/16` 高的面板，
 *    并且在画之前 **post 一个 `RackMountableRenderEvent.Block`**
 *    （带 `rack` / 槽位 `i` / `rack.lastData(i)` / 侧面 / `renderer`），
 *    事件没被取消时读取 `event.getFrontTextureOverride` 写进
 *    `block.frontOverride` 当作正面贴图；
 *  - 其余三面画侧板；背面额外用 `Textures.Rack.icons(...)` 覆盖贴图。
 *
 * ==1.21.1 状态==
 * 1.21.1 取消了 `ISimpleBlockRenderingHandler` / `RenderBlocks`。机架的
 * 静态外形（顶板 / 底板 / 四面板）完全可以由 blockstate json + 烘焙模型
 * 表达，因此本对象没有注册点。
 *
 * ==1.21.1 应该由谁承担==
 *  - 静态外形：`models/block/rack.json`（顶板 + 底板 + 四侧板，共 6 个
 *    element）+ `blockstates/rack.json`（按 `facing` 旋转）。
 *  - **槽位面板与挂载物**：属于动态状态，由
 *    `client/renderer/tileentity/RackRenderer` 承担（该文件由另一个代理移植）。
 *    注意 1.21.1 的 `RackMountableRenderEvent.Block` 构造器签名已经变了：
 *    现在是
 *    `new RackMountableRenderEvent.Block(rack, mountable, data, side, poseStack)`，
 *    不再接收 `RenderBlocks`，而是在 `pose` 字段里给出 `PoseStack`；
 *    覆盖贴图也从 `ResourceLocation` 通过 `setFrontTextureOverride` 设置。
 *    现成的参考实现见 `li.cil.oc.common.event.RackMountableRenderHandler`。
 *  - 背面贴图：`Textures.Block.RackIcons`（索引与 `Direction#ordinal` 对齐）。
 */
object Rack {

  /**
   * 原 `render(rack, x, y, z, block, renderer)`：世界里画机架外形，
   * 并对 4 个槽位 post `RackMountableRenderEvent.Block`。
   *
   * 1.21.1 由 `blockstates/rack.json`（静态）+ `client.renderer.tileentity.RackRenderer`
   * 和 `common.event.RackMountableRenderHandler`（动态 / 事件）承担。
   */
  def render(): Unit = {
    // TODO(blk): 1.21.1 无 ISimpleBlockRenderingHandler 注册点；
    //  RackMountableRenderEvent.Block 的构造器已改为接收 PoseStack，
    //  事件 post 的落点是 client.renderer.tileentity.RackRenderer。
  }
}
