package li.cil.oc.util

import com.google.common.base.Charsets
import net.minecraft.core.Direction
import net.minecraft.core.{HolderLookup, RegistryAccess}
import net.minecraft.nbt._
import net.minecraft.world.item.ItemStack

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.language.reflectiveCalls
import scala.reflect.ClassTag

/**
 * NBT 构造/读取的隐式转换层（原 1.7.10 的 `ExtendedNBT`）。
 *
 * 1.21.1 迁移要点：
 *  - `NBT.TAG_*` → [[net.minecraft.nbt.Tag.TAG_*]]
 *  - `new NBTTagXxx(v)` → `XxxTag.valueOf(v)`
 *  - `func_1502xx_x()` 系列 → `getAsXxx`
 *  - `ListTag` 的遍历/映射语义改为基于下标访问
 *  - `ItemStack` 的序列化需要 `HolderLookup.Provider`，见 [[fallbackRegistry]]
 */
object ExtendedNBT {

  /**
   * 序列化 / 反序列化物品时使用的注册表访问器。
   *
   * **绝对不能用 `RegistryAccess.EMPTY`。** 1.21.1 的 `ItemStack#save` 内部要先用
   * `registries.getOrThrow(Registries.ITEM)` 查出物品的 id 才能写进标签；在空访问器上这一步
   * 拿不到注册表，结果**每个物品都被静默写成空标签 `{"item": {}}`，读档时全部变成空气**。
   * 这就是「机箱里的物品全没了」的根因——存档里那 7 条空的 `{"item": {}}` 正是它留下的。
   * 官方 1.21.1 移植版的做法同样是用真实的服务端注册表（`ServerLifecycleHooks`）。
   *
   * 真正的取值逻辑（服务端 → 客户端 → 缓存 → 告警）在 Java 侧
   * [[li.cil.oc.util.RegistryAccessHelper]] 里，这样 Java 代码（例如
   * `api.prefab.ItemStackArrayValue`）能用到同一个真源，不会各写一份。
   *
   * 返回类型用 `HolderLookup.Provider`（`ItemStack#save` / `#parseOptional` 需要的正是它，
   * `RegistryAccess` 也实现了该接口）。Java 侧的 helper 在真的取不到注册表时返回的是
   * `RegistryAccess.EMPTY`，所以这里**不要**收窄成 `RegistryAccess`，
   * 否则一旦 helper 改成别的 provider 实现就会编译不过。
   */
  def fallbackRegistry: HolderLookup.Provider = RegistryAccessHelper.getOrEmpty()

  /**
   * 把一个物品堆写进 `nbt` 的 `name` 键（空栈写空标签）。
   *
   * **必须用返回值，不能依赖它写进传入的标签。** 1.21.1 的
   * `ItemStack#save(HolderLookup.Provider, Tag)` 内部是
   * `DataComponentUtil.wrapEncodingExceptions(this, CODEC, registries, tag)`，
   * 它是**返回**编码结果，而不是就地把内容填进 `tag`；空栈还会直接抛
   * `IllegalStateException: Cannot encode empty ItemStack`。
   * 把返回值丢掉就会写出空标签 `{}` —— 存档里表现为 `{"item": {}}`，
   * 读档后物品全部消失（这正是「机箱里的物品全没了」的直接原因）。
   * 因此统一走 `saveOptional`：空栈返回空标签，非空栈返回编码好的标签。
   */
  def putStack(nbt: CompoundTag, name: String, stack: ItemStack): Unit =
    nbt.put(name, encodeStack(stack))

  /** 把物品栈编码成 `CompoundTag`（空栈 → 空标签）。见 [[putStack]] 对返回值的说明。 */
  def encodeStack(stack: ItemStack): CompoundTag =
    if (stack == null) new CompoundTag()
    else stack.saveOptional(fallbackRegistry) match {
      case tag: CompoundTag => tag
      case other =>
        // 理论上不会发生：物品的 Codec 一定编码成 CompoundTag。真发生也保底不丢数据。
        val wrapper = new CompoundTag()
        wrapper.put("value", other)
        wrapper
    }

  implicit def toNbt(value: Boolean): ByteTag = ByteTag.valueOf(value)

