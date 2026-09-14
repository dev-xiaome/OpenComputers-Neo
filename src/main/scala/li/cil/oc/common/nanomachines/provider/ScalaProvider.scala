package li.cil.oc.common.nanomachines.provider

import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.prefab.AbstractProvider
import li.cil.oc.common.nanomachines.Implicits._
import net.minecraft.world.entity.player.Player

/**
 * Scala 侧的 [[li.cil.oc.api.prefab.AbstractProvider]]：
 * 把「Scala 风格的 `Iterable`」适配成 API 要求的 Java `Iterable`。
 */
abstract class ScalaProvider(id: String) extends AbstractProvider(id) {
  def createScalaBehaviors(player: Player): Iterable[Behavior]

  override def createBehaviors(player: Player): java.lang.Iterable[Behavior] = asJavaIterable(createScalaBehaviors(player))
}
