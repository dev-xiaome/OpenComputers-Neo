package li.cil.oc.server.network

import li.cil.oc.api.machine
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network
import li.cil.oc.api.network._
import li.cil.oc.api.network.{Node => ImmutableNode}
import li.cil.oc.server.driver.CompoundBlockEnvironment
import li.cil.oc.server.driver.Registry
import li.cil.oc.server.machine.ArgumentsImpl
import li.cil.oc.server.machine.Callbacks
import li.cil.oc.server.machine.Callbacks.ComponentCallback
import li.cil.oc.server.machine.Callbacks.PeripheralCallback
import li.cil.oc.util.SideTracker
import net.minecraft.nbt.CompoundTag

import scala.jdk.CollectionConverters._

/**
 * 组件节点。
 *
 * ==1.21.1 移植要点（与 CE-1.20 的差异仅限 API 翻译）==
 *  - 参数包装使用 `li.cil.oc.server.machine.ArgumentsImpl`，与 CE-1.20 的
 *    `new ArgumentsImpl(Seq(arguments: _*))` 一致。**不要**再在本地另写一份 Arguments
 *    实现：`ArgumentsImpl#isDefined` 带 `args(index) != null` 判断，而手写的简化版
 *    漏掉该判断后，Lua 传显式 `nil` 给 `optByteArray` 一类「可选参数」会直接抛
 *    `bad argument #1 (string expected, got nil)`，把 BIOS 打挂。
 *  - 回调表统一走 `li.cil.oc.server.machine.Callbacks`（支持 `ManagedPeripheral` 动态
 *    方法表、`MethodWhitelist` 白名单、`FilteredEnvironment` 过滤与 `NamedBlock#priority`
 *    排序）。本文件里历史上曾有一份手写的 `ComponentCallbacks` 替身，它只做静态注解扫描，
 *    导致同一宿主的 `component.methods()` 与 `component.invoke()` 各算一套方法表、互相
 *    不一致，已删除。
 *  - `CompoundTag#getInteger` → `CompoundTag#getInt`。
 *  - `api.network.Network#nodes()` / `#neighbors()` 返回的是裸 `Iterable`
 *    （Scala 2.13 的 `scala.collection.Iterable` 不继承 `java.lang.Iterable`），
 *    本文件统一用 [[NodeCollections.toScala]] 转换。
 */
// 注意：这里必须**全限定**本包的 `Node`（`li.cil.oc.server.network.Node`）。
// 文件顶部的 `import li.cil.oc.api.network._` 会把 Java 接口 `api.network.Node`
// 引入作用域，而 Scala 2 的名称绑定优先级里「不同编译单元里的同包成员」（第 4 级）
// 低于通配 import（第 3 级），裸写 `Node` 会解析到那个**没有实现**的接口上 ——
// 于是 `new Component {}` 报 "object creation impossible. Missing implementations
// for 9 members of trait Node"（见 `Network.scala:608`）。
// 本包的 `Node` 才提供 connect / disconnect / remove / neighbors / reachableNodes 等的实现。
trait Component extends network.Component with li.cil.oc.server.network.Node {
  val name: String

  def visibility = _visibility

  private lazy val callbacks = Callbacks(host)

  private lazy val hosts: Map[String, Option[Environment]] = host match {
    case multi: CompoundBlockEnvironment =>
      callbacks.map {
        case (method, callback) => callback match {
          case component: ComponentCallback =>
            multi.environments.find {
              case (_, environment) => environment.getClass == component.method.getDeclaringClass
            } match {
              case Some((_, environment)) => method -> Some(environment)
              case _ => method -> None
            }
          case peripheral: PeripheralCallback =>
            multi.environments.find {
              case (_, environment: ManagedPeripheral) => environment.methods.contains(peripheral.annotation.value)
              case _ => false
            } match {
              case Some((_, environment)) => method -> Some(environment)
              case _ => method -> None
            }
          case _ => method -> None
        }
      }
    case _ => callbacks.map {
      case (method, callback) => method -> Some(host)
    }
  }

  private var _visibility = Visibility.None

