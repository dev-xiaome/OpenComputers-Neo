package li.cil.oc.util

import li.cil.oc.api.internal
import li.cil.oc.api.network.{Component, Node}
import net.neoforged.neoforge.items.IItemHandler

import scala.language.reflectiveCalls

/**
 * 在网络上按地址查找数据库升级组件，并对其执行操作。
 *
 * 1.21.1 迁移说明（重要，待 [[li.cil.oc.server.component.UpgradeDatabase]] 移植后复查）：
 *  - 原实现直接以 `li.cil.oc.server.component.UpgradeDatabase` 作为参数类型并做类型匹配，
 *    但 `li.cil.oc.server` 包目前还没有加入 `gradle.properties` 的 `scala_ported_packages`，
 *    该类型不在编译范围内（会报 `object server is not a member of package li.cil.oc`）。
 *  - 因此这里改用 API 层已经存在的 [[li.cil.oc.api.internal.Database]] 做宿主判定，并用
 *    结构类型补上调用方需要的 `data` 成员。`UpgradeDatabase` 本来就实现了
 *    `internal.Database`，且其 `data` 是 `IItemHandler`（与
 *    [[ExtendedArguments.checkSlot]] 的参数类型一致），所以行为与原来的具体类型匹配等价。
 *  - `Node.network` / `Network.node(String)` / `Node.host` 与新版 API 同名，无需改动。
 *  - TODO(移植): `server` 包启用后，可以把 [[DatabaseUpgrade]] 直接换成
 *    `UpgradeDatabase`（或保留现状，二者等价），其余代码无需修改。
 */
object DatabaseAccess {
  /**
   * 数据库升级组件的结构类型：API 接口 + 调用方用到的 `data` 物品栏。
   * <br>
   * 之所以不直接写具体类，是为了在本包先于 `server` 包编译的过渡期内保持可用。
   */
  type DatabaseUpgrade = internal.Database { def data: IItemHandler }

  def withDatabase(node: Node, address: String, f: DatabaseUpgrade => Array[AnyRef]): Array[AnyRef] = {
    node.network.node(address) match {
      case component: Component => component.host match {
        case database: internal.Database => f(database.asInstanceOf[DatabaseUpgrade])
        case _ => throw new IllegalArgumentException("not a database")
      }
      case _ => throw new IllegalArgumentException("no such component")
    }
  }
}
