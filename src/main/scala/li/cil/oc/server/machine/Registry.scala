package li.cil.oc.server.machine

import java.util

import li.cil.oc.OpenComputers
import li.cil.oc.api.machine.Value

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.math.ScalaNumber

/**
 * `server/driver/Registry` 在 `server/machine` 内的本地替身。
 *
 * 迁移原因：`Machine` / `UserdataAPI` 都需要把回调返回值转换成可推送给 Lua 的
 * “简单值”（`Registry.convert`）。真正实现在 `li.cil.oc.server.driver.Registry`，
 * 但该文件目前还引用未移植完成的 `CompoundBlockDriver` / `common/block`，
 * 无法进入编译集。
 *
 * 转换逻辑本身与上游 `Registry.convertRecursively` 完全一致（含 memo 处理），
 * 唯一降级点是：注册的 [[li.cil.oc.api.driver.Converter]] 集合暂时取不到
 * （API 层没有暴露 `converters` 列表），因此自定义类型转换器不参与转换，
 * 这时对象会退回 `toString`——与上游“没有转换器命中”的分支行为一致。
 *
 * TODO(server.driver): `server/driver/Registry.scala` 移植完成后，把
 * `Machine.scala` / `luaj/UserdataAPI.scala` 的 `Registry` 引用改回
 * `li.cil.oc.server.driver.Registry`，并删除本文件。
 */
private[oc] object Registry {

  def convert(value: Array[AnyRef]): Array[AnyRef] =
    if (value != null) value.map(arg => convertRecursively(arg, new util.IdentityHashMap())) else null

  def convertRecursively(value: Any, memo: util.IdentityHashMap[AnyRef, AnyRef], force: Boolean = false): AnyRef = {
    val valueRef = value match {
      case number: ScalaNumber => number.underlying
      case reference: AnyRef => reference
      case null => null
      case primitive => primitive.asInstanceOf[AnyRef]
    }
    if (!force && memo.containsKey(valueRef)) {
      memo.get(valueRef)
    }
    else valueRef match {
      case null => null

      case arg: java.lang.Boolean => arg
      case arg: java.lang.Byte => arg
      case arg: java.lang.Character => arg
      case arg: java.lang.Short => arg
      case arg: java.lang.Integer => arg
      case arg: java.lang.Long => arg
      case arg: java.lang.Float => arg
      case arg: java.lang.Double => arg
      case arg: java.lang.Number => Double.box(arg.doubleValue)
      case arg: java.lang.String => arg

      case arg: Array[Boolean] => arg
      case arg: Array[Byte] => arg
      case arg: Array[Character] => arg
      case arg: Array[Short] => arg
      case arg: Array[Integer] => arg
      case arg: Array[Long] => arg
      case arg: Array[Float] => arg
      case arg: Array[Double] => arg
      case arg: Array[String] => arg

      case arg: Value => arg

      case arg: Array[_] => convertList(arg.asInstanceOf[AnyRef], arg.zipWithIndex.iterator, memo)
      case arg: Product => convertList(arg.asInstanceOf[AnyRef], arg.productIterator.zipWithIndex, memo)
      case arg: Seq[_] => convertList(arg.asInstanceOf[AnyRef], arg.zipWithIndex.iterator, memo)

      case arg: Map[_, _] => convertMap(arg, arg, memo)
      case arg: mutable.Map[_, _] => convertMap(arg, arg.toMap, memo)
      case arg: java.util.Map[_, _] =>
        // Scala 2.13：Java 集合不再隐式转换为 Scala 集合，需要显式 asScala。
        convertMap(arg, new util.HashMap[AnyRef, AnyRef](arg.asInstanceOf[util.Map[AnyRef, AnyRef]]).asScala.toMap, memo)

      case arg: Iterable[_] => convertList(arg.asInstanceOf[AnyRef], arg.zipWithIndex.toIterator, memo)
      case arg: java.lang.Iterable[_] => convertList(arg.asInstanceOf[AnyRef], arg.asScala.zipWithIndex.iterator, memo)

      case arg =>
        // TODO(server.driver): 上游此处会遍历注册的 Converter；API 层未暴露该列表，
        // 因此这里直接走“无转换器”分支。
        memo.put(arg, arg.toString)
        arg.toString
    }
  }

  def convertList(obj: AnyRef, list: Iterator[(Any, Int)], memo: util.IdentityHashMap[AnyRef, AnyRef]): Array[AnyRef] = {
    val converted = mutable.ArrayBuffer.empty[AnyRef]
    memo.put(obj, converted)
    for ((value, _) <- list) {
      converted += convertRecursively(value, memo)
    }
    converted.toArray
  }

  def convertMap(obj: AnyRef, map: Map[_, _], memo: util.IdentityHashMap[AnyRef, AnyRef]): AnyRef = {
    val target: mutable.Map[AnyRef, AnyRef] = memo.get(obj) match {
      case m: mutable.Map[AnyRef, AnyRef]@unchecked => m
      case m: java.util.Map[AnyRef, AnyRef]@unchecked => m.asScala
      case _ =>
        val created = mutable.Map.empty[AnyRef, AnyRef]
        memo.put(obj, created)
        created
    }
    map.foreach {
      case (key: AnyRef, value: AnyRef) => target += convertRecursively(key, memo) -> convertRecursively(value, memo)
      case _ =>
    }
    memo.get(obj)
  }
}
