package li.cil.oc.common.container

import com.mojang.datafixers.util.Pair

import li.cil.oc.Settings
import li.cil.oc.common.Tier
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu

/**
 * 槽位背景 / 等级图标（原 `li.cil.oc.client.gui.Icons`）。
 *
 * 1.7.10 里槽位背景是 `IIcon`，由 `TextureStitchEvent` 在方块图集里注册；
 * 1.21.1 改为 [[net.minecraft.world.inventory.Slot#getNoItemIcon]] 返回的
 * `(图集位置, 精灵位置)` 二元组，因此本对象只负责按名字拼出精灵位置。
 *
 * TODO(client.gui): `li.cil.oc.client.gui.Icons` 属于尚未移植的 `client` 包。
 * 移植后应当：
 *  - 在贴图集加载事件（`TextureStitchEvent.Pre`，目标是 `textures/atlas/blocks.png`）
 *    里把 `opencomputers_neo` 命名空间下 `icons` 目录的图标注册进图集；
 *  - 本对象改为转发到 `client.gui.Icons`（或直接删掉，由客户端直接调用）。
 * 在那之前这里只保留 1.7.10 的图标命名规则，保证容器层能给出稳定的精灵位置。
 *
 * 命名规则（与原实现一致）：槽位类型 → `icons/<slotType>`；
 * 等级 → `Tier.None` 为 `icons/na`，其余为 `icons/tier<等级数值>`。
 */
object SlotIcons {
  /** 图标所在图集（原版方块图集）。 */
  val atlas: ResourceLocation = InventoryMenu.BLOCK_ATLAS

  /**
   * `textures/item/icons/` 下真实存在的图标名（与 1.7.10 `TextureStitchEvent` 注册的那一批一致）：
   * `Slot.All` 的 12 个槽位类型 + `na` + `tier0..tier2`。
   *
   * 之所以要显式列出：1.7.10 的 `Icons.get` 未命中时返回 `null`，GUI 便不画背景；
   * 而 1.21.1 这条通道无法表达「不画」——`getNoItemIcon` 返回的精灵若指向不存在的路径，
   * `TextureAtlas#apply` 会回退成 `missingno`，也就是紫黑方格。所以未知名（例如
   * `Slot.None` / `Slot.Any` 这类没有专属图标的槽位）一律回退到真实存在的 `na`。
   */
  private val available = Set(
    "card", "component_bus", "container", "cpu", "eeprom", "floppy", "hdd",
    "memory", "rack_mountable", "tablet", "tool", "upgrade",
    "na", "tier0", "tier1", "tier2")

  /** 按槽位类型取图标（原 `Icons.get(slotType: String)`）。 */
  def get(slotType: String): ResourceLocation = {
    val name = if (slotType == null || slotType.isEmpty) "na" else slotType
    sprite(if (available.contains(name)) name else "na")
  }

  /**
   * 按等级取图标（原 `Icons.get(tier: Int)`）。
   *
   * 原实现只给 `Tier.None` 与 `Tier.One`~`Tier.Three` 注册了图标，其余返回 `null`；
   * 1.21.1 的 `getNoItemIcon()` 不接受空精灵（GUI 会直接去取贴图），
   * 因此越界等级统一退化成 `icons/na`（比原实现更保守，不会崩）。
   */
  def get(tier: Int): ResourceLocation = tier match {
    case Tier.None => sprite("na")
    case t if t >= Tier.One && t <= Tier.Three => sprite("tier" + t)
    case _ => sprite("na")
  }

  /** 槽位背景二元组（原 `Slot#setBackgroundIcon` / `getBackgroundIconIndex`）。 */
  def background(slotType: String): Pair[ResourceLocation, ResourceLocation] =
    Pair.of(atlas, get(slotType))

  /** 槽位背景二元组，按等级取图标。 */
  def background(tier: Int): Pair[ResourceLocation, ResourceLocation] =
    Pair.of(atlas, get(tier))

  /**
   * 拼出图集里的精灵位置。
   *
   * **必须带 `item/` 前缀**：1.21.1 的方块图集配置
   * （`assets/minecraft/atlases/blocks.json`，NeoForge 内置）对 `textures/item/` 下的贴图
   * 使用 `prefix: "item/"`，所以 `textures/item/icons/tier1.png` 对应的精灵名是
   * `opencomputers_neo:item/icons/tier1`。少这个前缀时 `TextureAtlas#apply` 会回退到
   * missingno —— 表现就是 GUI 里所有组件槽位背景变成**紫黑方格**。
   */
  private def sprite(name: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "item/icons/" + name)
}
