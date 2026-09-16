package li.cil.oc.server.machine

import java.util

import com.google.common.base.Charsets
import li.cil.oc.api.machine.Arguments
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

import scala.jdk.CollectionConverters._
import scala.collection.mutable

class ArgumentsImpl(val args: Seq[AnyRef]) extends Arguments {
  // Scala 2.13：Java 接口要求 `java.util.Iterator`，Scala 的 Iterator 需要显式转换。
  def iterator(): util.Iterator[AnyRef] = args.iterator.asJava

  def count() = args.length

  def checkAny(index: Int) = {
    checkIndex(index, "value")
    args(index) match {
      // Scala 2.13：`Unit` 不能再当作模式/值使用（Unit companion object is not allowed）。
      // 原语义是把「nil」统一成 null：null、None，以及装箱后的 unit 值 `()`。
      case null | None | _: scala.runtime.BoxedUnit => null
      case arg => arg
    }
  }

  def optAny(index: Int, default: AnyRef) = {
    if (!isDefined(index)) default
    else checkAny(index)
  }

  def checkBoolean(index: Int) = {
    checkIndex(index, "boolean")
    args(index) match {
      case value: java.lang.Boolean => value
      case value => throw typeError(index, value, "boolean")
    }
  }

  def optBoolean(index: Int, default: Boolean) = {
    if (!isDefined(index)) default
    else checkBoolean(index)
  }

  def checkDouble(index: Int) = {
    checkIndex(index, "number")
    args(index) match {
      case value: java.lang.Number => value.doubleValue
      case value => throw typeError(index, value, "number")
    }
  }

  def optDouble(index: Int, default: Double) = {
    if (!isDefined(index)) default
    else checkDouble(index)
  }

  def checkInteger(index: Int) = {
    checkIndex(index, "integer")
    args(index) match {
      // TODO: The below is correct behaviour, but breaks existing OC1 code (f.e. file:read(math.huge))
      /* case value: java.lang.Double =>
        if (!java.lang.Double.isFinite(value) || value < java.lang.Integer.MIN_VALUE || value > java.lang.Integer.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.intValue
        }
      case value: java.lang.Float =>
        if (!java.lang.Float.isFinite(value) || value < java.lang.Integer.MIN_VALUE || value > java.lang.Integer.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.intValue
        }
      case value: java.lang.Long =>
        if (value < java.lang.Integer.MIN_VALUE || value > java.lang.Integer.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.intValue
        }
      case value: java.lang.Number => value.intValue
      */
      case value: java.lang.Double =>
        if (value.isNaN)
          throw intError(index, value)
        else if (value > java.lang.Integer.MAX_VALUE)
          java.lang.Integer.MAX_VALUE
        else if (value < java.lang.Integer.MIN_VALUE)
          java.lang.Integer.MIN_VALUE
        else
          value.intValue
      case value: java.lang.Float =>
        if (value.isNaN)
          throw intError(index, value)
        else if (value > java.lang.Integer.MAX_VALUE)
          java.lang.Integer.MAX_VALUE
        else if (value < java.lang.Integer.MIN_VALUE)
          java.lang.Integer.MIN_VALUE
        else
          value.intValue
      case value: java.lang.Long =>
        if (value > java.lang.Integer.MAX_VALUE)
          java.lang.Integer.MAX_VALUE
        else if (value < java.lang.Integer.MIN_VALUE)
          java.lang.Integer.MIN_VALUE
        else
          value.intValue
      case value: java.lang.Number => value.intValue
      case value => throw typeError(index, value, "integer")
    }
  }

  def optInteger(index: Int, default: Int) = {
    if (!isDefined(index)) default
    else checkInteger(index)
  }

