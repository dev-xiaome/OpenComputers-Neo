package li.cil.oc.client.renderer.block

/**
 * 键盘（Keyboard）的静态方块几何。
 *
 * ==1.7.10 状态==
 * 本对象本身几乎不画几何，它做的是**贴图 UV 旋转**：
 * `Keyboard.render(keyboard, x, y, z, block, renderer)` 读取方块实体的
 * `facing` / `yaw`，据此设置 `RenderBlocks.uvRotateTop` /
 * `uvRotateBottom`（键盘朝上时旋转顶面贴图，朝下时旋转底面），
 * 朝向为 `DOWN` 时还要 `flipTexture = true`，最后调用
 * `renderer.renderStandardBlock(...)` 画一个普通立方体，再把
 * `uvRotateTop` / `uvRotateBottom` / `flipTexture` 复位。
 * 也就是说：键盘的**外形就是一个完整立方体**，唯一特殊的是贴图朝向。
 *
 * ==1.21.1 状态==
 * 1.21.1 取消了 `ISimpleBlockRenderingHandler` / `RenderBlocks`（连同
 * `uvRotateTop` / `flipTexture` 这些字段），方块几何改由 blockstate json +
 * 烘焙模型表达，因此本对象没有注册点。
 *
 * ==1.21.1 应该由谁承担==
 *  - 键盘外形：`models/block/keyboard.json`，就是一个 `from=[0,0,0]`、
 *    `to=[16,16,16]` 的 element（或直接 `parent` 指向 `block/cube`），
 *    六面各自的贴图在 json 的 `textures` 段里指定。
 *  - **贴图朝向**（原 `uvRotateTop/uvRotateBottom/flipTexture`）：
 *    用 `blockstates/keyboard.json` 的 `variants` + 模型的
 *    `x` / `y` 旋转（`"y": 90` 之类）表达。朝向为 `UP` 时把模型绕
 *    Y 轴按 `yaw` 旋转；朝向为 `DOWN` 时额外加 `"x": 180`。
 *    注意 1.21.1 的 `Direction` 顺序是 `DOWN, UP, NORTH, SOUTH, WEST, EAST`，
 *    与 1.7.10（`DOWN, UP, NORTH, SOUTH, WEST, EAST`）一致，可以直接照搬
 *    原 `yaw match` 的四条分支映射到 `y` 角度 `0 / 180 / 270 / 90`。
 *  - 按键按下时的动态高亮属于动态状态，由
 *    `client/renderer/tileentity/` 下对应的 `BlockEntityRenderer` 承担
 *    （键盘目前没有专用渲染器，若需要按键高亮建议新增）。
 */
object Keyboard {

  /**
   * 原 `render(keyboard, x, y, z, block, renderer)`：按 `facing` / `yaw`
   * 设置顶底面 UV 旋转后画一个标准立方体，返回 `renderStandardBlock` 的结果。
   *
   * 1.21.1 由 `blockstates/keyboard.json` 的 `variants` + 模型旋转承担。
   */
  def render(): Unit = {
    // TODO(blk): 1.21.1 无 uvRotateTop / flipTexture，也无 ISimpleBlockRenderingHandler
    //  注册点；朝向改由 blockstate variants 的模型旋转表达，见文件头说明。
  }
}