  implicit def toNbt(value: Byte): ByteTag = ByteTag.valueOf(value)

  implicit def toNbt(value: Short): ShortTag = ShortTag.valueOf(value)

  implicit def toNbt(value: Int): IntTag = IntTag.valueOf(value)

  implicit def toNbt(value: Long): LongTag = LongTag.valueOf(value)

  implicit def toNbt(value: Float): FloatTag = FloatTag.valueOf(value)

  implicit def toNbt(value: Double): DoubleTag = DoubleTag.valueOf(value)

  implicit def toNbt(value: Array[Byte]): ByteArrayTag = new ByteArrayTag(value)

  implicit def toNbt(value: Array[Int]): IntArrayTag = new IntArrayTag(value)

  implicit def toNbt(value: Array[Boolean]): ByteArrayTag = new ByteArrayTag(value.map(if (_) 1: Byte else 0: Byte))

  implicit def toNbt(value: String): StringTag = StringTag.valueOf(value)

  implicit def toNbt(value: ItemStack): CompoundTag = {
    val nbt = new CompoundTag()
    putStack(nbt, "item", value)
    nbt.getCompound("item")
  }

  implicit def toNbt(value: CompoundTag => Unit): CompoundTag = {
    val nbt = new CompoundTag()
    value(nbt)
    nbt
  }

  implicit def toNbt(value: Map[String, _]): CompoundTag = {
    val nbt = new CompoundTag()
    for ((key, value) <- value) value match {
      case value: Boolean => nbt.put(key, value)
      case value: Byte => nbt.put(key, value)
      case value: Short => nbt.put(key, value)
      case value: Int => nbt.put(key, value)
      case value: Long => nbt.put(key, value)
      case value: Float => nbt.put(key, value)
      case value: Double => nbt.put(key, value)
      case value: Array[Byte] => nbt.put(key, value)
      case value: Array[Int] => nbt.put(key, value)
      case value: String => nbt.put(key, value)
      case value: ItemStack => nbt.put(key, value)
      case _ =>
    }
    nbt
  }

