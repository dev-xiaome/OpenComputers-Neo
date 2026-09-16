package li.cil.oc.client.renderer.block

/**
 * 网络分流器（NetSplitter）的静态方块几何。
 *
 * ==1.7.10 状态==
 * `NetSplitter.render(openSides, block, x, y, z, renderer)` 用 `RenderBlocks`
 * 把分流器画成一个**中空的立方体框架**：
 *  - 底面 4 块（12×5 的一圈，四角留空）；
 *  - 四个竖直的角柱（`0..5` / `11..16` 的 5×16×5 立柱）；
 *  - 顶面 4 块；
 *  - 六个方向的「门口」：每个方向画一段 6×6 的短柱，长度取决于该面
 *    是否开启（`openSides(dir)`）——开启时贯通到方块边界（`0/16` 或 `16/16`），
 *    关闭时只到 `2/16` / `14/16`。
 * 另有物品栏分支（所有门口都用「关闭」尺寸，六面显式补画）。
 *
 * ==1.21.1 状态==
 * 1.21.1 取消了 `ISimpleBlockRenderingHandler` / `RenderBlocks`，方块几何
 * 改由 blockstate json + 烘焙模型表达，因此本对象没有注册点。
 *
 * ==1.21.1 应该由谁承担==
 *  - 框架（底面 / 角柱 / 顶面，共 12 个 element）：静态 json，放在
 *    `assets/opencomputers_neo/models/block` 目录下作为
 *    `netsplitter.json`（文档注释里不能写连续两星的目录路径）。
 *  - 六个方向的「门口」长度：依赖每个面是否开启，属于**运行期状态**，
 *    需要 `blockstates/netsplitter.json` 的 multipart（六条 `when` 条件）
 *    配合 `BlockState` 上的布尔属性（common 侧），或由
 *    `client/renderer/tileentity/NetSplitterRenderer`（另一个代理移植）
 *    在模型基础上叠画短柱。**建议**：静态框架走 json，门口长度如果有
 *    对应的 `BlockState` 属性就走 multipart；否则留给
 *    `NetSplitterRenderer` 用 `RenderUtil.drawCube` 画。
 *  - 开启状态的发光贴图：`Textures.Block.NetSplitterOn`。
 */
object NetSplitter {

  /**
   * 原 `render(openSides, block, x, y, z, renderer)`：世界里按六个面的
   * 开闭状态画中空框架 + 六个方向的门口短柱。
   *
   * 1.21.1 由 `models/block/netsplitter.json`（静态框架）+
   * `client.renderer.tileentity.NetSplitterRenderer`（动态部分，由另一个代理移植）承担。
   */
  def render(): Unit = {
    // TODO(blk): 1.21.1 无 ISimpleBlockRenderingHandler 注册点，保留签名的空实现。
  }
}
