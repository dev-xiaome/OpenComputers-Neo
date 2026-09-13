package li.cil

import li.cil.oc.common.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

import scala.jdk.CollectionConverters._

/**
 * `li.cil.oc` 及其子包的公共隐式转换与 Java 集合互操作兼容层。
 * 放在包对象里，子包内无需显式 import 即可使用。
 *
 * 注意：隐式类必须**直接定义**在包对象里；如果只是 `extends` 一个 trait，
 * Scala 2.13 不会把这些隐式暴露给子包。
 */
package object oc {
  // ---- ItemStack 的 NBT 访问（1.21.1 改为数据组件） ----

  implicit class ItemStackNBT(private val stack: ItemStack) {
    /** 等价于 1.7.10 的 `ItemStack#getTagCompound`。 */
    def getTag(): CompoundTag =
      if (stack == null || stack.isEmpty) null else stack.get(DataComponents.NBT.get())

    /** 等价于 1.7.10 的 `ItemStack#hasTagCompound`。 */
    def hasTag(): Boolean = getTag() != null

    /** 等价于 1.7.10 的 `ItemStack#setTagCompound`。 */
    def setTag(tag: CompoundTag): Unit =
      if (stack != null && !stack.isEmpty) stack.set(DataComponents.NBT.get(), tag)
  }

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
