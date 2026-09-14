package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.prefab.AbstractBehavior
import li.cil.oc.common.nanomachines.DamageSourceWithRandomCause
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player

/**
 * 「饥饿」行为：能量耗尽时反过来抽取玩家的饱和/饥饿值，并恢复一点纳米机器电量。
 *
 * 1.21.1 迁移要点：
 *  - 伤害来源改用本包的 [[DamageSourceWithRandomCause]]
 *    （1.7.10 的 `integration.util.DamageSourceWithRandomCause` 依赖旧的 `DamageSource(name)`
 *    构造器，1.21.1 已改为数据驱动的 `DamageType`）。
 *  - `player.attackEntityFrom(source, amount)` → `player.hurt(source, amount)`
 */
object HungryProvider extends ScalaProvider("d697c24a-014c-4773-a288-23084a59e9e8") {
  final val FillCount = 10 // 多造几个，提高被随机连接选中的概率。

  override def createScalaBehaviors(player: Player): Iterable[Behavior] = Iterable.fill(FillCount)(new HungryBehavior(player))

  override protected def readBehaviorFromNBT(player: Player, nbt: CompoundTag): Behavior = new HungryBehavior(player)

  class HungryBehavior(player: Player) extends AbstractBehavior(player) {
    /**
     * 饥饿时的伤害来源。
     *
     * 1.21.1 的 `DamageSource` 必须从注册表解析 `Holder[DamageType]`，
     * 而注册表随世界走（伤害类型是同步到客户端的数据包对象），
     * 因此这里按「行为所属玩家所在的世界」构造，而不是像旧版那样做成 object 级常量。
     */
    private lazy val hungryDamage = DamageSourceWithRandomCause(player.level(), "oc.nanomachinesHungry", 3).
      setDamageBypassesArmor().
      setDamageIsAbsolute()

    override def onDisable(reason: DisableReason): Unit = {
      if (reason == DisableReason.OutOfEnergy) {
        player.hurt(hungryDamage, Settings.get.nanomachinesHungryDamage.toFloat)
        api.Nanomachines.getController(player).changeBuffer(Settings.get.nanomachinesHungryEnergyRestored)
      }
    }
  }

}
