package li.cil.oc.client.renderer.block

/**
 * 线缆（Cable）的静态方块几何。
 *
 * ==1.7.10 状态==
 * `Cable.render(world, x, y, z, block, renderer)` 用 `RenderBlocks` 画三部分：
 *  1. 中心方块——半边长 `4/16/2`，即 8×8×8 像素的芯；
 *  2. 每个有连接的朝向各画一段「连接臂」，包围盒由 `setConnectedBounds`
 *     扩展到该方向的中点；
 *  3. 连出去但对面**不是**线缆时，再叠一个更小的「插头」（半边长
 *     `6/16/2 - 10e-5`）并在该方向画 `Textures.Cable.iconCap` 端盖；
 *     完全孤立的线缆则用 `setUnconnectedBounds` 画一圈封口。
 * 另有 `Cable.render(stack, renderer)` 负责物品栏里的线缆模型
 * （竖直的芯 + 上下端盖）。
 *
 * ==1.21.1 状态==
 * 1.21.1 取消了 `ISimpleBlockRenderingHandler` / `RenderBlocks`：方块几何
 * 由 blockstate json + 烘焙模型（BakedModel）表达。但线缆的连接形状
 * **依赖于邻居**（`common.block.Cable.neighbors(world, x, y, z)` 返回的位掩码），
 * 静态 json 无法表达这种「相邻即延长」的关系。
 *
 * ==1.21.1 应该由谁承担==
 *  - 静态「中心芯 + 六向连接臂」：1.21.1 的标准做法是
 *    `blockstates/cable.json` 写 **multipart**（多方）模型——六个方向各一条
 *    `when: { north: "true" }` 之类的条件，再加一个无条件的基础芯。
 *    这要求 `common.block.Cable` 的 `BlockState` 上带六个布尔属性
 *    （common 侧改动，本代理不可改），或改用 `ModelData` +
 *    `IDynamicBakedModel`。**建议落点**：`client/renderer/tileentity/` 下新增
 *    `CableRenderer` / `CableModel`，或更贴近 1.21.1 习惯地在客户端注册一个
 *    动态烘焙模型加载器（`RegisterModelLoadersEvent`）；本文件不改别的代理的文件。
 *  - 插头 / 端盖：`Textures.Block.CableCap`（原 `Textures.Cable.iconCap`），
 *    做成独立 element 或独立模型 json。
 *  - 物品栏模型：`item/cable.json`。1.7.10 的物品栏分支完全是为了绕开
 *    `RenderBlocks` 不补画不可见面，烘焙模型没有这个问题。
 */
object Cable {

  /**
   * 原 `render(world, x, y, z, block, renderer)`：在**世界里**按邻居掩码拼线缆几何。
   *
   * 原参数 `RenderBlocks` / `Block` / 世界坐标都只存在于 1.7.10，已整体删除；
   * 1.21.1 侧没有任何调用方。
   */
  def render(): Unit = {
    // TODO(blk): 需要「连接掩码 → multipart / 动态模型」的客户端模型，
    //  见文件头的说明。1.21.1 没有 ISimpleBlockRenderingHandler 注册点。
  }
}
