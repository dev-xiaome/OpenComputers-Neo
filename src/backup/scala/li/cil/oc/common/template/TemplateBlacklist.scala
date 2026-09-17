package li.cil.oc.common.template

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.{Item, ItemStack}

import scala.jdk.CollectionConverters._

/**
 * 装配机黑名单（对应 1.7.10 的 `common.template.TemplateBlacklist`）。
 *
 * 1.21.1 迁移要点：
 *  - 物品注册表由 `Item.itemRegistry` 改为 `BuiltInRegistries.ITEM`，
 *    注册名一律是 `namespace:path`（[[ResourceLocation#tryParse]] 会把缺省命名空间补成 `minecraft`）；
 *  - 1.21.1 已没有 damage 子类型，配置里的 `id@meta` 后缀只保留解析兼容、不再参与比较，
 *    比较改用 `ItemStack.isSameItem`（等价于「同物品」判定，忽略数据组件）。
 */
object TemplateBlacklist {
  private lazy val TheBlacklist = {
    // scnr
    val pattern = """^([^@]+)(?:@(\d+))?$""".r
    def parseDescriptor(id: String, meta: Int) = {
      val location = ResourceLocation.tryParse(id)
      val item: Item = if (location == null) null else BuiltInRegistries.ITEM.getOptional(location).orElse(null)
      if (item == null) {
        OpenComputers.log.warn(s"Bad assembler blacklist entry '$id', unknown item id.")
        None
      }
      else {
        // 1.21.1 没有子类型，`@meta` 已无意义：非零时提示一次，避免配置被误认为生效。
        if (meta != 0) {
          OpenComputers.log.debug(s"Ignoring damage value in assembler blacklist entry '$id@$meta' (not supported in 1.21.1).")
        }
        Option(new ItemStack(item))
      }
    }
    Settings.get.assemblerBlacklist.asScala.map {
      case pattern(id, null) => parseDescriptor(id, 0)
      case pattern(id, meta) => try parseDescriptor(id, meta.toInt) catch {
        case _: NumberFormatException =>
          OpenComputers.log.warn(s"Bad assembler blacklist entry '$id@$meta', invalid damage value.")
          None
      }
      case badFormat =>
        OpenComputers.log.warn(s"Bad assembler blacklist entry '$badFormat', invalid format (should be 'id' or 'id@damage').")
        None
    }.collect {
      case Some(stack) => stack
    }.toArray
  }

  def register(): Unit = {
    api.IMC.registerAssemblerFilter("li.cil.oc.common.template.TemplateBlacklist.filter")
  }

  def filter(stack: ItemStack): Boolean = {
    stack == null || stack.isEmpty || !TheBlacklist.exists(entry => ItemStack.isSameItem(entry, stack))
  }
}