  override def setVisibility(value: Visibility): Unit = {
    if (value.ordinal() > reachability.ordinal()) {
      throw new IllegalArgumentException("Trying to set computer visibility to '" + value + "' on a '" + name +
        "' node with reachability '" + reachability + "'. It will be limited to the node's reachability.")
    }
    if (SideTracker.isServer) {
      if (network != null) _visibility match {
        case Visibility.Neighbors => value match {
          case Visibility.Network => addTo(visibleNodes)
          case Visibility.None => removeFrom(directNeighbors)
          case _ =>
        }
        case Visibility.Network => value match {
          case Visibility.Neighbors =>
            val neighborSet = directNeighbors.toSet
            removeFrom(visibleNodes.filterNot(neighborSet.contains))
          case Visibility.None => removeFrom(visibleNodes)
          case _ =>
        }
        case Visibility.None => value match {
          case Visibility.Neighbors => addTo(directNeighbors)
          case Visibility.Network => addTo(visibleNodes)
          case _ =>
        }
      }
      _visibility = value
    }
  }

  // `api.network.Network#nodes()` / `#neighbors()` 的声明类型是裸 `Iterable`，
  // 而 Scala 2.13 的 `scala.collection.Iterable` 并不继承 `java.lang.Iterable`，
  // 不能直接当 Scala 集合用；统一走 [[NodeCollections.toScala]]。
  private def visibleNodes: scala.collection.Iterable[ImmutableNode] =
    if (network == null) scala.collection.Iterable.empty
    else NodeCollections.toScala(network.nodes(this)).filterNot(_ == this)

  private def directNeighbors: scala.collection.Iterable[ImmutableNode] =
    if (network == null) scala.collection.Iterable.empty
    else NodeCollections.toScala(network.neighbors(this))

  override def canBeSeenFrom(other: ImmutableNode) = visibility match {
    case Visibility.None => false
    case Visibility.Network => canBeReachedFrom(other)
    case Visibility.Neighbors => isNeighborOf(other)
  }

  // 注意：这里必须匹配**真实的**机器类型 `li.cil.oc.server.machine.Machine`。
  // 历史上本文件用过结构化类型（structural type）来绕开「server/machine 尚未移植」，
  // 但 Scala 的 `case x: 结构化类型` 在运行期**恒为真**（`isInstanceOf` 对结构化类型
  // 不做方法存在性检查），随后的反射调用 `getMethod("removeComponent", ...)` 会对任何
  // 没有这两个方法的宿主（例如 `tileentity.Screen`）抛 `NoSuchMethodException` 并崩档。
  private def addTo(nodes: scala.collection.Iterable[ImmutableNode]) = nodes.foreach(_.host match {
    case machine: li.cil.oc.server.machine.Machine => machine.addComponent(this)
    case _ =>
  })

  private def removeFrom(nodes: scala.collection.Iterable[ImmutableNode]) = nodes.foreach(_.host match {
    case machine: li.cil.oc.server.machine.Machine => machine.removeComponent(this)
    case _ =>
  })

  // ----------------------------------------------------------------------- //

  override def methods = callbacks.keySet.asJavaCollection

  override def annotation(method: String): machine.Callback =
    callbacks.get(method) match {
      case Some(callback) => callback.annotation
      case _ => throw new NoSuchMethodException()
    }

  override def invoke(method: String, context: Context, arguments: AnyRef*): Array[AnyRef] = {
    callbacks.get(method) match {
      case Some(callback) => hosts(method) match {
        case Some(environment) => Registry.convert(callback(environment, context, new ArgumentsImpl(arguments)))
        case _ => throw new NoSuchMethodException()
      }
      case _ => throw new NoSuchMethodException()
    }
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: CompoundTag): Unit = {
    // 必须调用 super：节点地址（`address`）由父 trait `Node#load` 负责恢复，
    // 漏掉这一步会让所有组件在存档重载后丢掉地址。CE-1.20 同样调用 `super.loadData`。
    super.load(nbt)
    // 标签名与 `common.item.data.NodeData` 的 `VisibilityTag` 一致（值为 "visibility"）。
    if (nbt.contains("visibility")) {
      _visibility = Visibility.values()(nbt.getInt("visibility"))
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    nbt.putInt("visibility", _visibility.ordinal())
  }

  override def toString = super.toString + s"@$name"
}