  def checkLong(index: Int) = {
    checkIndex(index, "integer")
    args(index) match {
      // TODO: The below is correct behaviour, but breaks existing OC1 code (f.e. file:read(math.huge))
      /* case value: java.lang.Double =>
        if (!java.lang.Double.isFinite(value) || value < java.lang.Long.MIN_VALUE || value > java.lang.Long.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.longValue
        }
      case value: java.lang.Float =>
        if (!java.lang.Float.isFinite(value) || value < java.lang.Long.MIN_VALUE || value > java.lang.Long.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.longValue
        }
      case value: java.lang.Number => value.longValue
      */
      case value: java.lang.Double =>
        if (value.isNaN)
          throw intError(index, value)
        else if (value > java.lang.Long.MAX_VALUE)
          java.lang.Long.MAX_VALUE
        else if (value < java.lang.Long.MIN_VALUE)
          java.lang.Long.MIN_VALUE
        else
          value.longValue
      case value: java.lang.Float =>
        if (value.isNaN)
          throw intError(index, value)
        else if (value > java.lang.Long.MAX_VALUE)
          java.lang.Long.MAX_VALUE
        else if (value < java.lang.Long.MIN_VALUE)
          java.lang.Long.MIN_VALUE
        else
          value.longValue
      case value: java.lang.Number => value.longValue
      case value => throw typeError(index, value, "integer")
    }
  }

  def optLong(index: Int, default: Long) = {
    if (!isDefined(index)) default
    else checkLong(index)
  }

  def checkString(index: Int) = {
    checkIndex(index, "string")
    args(index) match {
      case value: java.lang.String => value
      case value: Array[Byte] => new String(value, Charsets.UTF_8)
      case value => throw typeError(index, value, "string")
    }
  }

  def optString(index: Int, default: String) = {
    if (!isDefined(index)) default
    else checkString(index)
  }

  def checkByteArray(index: Int) = {
    checkIndex(index, "string")
    args(index) match {
      case value: java.lang.String => value.getBytes(Charsets.UTF_8)
      case value: Array[Byte] => value
      case value => throw typeError(index, value, "string")
    }
  }

  def optByteArray(index: Int, default: Array[Byte]) = {
    if (!isDefined(index)) default
    else checkByteArray(index)
  }

  // Java 接口签名是原始类型 `Map checkTable(int)`，Scala 侧必须返回 `java.util.Map`
  // （返回 `AnyRef` 会触发 "incompatible type in overriding"）。
  // Lua 表格在 Scala 侧可能是不可变 / 可变 Map，这里统一转成 Java Map 视图。
  def checkTable(index: Int): util.Map[_, _] = {
    checkIndex(index, "table")
    args(index) match {
      case value: java.util.Map[_, _] => value
      case value: Map[_, _] => value.asJava
      case value: mutable.Map[_, _] => value.asJava
      case value => throw typeError(index, value, "table")
    }
  }

  def optTable(index: Int, default: util.Map[_, _]) = {
    if (!isDefined(index)) default
    else checkTable(index)
  }

  def checkItemStack(index: Int) = {
    val map = checkTable(index)
    tableGet(map, "name") match {
      case name: String =>
        val damage = tableGet(map, "damage") match {
          case number: java.lang.Number => number.intValue
          case _ => 0
        }
        val tag = ItemStacks.tagOf(tableGet(map, "tag"))
        makeStack(name, damage, tag)
      case _ => throw new IllegalArgumentException("invalid item stack")
    }
  }

  def optItemStack(index: Int, default: ItemStack) = {
    if (!isDefined(index)) default
    else checkItemStack(index)
  }

  def isBoolean(index: Int) =
    index >= 0 && index < count && (args(index) match {
      case value: java.lang.Boolean => true
      case _ => false
    })

  def isDouble(index: Int) =
    index >= 0 && index < count && (args(index) match {
      case value: java.lang.Number => true
      case _ => false
    })

  def isInteger(index: Int) =
    index >= 0 && index < count && (args(index) match {
      // TODO: The below is correct behaviour, but may break existing OC1 code
      /* case value: java.lang.Double =>
        java.lang.Double.isFinite(value) && value >= java.lang.Integer.MIN_VALUE && value <= java.lang.Integer.MAX_VALUE
      case value: java.lang.Float =>
        java.lang.Float.isFinite(value) && value >= java.lang.Integer.MIN_VALUE && value <= java.lang.Integer.MAX_VALUE
      case value: java.lang.Long =>
        value >= java.lang.Integer.MIN_VALUE && value <= java.lang.Integer.MAX_VALUE */
      case value: java.lang.Double => !value.isNaN
      case value: java.lang.Float => !value.isNaN
      case value: java.lang.Number => true
      case _ => false
    })

