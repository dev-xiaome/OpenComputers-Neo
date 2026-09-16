package li.cil.oc.integration.vanilla

import li.cil.oc.OpenComputers

import java.lang.reflect.Field
import scala.collection.mutable

/**
 * 原版方块实体字段的反射读取工具。
 *
 * 1.21.1 把若干原版方块实体的运行状态字段改成了**包私有**（package-private），
 * 例如 `AbstractFurnaceBlockEntity#litTime`、`BrewingStandBlockEntity#brewTime`、
 * `BeaconBlockEntity#levels`，外部包既没有 getter 也无法直接访问；
 * `BaseSpawner#nextSpawnData` 同样是私有的。
 * 这些字段正好是 OC 驱动需要暴露给 Lua 的信息，因此这里统一用反射读取
 * （原版 jar 在 classpath 上属于 unnamed module，`setAccessible` 不受模块系统限制）。
 *
 * 全部访问都做了缓存与异常兜底：字段找不到时不抛异常，只记一条 debug 日志并返回默认值，
 * 这样即便将来原版重命名字段也不会把整个驱动拖崩。
 */
private[vanilla] object VanillaReflection {
  private val cache = mutable.Map.empty[(Class[_], String), Option[Field]]

  private def find(clazz: Class[_], name: String): Option[Field] =
    cache.getOrElseUpdate((clazz, name), {
      var current: Class[_] = clazz
      var result: Option[Field] = None
      while (current != null && result.isEmpty) {
        try {
          val field = current.getDeclaredField(name)
          field.setAccessible(true)
          result = Some(field)
        }
        catch {
          case _: NoSuchFieldException => current = current.getSuperclass
          case _: SecurityException => current = null
        }
      }
      if (result.isEmpty) {
        OpenComputers.log.debug(s"Could not find field '$name' on ${clazz.getName} (see VanillaReflection).")
      }
      result
    })

  /** 读取 `int` 字段；读取失败时返回 `default`。 */
  def int(target: AnyRef, name: String, default: Int = 0): Int =
    if (target == null) default
    else find(target.getClass, name).fold(default) { field =>
      try field.getInt(target)
      catch {
        case t: Throwable =>
          OpenComputers.log.debug(s"Error reading int field '$name' on ${target.getClass.getName}.", t)
          default
      }
    }

  /**
   * 读取任意引用类型字段；读取失败或字段为 `null` 时返回 `null`。
   *
   * 泛型参数只是方便调用方书写，运行期一律按擦除后的类型返回。
   */
  def obj[T](target: AnyRef, name: String): T =
    if (target == null) null.asInstanceOf[T]
    else find(target.getClass, name).fold(null.asInstanceOf[T]) { field =>
      try field.get(target).asInstanceOf[T]
      catch {
        case t: Throwable =>
          OpenComputers.log.debug(s"Error reading field '$name' on ${target.getClass.getName}.", t)
          null.asInstanceOf[T]
      }
    }
}
