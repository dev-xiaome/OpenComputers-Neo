package li.cil.oc.common.datacomponents

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.mojang.serialization.{Codec, DataResult, DynamicOps}
import li.cil.oc.server.machine.Machine.State
import net.minecraft.nbt.CompoundTag

import java.nio.ByteBuffer
import scala.collection.mutable
import scala.jdk.OptionConverters._
import scala.jdk.CollectionConverters._

case class MachineData(state: List[State.Value],
                       users: Set[String],
                       message: Option[String],
                       components: List[MachineData.Component],
                       nodeAddress: Option[String],
                       architectureData: CompoundTag,
                       signals: List[MachineData.Signal],
                       uptime: Long,
                       cpuTotal: Long,
                       remainingPause: Int)

object MachineData {
  val CODEC: Codec[MachineData] = RecordCodecBuilder.create(inst => inst.group(
    ScalaCodec.list(Codec.STRING)
      .xmap[List[State.Value]](
        (v: List[String]) => v.map(State.withName),
        (i: List[State.Value]) => i.map(_.toString)
      )
      .fieldOf("state")
      .forGetter(_.state),
    ScalaCodec.set(Codec.STRING).fieldOf("users").forGetter(_.users),
    ScalaCodec.optionFieldOf("message", Codec.STRING).forGetter(_.message),
    ScalaCodec.list(Component.CODEC).fieldOf("components").forGetter(_.components),
    ScalaCodec.optionFieldOf("node_address", Codec.STRING).forGetter(_.nodeAddress),
    CompoundTag.CODEC.fieldOf("architecture_data").forGetter(_.architectureData),
    ScalaCodec.list(Signal.CODEC).fieldOf("signals").forGetter(_.signals),
    ScalaCodec.LONG.fieldOf("uptime").forGetter(_.uptime),
    ScalaCodec.LONG.fieldOf("cpu_total").forGetter(_.cpuTotal),
    ScalaCodec.INT.fieldOf("remaining_pause").forGetter(_.remainingPause)
  ).apply(inst, MachineData.apply _))
  
  case class Component(address: String, name: String)
  
  object Component {
    val CODEC: Codec[Component] = RecordCodecBuilder.create(inst => inst.group(
      Codec.STRING.fieldOf("address").forGetter(_.address),
      Codec.STRING.fieldOf("name").forGetter(_.name)
    ).apply(inst, Component.apply _))
  }
  
  case class Signal(name: String, args: List[Signal.Value])

  object Signal {
    sealed class Value
    
    object Value {
      val CODEC: Codec[Value] = new Codec[Value] {
        override def decode[T](ops: DynamicOps[T], input: T): DataResult[Pair[Value, T]] = {
          ops.getMapValues(input).flatMap(e => e.findFirst().toScala match {
            case Some(value) => ops.getStringValue(value.getFirst).flatMap {
              case "Z" => ops.getBooleanValue(value.getSecond).map(v => Pair.of(Boolean(v), ops.empty()))
              case "L" => ops.getNumberValue(value.getSecond).map(v => Pair.of(Long(v.longValue), ops.empty()))
              case "D" => ops.getNumberValue(value.getSecond).map(v => Pair.of(Double(v.doubleValue), ops.empty()))
              case "S" => ops.getStringValue(value.getSecond).map(v => Pair.of(StringValue(v), ops.empty()))
              case "B" => ops.getByteBuffer(value.getSecond).map(v => Pair.of(ByteArray(v), ops.empty()))
              case "M" => ops.getMapEntries(value.getSecond).map(s => {
                val map = mutable.Map[T, T]()
                s.accept(map.put)
                
                Pair.of(
                  StringMap(for (key -> value <- map.toMap)
                    yield ops.getStringValue(key) match {
                      case key: DataResult.Success[_] => ops.getStringValue(value) match {
                        case value: DataResult.Success[_] => key.value() -> value.value()
                        case error: DataResult.Error[_] => return DataResult.error(error.messageSupplier())
                      }
                      case error: DataResult.Error[_] => return DataResult.error(error.messageSupplier())
                    }),
                  ops.empty()
                )
              })
              case "C" => ops.withDecoder(CompoundTag.CODEC).apply(value.getSecond).map(m => m.mapFirst(tag => Compound(tag)))
              case other => DataResult.error(() => s"Illegal value type $other")
            }
            case None => DataResult.success(Pair.of(Null, ops.empty()))
          })
        }

        override def encode[T](input: Value, ops: DynamicOps[T], prefix: T): DataResult[T] = {
          DataResult.success(ops.createMap(
            input match {
              case Boolean(v) => Map(ops.createString("Z") -> ops.createBoolean(v)).asJava
              case Long(v) => Map(ops.createString("L") -> ops.createLong(v)).asJava
              case Double(v) => Map(ops.createString("D") -> ops.createDouble(v)).asJava
              case StringValue(v) => Map(ops.createString("S") -> ops.createString(v)).asJava
              case ByteArray(v) => Map(ops.createString("B") -> ops.createByteList(v)).asJava
              case StringMap(v) => Map(ops.createString("M") -> ops.createMap(v.map { 
                case (a, b) => ops.createString(a) -> ops.createString(b) 
              }.asJava)).asJava
              case Compound(v) => Map(ops.createString("C") -> (ops.withEncoder(CompoundTag.CODEC).apply(v) match {
                case success: DataResult.Success[_] => success.value()
                case error: DataResult.Error[_] => return DataResult.error(error.messageSupplier())
              })).asJava
              case Null => Map.empty[T, T].asJava
            }
          ))
        }
      }
    }
    
    case object Null extends Value
    case class Boolean(value: scala.Boolean) extends Value
    case class Long(value: scala.Long) extends Value
    case class Double(value: scala.Double) extends Value
    case class StringValue(value: java.lang.String) extends Value
    case class ByteArray(value: ByteBuffer) extends Value
    case class StringMap(value: Map[java.lang.String, java.lang.String]) extends Value
    case class Compound(value: CompoundTag) extends Value
    
    val CODEC: Codec[Signal] = RecordCodecBuilder.create(inst => inst.group(
      Codec.STRING.fieldOf("name").forGetter(_.name),
      ScalaCodec.list(Value.CODEC).fieldOf("args").forGetter(v => v.args)
    ).apply(inst, Signal.apply _))
  }
}
