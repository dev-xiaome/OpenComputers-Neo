package li.cil.oc.client.renderer.block

import com.google.common.base.Strings
import li.cil.oc.client.renderer.tileentity.RenderUtil
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.resources.ResourceLocation

/**
 * 可打印方块（Print，即「3D 打印件」）的静态方块几何。
 *
 * ==1.7.10 状态==
 * `Print.render(data, state, facing, x, y, z, block, renderer)` 是一个
 * **完全数据驱动**的渲染：形状列表来自方块实体的 `PrintData`
 * （`data.stateOn` / `data.stateOff`，每个 `Shape` 带 `bounds`、`texture`、
 * `tint`）。对每个形状它做三件事：
 *  1. 把形状的 `bounds` 按方块朝向 `rotateTowards(facing)` 旋转；
 *  2. 把 `shape.tint` 与 `shape.texture` 写进 `common.block.Print` 的
 *     `colorMultiplierOverride` / `textureOverride` 字段（由方块侧查图标）；
 *  3. `renderer.setRenderBounds(bounds)` + `renderer.renderStandardBlock(...)`。
 * 形状列表为空时退化为一个整方块 + `missingno` 贴图。
 * 另有 `resolveTexture(name)`：从方块图集里按名字取 `IIcon`，取不到就用
 * `missingno`。
 *
 * ==1.21.1 状态==
 * 1.21.1 取消了 `ISimpleBlockRenderingHandler` / `RenderBlocks`（以及
 * `TextureMap#getTextureExtry`）。更要紧的是：`common.block.Print` 上
 * `textureOverride` / `colorMultiplierOverride` / `isSingleShape` 这套
 * 「用可变字段临时改方块贴图」的手法在烘焙模型体系下**完全失效**——
 * 方块模型是烘焙后按 `BlockState` 缓存复用的，不能按方块实体逐帧改。
 *
 * ==1.21.1 应该由谁承担==
 *  - 打印件外形**必须**由 `client/renderer/tileentity/` 下的
 *    `BlockEntityRenderer`（现成的 `PrinterRenderer` 或为 Print 新增的
 *    `PrintRenderer`，由另一个代理移植）承担：在
 *    `BlockEntityRenderer#render` 里读 `tileentity.Print` 的 `data` /
 *    `state` / `facing`，对每个 `Shape` 用 `RenderUtil.drawSpriteQuad`
 *    画出六个面。`bounds` 的旋转可直接复用
 *    `li.cil.oc.util.ExtendedAABB.rotateTowards`。
 *  - 静态部分（形状列表为空时的整方块 + `missingno`）：由
 *    `blockstates/print.json` 配合 `models/block/print.json` 表达。
 *  - 贴图：原 `Minecraft#getTextureMapBlocks#getTextureExtry(name)` 改为
 *    [[RenderUtil.sprite]]（按需查方块图集，资源包重载后自动生效）。
 */
object Print {

  /**
   * 原 `resolveTexture(name)`：按名字取方块图集精灵，取不到就回退 `missingno`。
   *
   * 1.21.1 里 `IIcon` 已被 `TextureAtlasSprite` 取代，`TextureMap#getTextureExtry`
   * 已被移除，因此改为用 [[RenderUtil.sprite]] 查询。
   *
   * @param name 贴图名；含 `:` 时按完整 `ResourceLocation` 解析，否则补上本模组
   *             命名空间与 `block/` 前缀
   * @return 图集精灵；图集尚未加载或名字无效时返回 `null`
   */
  def resolveTexture(name: String): TextureAtlasSprite = {
    if (Strings.isNullOrEmpty(name)) return null
    val location =
      if (name.contains(":")) ResourceLocation.parse(name)
      else RenderUtil.blockTexture(name)
    val sprite = RenderUtil.sprite(location)
    // 原实现的回退：`missingno`。
    if (sprite != null) sprite
    else RenderUtil.sprite(ResourceLocation.withDefaultNamespace("missingno"))
  }

  /**
   * 原 `render(data, state, facing, x, y, z, block, renderer)`：按形状列表画出打印件。
   *
   * 1.21.1 由带方块实体的 `BlockEntityRenderer` 承担（见文件头说明）；
   * 本对象不再有 `ISimpleBlockRenderingHandler` 注册点。
   */
  def render(): Unit = {
    // TODO(blk): 打印件是数据驱动的动态几何，必须搬到
    //  client/renderer/tileentity 下的 BlockEntityRenderer（另一个代理的文件），
    //  用 RenderUtil.drawSpriteQuad 逐形状绘制。这里保留签名的空实现。
  }

  /**
   * 原 `printBlock.isSingleShape = shapes.size == 1` 的判定。
   *
   * 1.21.1 的烘焙模型没有「单形状」快速路径，这里保留成纯函数，
   * 供移植后的 `BlockEntityRenderer` 参考。
   */
  def isSingleShape(shapeCount: Int): Boolean = shapeCount == 1
}
