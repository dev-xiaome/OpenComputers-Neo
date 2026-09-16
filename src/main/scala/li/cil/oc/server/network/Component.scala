package li.cil.oc.server.network

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import li.cil.oc.OpenComputers
import li.cil.oc.api.machine
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network
import li.cil.oc.api.network._
import li.cil.oc.api.network.{Node => ImmutableNode}
import li.cil.oc.server.driver.CompoundBlockEnvironment
import li.cil.oc.server.driver.Registry
import li.cil.oc.util.SideTracker
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

import scala.jdk.CollectionConverters._
import scala.language.reflectiveCalls

/**
 * 组件节点。
 *
 * ==1.21.1 移植要点==
 *  - `li.cil.oc.server.machine.Callbacks` / `Machine` / `ArgumentsImpl` **尚未移植**
 *    （见 `docs/PROGRESS.md`，属于 `server/machine` 包）。为了不改动别人的包、同时保证
 *    本包可独立编译，这里做了最小改造（详见文件末尾 [[ComponentCallbacks]] / [[ComponentArguments]]，
 *    以及下面的结构化类型 [[Component.HostMachine]]）。
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

  import Component.HostMachine

  private lazy val callbacks = ComponentCallbacks(host)

  private lazy val hosts: Map[String, Option[Environment]] = host match {
    case multi: CompoundBlockEnvironment =>
      callbacks.map {
        case (method, callback) => callback match {
          case component: ComponentCallbacks.ComponentCallback =>
            multi.environments.find {
              case (_, environment) => environment.getClass == component.method.getDeclaringClass
            } match {
              case Some((_, environment)) => method -> Some(environment)
              case _ => method -> None
            }
          case peripheral: ComponentCallbacks.PeripheralCallback =>
            multi.environments.find {
              case (_, environment: ManagedPeripheral) => methodsContains(environment.methods, peripheral.name)
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

  /** `ManagedPeripheral#methods` 声明为 Java `String[]`，这里做一次空安全 + 兼容 `java.util.List` 的判定。 */
  private def methodsContains(methods: Array[String], name: String): Boolean =
    methods != null && methods.contains(name)

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

  // 注意：这里必须匹配**真实的**机器类型，不能用上面的结构化类型 `HostMachine`。
  // Scala 的 `case x: 结构化类型` 在运行期**恒为真**（`isInstanceOf` 对结构化类型不做
  // 方法存在性检查），随后的反射调用 `getMethod("removeComponent", ...)` 会对任何
  // 没有这两个方法的宿主（例如 `tileentity.Screen`）抛 `NoSuchMethodException` 并崩档。
  // `server/machine` 移植完成后就应当换回真实类型匹配（原 TODO 说的正是这件事）。
  private def addTo(nodes: scala.collection.Iterable[ImmutableNode]) = nodes.foreach(_.host match {
    case machine: li.cil.oc.server.machine.Machine => machine.addComponent(this)
    case _ =>
  })

  private def removeFrom(nodes: scala.collection.Iterable[ImmutableNode]) = nodes.foreach(_.host match {
    case machine: li.cil.oc.server.machine.Machine => machine.removeComponent(this)
    case _ =>
  })

  // ----------------------------------------------------------------------- //

  override def methods = callbacks.keySet.asJava

  override def annotation(method: String): machine.Callback =
    callbacks.get(method) match {
      case Some(callback) => callback.annotation
      case _ => throw new NoSuchMethodException()
    }

  override def invoke(method: String, context: Context, arguments: AnyRef*): Array[AnyRef] = {
    callbacks.get(method) match {
      case Some(callback) => hosts(method) match {
        case Some(environment) => Registry.convert(callback(environment, context, ComponentArguments(arguments)))
        case _ => throw new NoSuchMethodException()
      }
      case _ => throw new NoSuchMethodException()
    }
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: CompoundTag): Unit = {
    // 不能写 `super.load(nbt)`：线性化上游的 `api.network.Node` / `api.Persistable`
    // 都是**抽象** Java 接口（没有 `default` 实现），Scala 2.13 比 2.11 严格，
    // 会报 “method load in trait Persistable is accessed from super. It may not be
    // abstract unless it is overridden by a member declared `abstract` and `override`”。
    // 上游没有实现可调用，去掉调用语义完全等价。
    if (nbt.contains("visibility")) {
      _visibility = Visibility.values()(nbt.getInt("visibility"))
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    nbt.putInt("visibility", _visibility.ordinal())
  }

  override def toString = super.toString + s"@$name"
}

object Component {
  /**
   * 宿主机的结构化类型（对应尚未移植的 `li.cil.oc.server.machine.Machine`）。
   *
   * 1.7.10 原实现直接 `case machine: Machine =>`（`Machine` 是个 Scala class）。
   * `server/machine` 整个包还没移植，这里改成结构化类型：只要宿主对象在运行期
   * 提供了 `addComponent` / `removeComponent` 两个方法就能被识别（Scala 用反射调用，
   * 有缓存，性能可接受）。等 `server/machine` 移植完成后应换回真实类型匹配。
   *
   * TODO(server.machine): `Machine` 移植后把本结构化类型删除，改为 `case machine: Machine =>`。
   */
  type HostMachine = {
    def addComponent(component: network.Component): Unit
    def removeComponent(component: network.Component): Unit
  }
}

