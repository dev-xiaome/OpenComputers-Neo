package li.cil.oc.common

import li.cil.oc.OpenComputers
import net.minecraft.resources.ResourceLocation

import java.util.Locale

/** The pride flags that may be displayed on a robot.
  *
  * Keep this list common-side so recipes, the robot API, persistence, and the
  * client renderer all agree on the same allow-list.
  */
object RobotFlags {
  final case class Flag(name: String, id: ResourceLocation, height: Float)

  private def flag(name: String, height: Float = 6f): Flag =
    Flag(name, ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, name + "_flag"), height)

  val Progress = flag("progress")
  val Lesbian = flag("lesbian", 5f)
  val Bisexual = flag("bisexual")
  val Pansexual = flag("pansexual")
  val Asexual = flag("asexual")
  val Rainbow = flag("rainbow")
  val Trans = flag("trans", 5f)

  val All: Seq[Flag] = Seq(Progress, Lesbian, Bisexual, Pansexual, Asexual, Rainbow, Trans)

  private val flagsById = All.map(value => value.id -> value).toMap
  private val flagsByName = All.flatMap(value => Seq(
    value.name -> value,
    value.id.getPath -> value,
    value.id.toString -> value
  )).toMap

  def byId(id: ResourceLocation): Option[Flag] = Option(id).flatMap(flagsById.get)

  def byName(value: String): Option[Flag] =
    Option(value).flatMap(name => flagsByName.get(name.trim.toLowerCase(Locale.ROOT)))
}
