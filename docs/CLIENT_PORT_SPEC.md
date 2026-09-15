# `li.cil.oc.client` 移植约定（工作稿）

本文件是 `client/**` 移植期间各施工方共用的接口约定，**不是最终文档**，完成后可删。

## 背景

- 目标工程：`D:\Workspace\Mods\1.21.1\OpenComputers Neo`（MC 1.21.1 / NeoForge 21.1.244 / Scala 2.13.14）
- 只读参考：`mod_src\OpenComputers-master-MC1.7.10\src\main\scala\li\cil\oc\client\`
- 目标目录 `src\main\scala\li\cil\oc\client\` 里的文件是 `tools/port-rewrite.ps1` 机械改写过的一版：
  **包名/类名映射已做过，但渲染 API 仍是 1.7.10 的写法**（`Tessellator`/`GL11`/`RenderBlocks`/`GuiScreen`…），
  所以每个文件都要按 1.21.1 重写，不是小修小补。

## 硬性约束

1. **禁止运行 gradle**。自检只用：
   ```powershell
   Remove-Item "$env:TEMP\oc-scalac-out" -Recurse -Force -ErrorAction SilentlyContinue
   cd "D:\Workspace\Mods\1.21.1\OpenComputers Neo"
   .\tools\scalac-check-full.ps1 -Packages "li/cil/oc/*.scala,li/cil/oc/util/**,li/cil/oc/common/**,li/cil/oc/client/**" -Log "$env:TEMP\cli.log"
   ```
   只看 `client\` 开头的报错行（`common\` 里的报错来自尚未移植的 `server/**`，与本次无关）。
2. 只改/建 `src\main\scala\li\cil\oc\client\**`。**不要动** `common/**`、`server/**`、`integration/**`、
   `OpenComputersNeo.scala`、`common/init/Registry.scala`、`gradle.properties`。
3. 新增注释用**简体中文**。**Scala 块注释可嵌套**：doc 注释里绝不能出现 `/*` 序列（会提前闭合注释）。
4. 用 `javap -cp "D:\Workspace\Mods\1.21.1\OpenComputers Neo\build\moddev\artifacts\neoforge-21.1.244-merged.jar" <类全名>`
   核实 API，不要凭记忆。
5. 允许降级：保留类名、方法签名与调用点，内部实现可以简化，但要写 `// TODO(client): 简体中文说明`。

## 关键 API 速查（已 javap 核实）

```scala
// 方块实体渲染器
trait BlockEntityRenderer[T <: BlockEntity] {
  def render(be: T, partialTicks: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int): Unit
  def getViewDistance: Int = 64          // 可选覆写
  def shouldRenderOffScreen(be: T): Boolean = false
}
// 注册（mod 事件总线）
//   event.registerBlockEntityRenderer(blockEntityType, new BlockEntityRendererProvider[T] {
//     override def create(ctx: BlockEntityRendererProvider.Context): BlockEntityRenderer[T] = ...
//   })

// 几何
pose.pushPose(); pose.translate(dx, dy, dz); pose.scale(sx, sy, sz); pose.mulPose(q: Quaternionf); pose.popPose()
// 顶点
val vc = buffers.getBuffer(RenderType.cutout())   // 或 translucent() / solid() / entityCutoutNoCull(tex)
vc.addVertex(pose.last(), x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose.last(), nx, ny, nz)

// 精灵
Minecraft.getInstance.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(rl)   // 方块图集
sprite.getU0/getU1/getV0/getV1
```

**NeoForge 1.21.1 没有 `TextureStitchEvent`**：精灵一律用上面的图集查询按需获取，不要缓存到注册事件里。

## 新增的共享工具（由主线维护，其他施工方直接用）

```scala
package li.cil.oc.client.renderer.tileentity

object RenderUtil {
  def shouldShowErrorLight(hash: Int): Boolean          // 原有
  def drawQuad(pose: PoseStack, vc: VertexConsumer,
               x0: Double, y0: Double, z0: Double,
               x1: Double, y1: Double, z1: Double,
               x2: Double, y2: Double, z2: Double,
               x3: Double, y3: Double, z3: Double,
               u0: Float, v0: Float, u1: Float, v1: Float,
               light: Int, overlay: Int): Unit          // 逆时针四顶点，UV 顺序同上
  def normal(pose: PoseStack, vc: VertexConsumer, nx: Float, ny: Float, nz: Float): Unit
  def sprite(rl: ResourceLocation): TextureAtlasSprite   // 方块图集查询，未加载返回 null
  def blockLight(level: Level, pos: BlockPos): Int       // 光照值（left << 16 | right << 8）
}
```

## 计划新增的模组侧入口（主线负责，其他施工方只调用）

```scala
package li.cil.oc.client
object ClientSetup {                 // 客户端专用注册与事件监听全部集中在这里
  def initialize(modBus: IEventBus): Unit
  def registerGameEvents(): Unit
}
```

`Textures.scala` 保持「`ResourceLocation` 常量表」的角色，**不再有 `IIcon` 与 `init(TextureManager)`**；
原来那些 `Textures.Xxx.iconYyy` 改成 `Textures.Xxx.yyy`（类型 `ResourceLocation`），取精灵用 `RenderUtil.sprite(...)`。

## 图标/字体常量

- 资源命名空间 `Settings.resourceDomain`（= `opencomputers_neo`）
- 方块纹理目录已改为 `textures/block/**`（见 `docs/PROGRESS.md` 的资源迁移记录）；GUI 仍是 `textures/gui/**`
- 字体贴图 `textures/font/chars.png` / `chars_aliased.png`