  def typedMapToNbt(map: Map[_, _]): Tag = {
    def mapToList(value: Array[(_, _)]) = value.collect {
      // Ignore, can be stuff like the 'n' introduced by Lua's `pack`.
      case (k: Number, v) => k -> v
    }.sortBy(_._1.intValue()).map(_._2)
    def asList(value: Option[Any]): IndexedSeq[_] = value match {
      case Some(v: Array[_]) => v
      case Some(v: Map[_, _]) => mapToList(v.toArray)
      case Some(v: mutable.Map[_, _]) => mapToList(v.toArray)
      case Some(v: java.util.Map[_, _]) => mapToList(v.asScala.toArray)
      case Some(v: String) => v.getBytes(Charsets.UTF_8)
      case _ => throw new IllegalArgumentException("Illegal or missing value.")
    }
    def asMap[K](value: Option[Any]): Map[K, _] = value match {
      case Some(v: Map[K, _]@unchecked) => v
      case Some(v: mutable.Map[K, _]@unchecked) => v.toMap
      case Some(v: java.util.Map[K, _]@unchecked) => v.asScala.toMap
      case _ => throw new IllegalArgumentException("Illegal value.")
    }
    val typeAndValue = asMap[String](Option(map))
    val nbtType = typeAndValue.get("type")
    val nbtValue = typeAndValue.get("value")
    nbtType match {
      case Some(n: Number) => n.intValue() match {
        case Tag.TAG_BYTE => ByteTag.valueOf(nbtValue match {
          case Some(v: Number) => v.byteValue()
          case _ => throw new IllegalArgumentException("Illegal or missing value.")
        })

        case Tag.TAG_SHORT => ShortTag.valueOf(nbtValue match {
          case Some(v: Number) => v.shortValue()
          case _ => throw new IllegalArgumentException("Illegal or missing value.")
        })

        case Tag.TAG_INT => IntTag.valueOf(nbtValue match {
          case Some(v: Number) => v.intValue()
          case _ => throw new IllegalArgumentException("Illegal or missing value.")
        })

        case Tag.TAG_LONG => LongTag.valueOf(nbtValue match {
          case Some(v: Number) => v.longValue()
          case _ => throw new IllegalArgumentException("Illegal or missing value.")
        })

        case Tag.TAG_FLOAT => FloatTag.valueOf(nbtValue match {
          case Some(v: Number) => v.floatValue()
          case _ => throw new IllegalArgumentException("Illegal or missing value.")
        })

        case Tag.TAG_DOUBLE => DoubleTag.valueOf(nbtValue match {
          case Some(v: Number) => v.doubleValue()
          case _ => throw new IllegalArgumentException("Illegal or missing value.")
        })

        case Tag.TAG_BYTE_ARRAY => new ByteArrayTag(asList(nbtValue).map {
          case n: Number => n.byteValue()
          case _ => throw new IllegalArgumentException("Illegal value.")
        }.toArray)

        case Tag.TAG_STRING => StringTag.valueOf(nbtValue match {
          case Some(v: String) => v
          case Some(v: Array[Byte]) => new String(v, Charsets.UTF_8)
          case _ => throw new IllegalArgumentException("Illegal or missing value.")
        })

        case Tag.TAG_LIST =>
          val list = new ListTag()
          asList(nbtValue).map(v => asMap[Any](Option(v))).foreach(v => list.add(typedMapToNbt(v)))
          list

        case Tag.TAG_COMPOUND =>
          val nbt = new CompoundTag()
          val values = asMap[String](nbtValue)
          for ((name, entry) <- values) {
            try nbt.put(name, typedMapToNbt(asMap[Any](Option(entry)))) catch {
              case t: Throwable => throw new IllegalArgumentException(s"Error converting entry '$name': ${t.getMessage}")
            }
          }
          nbt

        case Tag.TAG_INT_ARRAY =>
          new IntArrayTag(asList(nbtValue).map {
            case n: Number => n.intValue()
            case _ => throw new IllegalArgumentException()
          }.toArray)

        case _ => throw new IllegalArgumentException(s"Unsupported NBT type '$n'.")
      }
      case Some(t) => throw new IllegalArgumentException(s"Illegal NBT type '$t'.")
      case _ => throw new IllegalArgumentException(s"Missing NBT type.")
    }
  }

  implicit def booleanIterableToNbt(value: Iterable[Boolean]): Iterable[ByteTag] = value.map(toNbt)

  implicit def byteIterableToNbt(value: Iterable[Byte]): Iterable[ByteTag] = value.map(toNbt)

  implicit def shortIterableToNbt(value: Iterable[Short]): Iterable[ShortTag] = value.map(toNbt)

  implicit def intIterableToNbt(value: Iterable[Int]): Iterable[IntTag] = value.map(toNbt)

  implicit def intArrayIterableToNbt(value: Iterable[Array[Int]]): Iterable[IntArrayTag] = value.map(toNbt)

  implicit def longIterableToNbt(value: Iterable[Long]): Iterable[LongTag] = value.map(toNbt)

  implicit def floatIterableToNbt(value: Iterable[Float]): Iterable[FloatTag] = value.map(toNbt)

  implicit def doubleIterableToNbt(value: Iterable[Double]): Iterable[DoubleTag] = value.map(toNbt)

  implicit def byteArrayIterableToNbt(value: Iterable[Array[Byte]]): Iterable[ByteArrayTag] = value.map(toNbt)

  implicit def stringIterableToNbt(value: Iterable[String]): Iterable[StringTag] = value.map(toNbt)

  implicit def writableIterableToNbt(value: Iterable[CompoundTag => Unit]): Iterable[CompoundTag] = value.map(toNbt)

  implicit def itemStackIterableToNbt(value: Iterable[ItemStack]): Iterable[CompoundTag] = value.map(toNbt)

  implicit def extendNBTBase(nbt: Tag): ExtendedNBTBase = new ExtendedNBTBase(nbt)

  implicit def extendCompoundTag(nbt: CompoundTag): ExtendedCompoundTag = new ExtendedCompoundTag(nbt)

  implicit def extendListTag(nbt: ListTag): ExtendedListTag = new ExtendedListTag(nbt)