  def isLong(index: Int) =
    index >= 0 && index < count && (args(index) match {
      // TODO: The below is correct behaviour, but may break existing OC1 code
      /* case value: java.lang.Double =>
        java.lang.Double.isFinite(value) && value >= java.lang.Long.MIN_VALUE && value <= java.lang.Long.MAX_VALUE
      case value: java.lang.Float =>
        java.lang.Float.isFinite(value) && value >= java.lang.Long.MIN_VALUE && value <= java.lang.Long.MAX_VALUE */
      case value: java.lang.Double => !value.isNaN
      case value: java.lang.Float => !value.isNaN
      case value: java.lang.Number => true
      case _ => false
    })

  def isString(index: Int) =
    index >= 0 && index < count && (args(index) match {
      case value: java.lang.String => true
      case value: Array[Byte] => true
      case _ => false
    })

  def isByteArray(index: Int) =
    index >= 0 && index < count && (args(index) match {
      case value: java.lang.String => true
      case value: Array[Byte] => true
      case _ => false
    })

  def isTable(index: Int) =
    index >= 0 && index < count && (args(index) match {
      case value: java.util.Map[_, _] => true
      case value: Map[_, _] => true
      case value: mutable.Map[_, _] => true
      case _ => false
    })

  def isItemStack(index: Int) =
    isTable(index) && {
      val map = checkTable(index)
      tableGet(map, "name") match {
        case value: String => true
        case value: Array[Byte] => true
        case _ => false
      }
    }

  def toArray = args.map {
    case value: Array[Byte] => new String(value, Charsets.UTF_8)
    case value => value
  }.toArray

  /**
   * 统一的表格取值：`checkTable` 现在总是返回 `java.util.Map`（Scala 的 Map 已在
   * `checkTable` 里转成 Java 视图），这里保留对 Scala Map 的兼容分支以防万一。
   */
  private def tableGet(table: Any, key: Any): Any = table match {
    case value: java.util.Map[_, _] => value.asInstanceOf[java.util.Map[Any, Any]].get(key)
    case value: Map[_, _] => value.asInstanceOf[Map[Any, Any]].getOrElse(key, null)
    case value: mutable.Map[_, _] => value.asInstanceOf[mutable.Map[Any, Any]].getOrElse(key, null)
    case _ => null
  }

  private def isDefined(index: Int) = index >= 0 && index < args.length && args(index) != null

  private def checkIndex(index: Int, name: String) =
    if (index < 0) throw new IndexOutOfBoundsException()
    else if (args.length <= index) throw new IllegalArgumentException(
      s"bad arguments #${index + 1} ($name expected, got no value)")

  private def typeError(index: Int, have: AnyRef, want: String) =
    new IllegalArgumentException(
      s"bad argument #${index + 1} ($want expected, got ${typeName(have)})")

  private def intError(index: Int, have: AnyRef) =
    new IllegalArgumentException(
      s"bad argument #${index + 1} (${typeName(have)} has no integer representation)")

  private def typeName(value: AnyRef): String = value match {
    case null | None | _: scala.runtime.BoxedUnit => "nil"
    case _: java.lang.Boolean => "boolean"
    case _: java.lang.Byte => "integer"
    case _: java.lang.Short => "integer"
    case _: java.lang.Integer => "integer"
    case _: java.lang.Long => "integer"
    case _: java.lang.Number => "number"
    case _: java.lang.String => "string"
    case _: Array[Byte] => "string"
    case value: java.util.Map[_, _] => "table"
    case value: Map[_, _] => "table"
    case value: mutable.Map[_, _] => "table"
    case _ => value.getClass.getSimpleName
  }

  private def makeStack(name: String, damage: Int, tag: Option[CompoundTag]) =
    // TODO(1.21.1): 原实现用 `Item.itemRegistry.getObject(name)` + `ItemStack(item, 1, damage)`；
    // 注册表与 damage 语义都变了，统一收敛到 ItemStacks。
    ItemStacks.makeStack(name, damage, tag)
}