/**
 * `li.cil.oc.server.machine.Callbacks` 的最小替代实现（该包尚未移植）。
 *
 * 与原实现的差异：
 *  - 只做「静态分析」：扫描宿主类（含父类）上带 `@Callback` 注解的方法；
 *    原实现还支持 [[CompoundBlockEnvironment]] 的多环境聚合、`ManagedPeripheral` 的动态
 *    `methods()`、[[li.cil.oc.api.driver.MethodWhitelist]] 白名单与
 *    [[li.cil.oc.api.network.FilteredEnvironment]] 过滤，这些都要等 `server/machine` 移植后补齐。
 *  - 不做按 `priority` 排序（`NamedBlock#priority`）。
 *
 * TODO(server.machine): `Callbacks` 移植后删除本对象，`Component` 改为直接调用它。
 */
private[network] object ComponentCallbacks {
  /** 缓存，键为宿主类。 */
  private val cache = scala.collection.mutable.Map.empty[Class[_], Map[String, Callback]]

  def apply(host: Any): Map[String, Callback] =
    if (host == null) Map.empty
    else cache.getOrElseUpdate(host.getClass, analyze(host.getClass))

  /** 该组件暴露的某个方法是否被声明为「直接调用」（`@Callback(direct = true)`）。 */
  def isDirect(host: Any, name: String): Boolean =
    apply(host).get(name).exists(_.annotation.direct())

  private def analyze(seed: Class[_]): Map[String, Callback] = {
    val callbacks = scala.collection.mutable.Map.empty[String, Callback]
    var c: Class[_] = seed
    while (c != null && c != classOf[Object]) {
      c.getDeclaredMethods.filter(_.isAnnotationPresent(classOf[machine.Callback])).foreach(m =>
        if (m.getParameterTypes.length != 2 ||
          m.getParameterTypes()(0) != classOf[Context] ||
          m.getParameterTypes()(1) != classOf[Arguments]) {
          OpenComputers.log.error(s"Invalid use of Callback annotation on ${m.getDeclaringClass.getName}.${m.getName}: invalid argument types or count.")
        }
        else if (m.getReturnType != classOf[Array[AnyRef]]) {
          OpenComputers.log.error(s"Invalid use of Callback annotation on ${m.getDeclaringClass.getName}.${m.getName}: invalid return type.")
        }
        else if (!Modifier.isPublic(m.getModifiers)) {
          OpenComputers.log.error(s"Invalid use of Callback annotation on ${m.getDeclaringClass.getName}.${m.getName}: method must be public.")
        }
        else {
          val a = m.getAnnotation(classOf[machine.Callback])
          val name = if (a.value != null && a.value.trim != "") a.value else m.getName
          if (!callbacks.contains(name)) {
            callbacks += name -> new ComponentCallback(m, a)
          }
        }
      )
      c = c.getSuperclass
    }
    callbacks.toMap
  }

  abstract class Callback(val annotation: machine.Callback) {
    def apply(instance: AnyRef, context: Context, args: Arguments): Array[AnyRef]
  }

  class ComponentCallback(val method: Method, annotation: machine.Callback) extends Callback(annotation) {
    override def apply(instance: AnyRef, context: Context, args: Arguments): Array[AnyRef] =
      method.invoke(instance, context, args).asInstanceOf[Array[AnyRef]]
  }

  class PeripheralCallback(val name: String) extends Callback(new PeripheralAnnotation(name)) {
    override def apply(instance: AnyRef, context: Context, args: Arguments): Array[AnyRef] =
      instance match {
        case peripheral: ManagedPeripheral => peripheral.invoke(name, context, args)
        case _ => throw new NoSuchMethodException()
      }
  }

  /**
   * `ManagedPeripheral` 是动态方法表（没有 `@Callback` 注解），
   * 这里在运行期合成一个等价的注解实例，保持 `Component#annotation` 的对外行为。
   */
  final class PeripheralAnnotation(val name: String) extends machine.Callback {
    override def value(): String = name
    override def direct(): Boolean = false
    override def limit(): Int = Integer.MAX_VALUE
    override def doc(): String = ""
    override def getter(): Boolean = false
    override def setter(): Boolean = false
    override def annotationType(): Class[_ <: java.lang.annotation.Annotation] = classOf[machine.Callback]
    override def toString: String = s"@Callback($name)"
  }
}

