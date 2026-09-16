package li.cil.oc.client.gui

import li.cil.oc.Settings
import li.cil.oc.common.Tier
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu

/**
 * 槽位 / 等级图标（原 1.7.10 的 `li.cil.oc.client.gui.Icons`）。
 *
 * ==为什么整体降级==
 * 1.7.10 里本对象通过 `TextureStitchEvent` 把 `icons/` 下的图标注册进**方块图集**，
 * 并缓存返回的 `IIcon`。1.21.1 里：
 *  - 既没有 `TextureStitchEvent`（改为 `TextureAtlasSprite` 的按需加载，
 *    图标文件只要在 `assets/<namespace>/textures/<目录>` 下就会被自动收集）；
 *  - 也没有 `IIcon`，取而代之的是 [[net.minecraft.client.renderer.texture.TextureAtlasSprite]]；
 *  - 槽位背景图标的官方通道是 [[net.minecraft.world.inventory.Slot#getNoItemIcon]]
 *    返回的 `(图集位置, 精灵位置)` 二元组，而不是「注册时拿到句柄、渲染时再取」。
 *
 * 因此这里只保留**位置**的计算，图标句柄交给调用方在渲染时按需获取：
 * `Minecraft.getInstance.getTextureAtlas(Icons.atlas).apply(Icons.get(...))`。
 *
 * 命名规则与 1.7.10 完全一致：槽位类型 → `icons/<slotType>`；
 * 等级 → `Tier.None` 为 `icons/na`，`Tier.One`~`Tier.Three` 为 `icons/tier<等级数值>`。
 *
 * 注意：`common.container.SlotIcons` 已经做过同样的改造（容器层要给出
 * `getNoItemIcon`），两边规则必须保持一致，改动其中一边时请同步另一边。
 */
object Icons {
  /** 槽位图标所在的图集（原版方块图集）。 */
  val atlas: ResourceLocation = InventoryMenu.BLOCK_ATLAS

  /**
   * 兼容 1.7.10 的初始化入口。
   *
   * 1.21.1 不需要（也无法）在图集打补丁时抢注图标：图标位置是纯计算出来的，
   * 精灵由原版按需加载，因此这里是空实现，保留它只是为了不改动既有调用点。
   */
  def initialize(): Unit = ()

  /** 按槽位类型取图标位置（原 `Icons.get(slotType: String)`，原返回 `IIcon`）。 */
  def get(slotType: String): ResourceLocation =
    sprite(if (slotType == null || slotType.isEmpty) "none" else slotType)

  /**
   * 按等级取图标位置（原 `Icons.get(tier: Int)`，原返回 `IIcon`）。
   *
   * 原实现只给 `Tier.None` 与 `Tier.One`~`Tier.Three` 注册了图标，其余返回 `null`；
   * 1.21.1 的贴图查找不接受空位置，因此越界等级统一退化成 `icons/na`
   * （与原实现相比更保守，不会崩）。
   */
  def get(tier: Int): ResourceLocation = tier match {
    case Tier.None => sprite("na")
    case t if t >= Tier.One && t <= Tier.Three => sprite("tier" + t)
    case _ => sprite("na")
  }

  private def sprite(name: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "icons/" + name)
}
