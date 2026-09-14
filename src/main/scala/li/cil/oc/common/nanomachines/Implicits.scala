package li.cil.oc.common.nanomachines

import li.cil.oc.api.network._
import net.minecraft.world.item.ItemStack

import scala.jdk.CollectionConverters._

/**
 * 纳米机器包内部的 Java 集合互操作辅助。
 *
 * 1.7.10 的代码依赖 `scala.collection.JavaConversions` 提供的 `asJavaIterable`，
 * Scala 2.13 已移除此对象；`li.cil.oc` 包对象只提供了一部分兼容函数
 * （`asJavaCollection` / `seqAsJavaList` …），这里补齐纳米机器代码用到的那一个。
 */
object Implicits {
  /** 把 Scala `Iterable` 包装成 Java `Iterable`（视图，惰性）。 */
  def asJavaIterable[A](iterable: Iterable[A]): java.lang.Iterable[A] =
    iterable.asJava
}
