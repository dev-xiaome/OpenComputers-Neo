package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/**
 * 纳米机器人物品数据（原 1.7.10 的 `NanomachineData`）。
 *
 * 1.21.1 迁移要点：
 *  - `hasKey` → `contains`
 *  - 原版有一个「以控制器为参数」的辅助构造器 `this(controller: ControllerImpl)`。
 *    `ControllerImpl` 在 `common/nanomachines`（尚未移植），直接写会引入未移植依赖，
 *    且结构化类型的辅助构造器会与 `this(stack: ItemStack)` 在擦除后签名冲突；
 *    因此改为伴生对象上的工厂方法 [[NanomachineData#fromController]]。
 *
 * TODO(纳米机器): `common/nanomachines.ControllerImpl` / `NeuralNetwork` 移植完成后，
 * 可把此处的结构化类型换成实际类型以获得编译期校验。
 */
class NanomachineData extends ItemData(Constants.ItemName.Nanomachines) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var uuid: String = ""
  var configuration: Option[CompoundTag] = None

  override def load(nbt: CompoundTag): Unit = {
    uuid = nbt.getString(Settings.namespace + "uuid")
    if (nbt.contains(Settings.namespace + "configuration")) {
      configuration = Option(nbt.getCompound(Settings.namespace + "configuration"))
    }
    else {
      configuration = None
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    nbt.putString(Settings.namespace + "uuid", uuid)
    configuration.foreach(tag => nbt.put(Settings.namespace + "configuration", tag))
  }
}

object NanomachineData {
  /** 控制器（玩家身上的纳米机器）需要提供的最小接口。 */
  type ControllerLike = {
    def uuid: String
    def configuration: { def save(nbt: CompoundTag, forItem: Boolean): Unit }
  }

  /** 以控制器为来源构造一份物品数据（替代原辅助构造器）。 */
  def fromController(controller: ControllerLike): NanomachineData = {
    val data = new NanomachineData()
    data.uuid = controller.uuid
    val nbt = new CompoundTag()
    controller.configuration.save(nbt, true)
    data.configuration = Option(nbt)
    data
  }
}