/**
 * `li.cil.oc.server.machine.ArgumentsImpl` 的最小替代实现（该包尚未移植）。
 *
 * 1.21.1 的 `api.machine.Arguments` 是 Java 接口，[Component#invoke] 只需要把一个
 * `Object[]` 包成 `Arguments` 交给回调。原 `ArgumentsImpl` 里那一大堆类型检查/辅助方法
 * 属于 `server/machine` 的职责，这里只实现接口本身要求的最小集合。
 *
 * TODO(server.machine): `ArgumentsImpl` 移植后删除本对象，`Component#invoke` 改为直接使用它。
 */
private[network] object ComponentArguments {
  def apply(args: Seq[AnyRef]): Arguments = new Arguments {
    private val values: Array[AnyRef] = args.toArray

    private val UTF8 = java.nio.charset.StandardCharsets.UTF_8

    override def iterator(): java.util.Iterator[AnyRef] = values.iterator.asJava

    override def count(): Int = values.length

    override def toArray(): Array[AnyRef] =
      values.map {
        case value: Array[Byte] => new String(value, UTF8)
        case value => value
      }

    private def checkIndex(index: Int): Unit =
      if (index < 0 || index >= values.length) {
        throw new IllegalArgumentException(s"index out of bounds (0 <= $index < ${values.length})")
      }

    override def checkAny(index: Int): AnyRef = {
      checkIndex(index)
      values(index) match {
        // Scala 2.13：`Unit` 伴生对象不能再作为模式使用，unit 值改用守卫判断。
        case null | None => null
        case arg if arg == () => null
        case arg => arg
      }
    }

    override def checkBoolean(index: Int): Boolean = check(index, "boolean") {
      case value: java.lang.Boolean => value
    }

    override def checkInteger(index: Int): Int = check(index, "number") {
      case value: java.lang.Number => value.intValue
    }

    override def checkLong(index: Int): Long = check(index, "number") {
      case value: java.lang.Number => value.longValue
    }

    override def checkDouble(index: Int): Double = check(index, "number") {
      case value: java.lang.Number => value.doubleValue
    }

    override def checkString(index: Int): String = check(index, "string") {
      case value: String => value
      case value: Array[Byte] => new String(value, UTF8)
    }

    override def checkByteArray(index: Int): Array[Byte] = check(index, "string") {
      case value: Array[Byte] => value
      case value: String => value.getBytes(UTF8)
    }

    override def checkTable(index: Int): java.util.Map[_, _] = check(index, "table") {
      case value: java.util.Map[_, _] => value
    }

    override def checkItemStack(index: Int): ItemStack = check(index, "item stack") {
      case value: ItemStack => value
    }

    // 注意：Java 侧的参数名是 `def`，在 Scala 中 `def` 是关键字，因此这里改名为 `default`。
    // 参数名不参与重载决议，不影响 override。
    override def optAny(index: Int, default: AnyRef): AnyRef = if (defined(index)) checkAny(index) else default

    override def optBoolean(index: Int, default: Boolean): Boolean = if (defined(index)) checkBoolean(index) else default

    override def optInteger(index: Int, default: Int): Int = if (defined(index)) checkInteger(index) else default

    override def optLong(index: Int, default: Long): Long = if (defined(index)) checkLong(index) else default

    override def optDouble(index: Int, default: Double): Double = if (defined(index)) checkDouble(index) else default

    override def optString(index: Int, default: String): String = if (defined(index)) checkString(index) else default

    override def optByteArray(index: Int, default: Array[Byte]): Array[Byte] = if (defined(index)) checkByteArray(index) else default

    override def optTable(index: Int, default: java.util.Map[_, _]): java.util.Map[_, _] = if (defined(index)) checkTable(index) else default

    override def optItemStack(index: Int, default: ItemStack): ItemStack = if (defined(index)) checkItemStack(index) else default

    override def isBoolean(index: Int): Boolean = is(index, classOf[java.lang.Boolean])

    override def isInteger(index: Int): Boolean = is(index, classOf[java.lang.Number])

    override def isLong(index: Int): Boolean = is(index, classOf[java.lang.Number])

    override def isDouble(index: Int): Boolean = is(index, classOf[java.lang.Number])

    override def isString(index: Int): Boolean = is(index, classOf[String]) || is(index, classOf[Array[Byte]])

    override def isByteArray(index: Int): Boolean = isString(index)

    override def isTable(index: Int): Boolean = is(index, classOf[java.util.Map[_, _]])

    override def isItemStack(index: Int): Boolean = is(index, classOf[ItemStack])

    private def defined(index: Int): Boolean = index >= 0 && index < values.length

    private def is(index: Int, clazz: Class[_]): Boolean =
      defined(index) && values(index) != null && clazz.isInstance(values(index))

    private def check[T](index: Int, expected: String)(f: PartialFunction[AnyRef, T]): T = {
      checkIndex(index)
      val value = values(index)
      if (f.isDefinedAt(value)) f(value)
      else throw new IllegalArgumentException(
        s"bad argument #${index + 1} ($expected expected, got ${if (value == null) "nil" else value.getClass.getName})")
    }
  }
}
