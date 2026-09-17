package li.cil.oc.server.machine

import li.cil.oc.api
import net.minecraft.nbt.CompoundTag

/**
 * 架构侧 Lua API 的公共基类，方法名与 OCCE `server/machine/ArchitectureAPI.scala` 逐字对齐。
 *
 * 注意方法名是 `loadData` / `saveData`，不是 `load` / `save`：OCCE 为区分「架构自身的
 * 状态数据」与 `api.machine.Architecture#load/save`（在 CE-1.20 里同样是
 * `loadData` / `saveData`，见 `src/main/java/li/cil/oc/api/machine/Architecture.java`），
 * 在 1.4 之后统一改成了带 `Data` 后缀的名字。
 *
 * 本类与 `api.machine.Architecture` **没有**继承关系（`LuaJLuaArchitecture` 自己直接实现
 * 那个接口，见 `machine/luaj` 包），因此这两个方法是本类自有的钩子，不与接口方法冲突。
 */
abstract class ArchitectureAPI(val machine: api.machine.Machine) {
  protected def node = machine.node

  protected def components = machine.components

  def initialize(): Unit

  def loadData(nbt: CompoundTag): Unit = {}

  def saveData(nbt: CompoundTag): Unit = {}
}
