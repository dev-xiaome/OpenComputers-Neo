package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.Settings
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * 手册图片 URL 解析的公共工具。
 *
 * 手册页面里的图片写作 `![标题](前缀:名字@子类型)`，例如
 * `![芯片](item:opencomputers:chip1@0)`。前缀由 `api.Manual` 的 provider 表分发，
 * 传给 provider 的 `data` 就是 `opencomputers:chip1@0` 这一段。
 *
 * ==与 1.7.10 的差别==
 *  - 命名空间从 `opencomputers` 改成了 `opencomputers_neo`，而手册页面资源
 *    是从 1.7.10 机械迁移过来的、里面仍写着旧命名空间，因此这里统一做一次
 *    `opencomputers` → [[li.cil.oc.Settings.resourceDomain]] 的替换；
 *  - 1.21.1 取消了物品/方块的数字子类型（metadata），`@0` 这类后缀
 *    **直接忽略**（见各 provider 里的 TODO）。
 */
private[render] object ManualImages {
  /** 把手册里写的名字解析成 `ResourceLocation`；解析失败返回 `null`。 */
  def location(name: String): ResourceLocation = {
    val cleaned = if (name == null) "" else name.trim
    if (cleaned.isEmpty) return null
    val parsed = ResourceLocation.tryParse(cleaned)
    if (parsed == null) return null
    if (parsed.getNamespace == "opencomputers") {
      ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, parsed.getPath)
    }
    else parsed
  }

  /**
   * 取物品堆叠。
   *
   * TODO(manual): 1.7.10 的 `@子类型`（metadata）在 1.21.1 已由数据组件取代，
   *   这里直接忽略子类型；若手册确实需要区分变体，请给对应条目补上完整物品名。
   */
  def stack(name: String): ItemStack = {
    val rl = location(name)
    if (rl == null) return ItemStack.EMPTY
    val item = BuiltInRegistries.ITEM.get(rl)
    if (item == null || item == net.minecraft.world.item.Items.AIR) ItemStack.EMPTY
    else new ItemStack(item)
  }
}