  class ExtendedNBTBase(val nbt: Tag) {
    def toTypedMap: Map[String, _] = Map("type" -> nbt.getId, "value" -> (nbt match {
      case tag: ByteTag =>
        tag.getAsByte
      case tag: ShortTag =>
        tag.getAsShort
      case tag: IntTag =>
        tag.getAsInt
      case tag: LongTag =>
        tag.getAsLong
      case tag: FloatTag =>
        tag.getAsFloat
      case tag: DoubleTag =>
        tag.getAsDouble
      case tag: ByteArrayTag =>
        tag.getAsByteArray
      case tag: StringTag =>
        tag.getAsString
      case tag: ListTag =>
        tag.toTypedSeq.map((entry: Tag) => entry.toTypedMap)
      case tag: CompoundTag =>
        tag.getAllKeys.asScala.map(key => key -> tag.get(key).toTypedMap).toMap
      case tag: IntArrayTag =>
        tag.getAsIntArray
      case _ => throw new IllegalArgumentException()
    }))
  }

  class ExtendedCompoundTag(val nbt: CompoundTag) {
    def setNewCompoundTag(name: String, f: (CompoundTag) => Any): CompoundTag = {
      val t = new CompoundTag()
      f(t)
      nbt.put(name, t)
      nbt
    }

    def setNewTagList(name: String, values: Iterable[Tag]): CompoundTag = {
      val t = new ListTag()
      t.addAll(values)
      nbt.put(name, t)
      nbt
    }

    def setNewTagList(name: String, values: Tag*): CompoundTag = setNewTagList(name, values)

    def getDirection(name: String): Option[Direction] = {
      nbt.getByte(name) match {
        case id if id < 0 => None
        case id =>
          Option(Direction.from3DDataValue(id))
      }
    }

    def setDirection(name: String, d: Option[Direction]): Unit = {
      d match {
        case Some(side) => nbt.putByte(name, side.get3DDataValue.toByte)
        case _ => nbt.putByte(name, -1: Byte)
      }
    }

    def getBooleanArray(name: String): Array[Boolean] = nbt.getByteArray(name).map(_ == 1)

    def setBooleanArray(name: String, value: Array[Boolean]): Unit = nbt.put(name, toNbt(value))
  }

  class ExtendedListTag(val nbt: ListTag) {
    def appendNewCompoundTag(f: (CompoundTag) => Unit): Unit = {
      val t = new CompoundTag()
      f(t)
      nbt.add(t)
    }

    def append(values: Iterable[Tag]): Unit = {
      for (value <- values) {
        nbt.add(value)
      }
    }

    def append(values: Tag*): Unit = append(values)

    def foreach[T <: Tag](f: T => Unit): Unit = {
      var i = 0
      while (i < nbt.size()) {
        f(nbt.get(i).asInstanceOf[T])
        i += 1
      }
    }

    def map[T <: Tag, Value](f: T => Value): IndexedSeq[Value] = {
      // 注意：默认作用域里的 `IndexedSeq` 是 `scala.collection.immutable.IndexedSeq`，
      // 而 `mutable.ArrayBuffer` 只实现 `scala.collection.IndexedSeq`，因此必须转换。
      val buffer = mutable.ArrayBuffer.empty[Value]
      var i = 0
      while (i < nbt.size()) {
        buffer += f(nbt.get(i).asInstanceOf[T])
        i += 1
      }
      buffer.toIndexedSeq
    }

    def toArray[T: ClassTag]: Array[T] = map((t: T) => t).toArray
  }

  implicit class RichListTag(private val nbt: ListTag) extends AnyVal {
    def addAll(values: Iterable[Tag]): Unit = values.foreach(nbt.add)

    def toTypedSeq: Seq[Tag] = {
      // 同上：`Seq` 是 `scala.collection.immutable.Seq`，需从 `ArrayBuffer` 转换。
      val buffer = mutable.ArrayBuffer.empty[Tag]
      var i = 0
      while (i < nbt.size()) {
        buffer += nbt.get(i)
        i += 1
      }
      buffer.toIndexedSeq
    }

    def tagCount: Int = nbt.size()
  }

}
