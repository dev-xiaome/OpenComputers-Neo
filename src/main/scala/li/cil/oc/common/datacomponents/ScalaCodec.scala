package li.cil.oc.common.datacomponents

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.mojang.serialization.{Codec, MapCodec}
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.{ByteBufCodecs, StreamCodec}
import net.minecraft.util.{ColorRGBA, ExtraCodecs}
import net.minecraft.world.phys.{AABB, Vec3}

import java.nio.ByteBuffer
import java.util.stream.IntStream
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.reflect.ClassTag

object ScalaCodec {
  val BOOL: Codec[Boolean] = Codec.BOOL.xmap(b => b, b => b)
  val INT: Codec[Int] = Codec.INT.xmap(b => b, b => b)
  val INT_ARRAY: Codec[Array[Int]] = Codec.INT_STREAM.xmap(b => b.toArray, b => IntStream.of(b: _*))
  val LONG: Codec[Long] = Codec.LONG.xmap(b => b, b => b)
  val BYTE: Codec[Byte] = Codec.BYTE.xmap(b => b, b => b)
  val FLOAT: Codec[Float] = Codec.FLOAT.xmap(b => b, b => b)
  val DOUBLE: Codec[Double] = Codec.DOUBLE.xmap(b => b, b => b)

  val AABB: Codec[AABB] = RecordCodecBuilder.create(inst => inst.group(
    Vec3.CODEC.fieldOf("from").forGetter(_.getMinPosition),
    Vec3.CODEC.fieldOf("to").forGetter(_.getMaxPosition)
  ).apply(inst, (from, to) => new AABB(from, to)))

  def map[K, V](k: Codec[K], v: Codec[V]): Codec[Map[K, V]] = Codec.unboundedMap(k, v).xmap(_.asScala.toMap, _.asJava)
  def list[T](codec: Codec[T]): Codec[List[T]] = Codec.list(codec).xmap(_.asScala.toList, _.asJava)
  def mutableSet[T](codec: Codec[T])(implicit ord: Ordering[T]): Codec[mutable.Set[T]] = Codec.list(codec).xmap(_.asScala.to(mutable.Set), _.toList.sorted.asJava)
  def array[T: ClassTag](codec: Codec[T]): Codec[Array[T]] = Codec.list(codec).xmap(_.asScala.toArray, _.toList.asJava)
  def set[T](codec: Codec[T]): Codec[Set[T]] = Codec.list(codec).xmap(_.asScala.toSet, _.toList.asJava)
  def optionFieldOf[T](name: String, codec: Codec[T], lenient: Boolean = false): MapCodec[Option[T]] = Codec.optionalField(name, codec, lenient).xmap(_.toScala, _.toJava)
  def pair[A, B](pair: (Codec[A], Codec[B])): Codec[(A, B)] =
    RecordCodecBuilder.create(inst => inst.group(
      pair._1.fieldOf("first").forGetter[(A, B)](_._1),
      pair._2.fieldOf("second").forGetter[(A, B)](_._2)
    ).apply(inst, (a, b) => a -> b))
}

object ScalaStreamCodec {
  val BOOL: StreamCodec[ByteBuf, Boolean] = ByteBufCodecs.BOOL.map(b => b, b => b)
  val INT: StreamCodec[ByteBuf, Int] = ByteBufCodecs.INT.map(b => b, b => b)
  val INT_ARRAY: StreamCodec[ByteBuf, Array[Int]] = list(INT).map(i => i.toArray, i => i.toList)
  val VAR_INT: StreamCodec[ByteBuf, Int] = ByteBufCodecs.VAR_INT.map(b => b, b => b)
  val VAR_LONG: StreamCodec[ByteBuf, Long] = ByteBufCodecs.VAR_LONG.map(b => b, b => b)
  val BYTE: StreamCodec[ByteBuf, Byte] = ByteBufCodecs.BYTE.map(b => b, b => b)
  val FLOAT: StreamCodec[ByteBuf, Float] = ByteBufCodecs.FLOAT.map(b => b, b => b)
  val DOUBLE: StreamCodec[ByteBuf, Double] = ByteBufCodecs.DOUBLE.map(b => b, b => b)

  val AABB: StreamCodec[ByteBuf, AABB] = StreamCodec.composite(
    DOUBLE, _.minX,
    DOUBLE, _.minY,
    DOUBLE, _.minZ,
    DOUBLE, _.maxX,
    DOUBLE, _.maxY,
    DOUBLE, _.maxZ,
    (x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) =>
      new AABB(x1, y1, z1, x2, y2, z2)
  )

  val COLOR_RGBA: StreamCodec[ByteBuf, ColorRGBA] =
    ByteBufCodecs.INT.map[ColorRGBA](v => new ColorRGBA(v), v => v.rgba)

  def list[B <: ByteBuf, T](codec: StreamCodec[B, T]): StreamCodec[B, List[T]] =
    ByteBufCodecs.list().apply(codec).map(_.asScala.toList, _.asJava)
  def array[B <: ByteBuf, T: ClassTag](codec: StreamCodec[B, T]): StreamCodec[B, Array[T]] =
    ByteBufCodecs.list().apply(codec).map(l => l.asScala.toArray, _.toList.asJava)
  def mutableSet[B <: ByteBuf, T](codec: StreamCodec[B, T]): StreamCodec[B, mutable.Set[T]] =
    ByteBufCodecs.list().apply(codec).map(_.asScala.to(mutable.Set), _.toList.asJava)
  def set[B <: ByteBuf, T](codec: StreamCodec[B, T]): StreamCodec[B, Set[T]] =
    ByteBufCodecs.list().apply(codec).map(_.asScala.toSet, _.toList.asJava)
  def option[B <: ByteBuf, T](codec: StreamCodec[B, T]): StreamCodec[B, Option[T]] =
    ByteBufCodecs.optional(codec).map(_.toScala, _.toJava)
  def pair[B <: ByteBuf, K, V](pair: (StreamCodec[B, K], StreamCodec[B, V])): StreamCodec[B, (K, V)] =
    StreamCodec.composite(
      pair._1, _._1,
      pair._2, _._2,
      (a: K, b: V) => a -> b
    )
}
