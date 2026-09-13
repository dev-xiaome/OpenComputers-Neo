package li.cil

import li.cil.oc.util.ExtendedItemStack

import scala.jdk.CollectionConverters._

/**
 * `li.cil.oc` 及其子包的公共隐式转换与 Java 集合互操作兼容层。
 * 放在包对象里，子包内无需显式 import 即可使用。
 */
package object oc extends ExtendedItemStack {
  // ---- scala.collection.JavaConversions 兼容层（Scala 2.13 已移除） ----

  def mapAsScalaMap[A, B](m: java.util.Map[A, B]): scala.collection.mutable.Map[A, B] = m.asScala

  def mapAsJavaMap[A, B](m: scala.collection.Map[A, B]): java.util.Map[A, B] = m.asJava

  def asScalaBuffer[A](l: java.util.List[A]): scala.collection.mutable.Buffer[A] = l.asScala

  def asJavaCollection[A](i: Iterable[A]): java.util.Collection[A] = scala.jdk.javaapi.CollectionConverters.asJavaCollection(i)

  def seqAsJavaList[A](s: Seq[A]): java.util.List[A] = s.asJava

  def bufferAsJavaList[A](b: scala.collection.mutable.Buffer[A]): java.util.List[A] = b.asJava

  def asScalaIterator[A](i: java.util.Iterator[A]): Iterator[A] = i.asScala

  def collectionAsScalaIterable[A](c: java.util.Collection[A]): Iterable[A] = c.asScala

  def setAsJavaSet[A](s: Set[A]): java.util.Set[A] = s.asJava

  def asScalaSet[A](s: java.util.Set[A]): scala.collection.mutable.Set[A] = s.asScala
}
