package li.cil.oc.common

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.LevelEvent

import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * 按 ID 跟踪「已加载的组件」，用于在服务端 / 客户端之间互相发消息，而不需要知道
 * 组件所在的容器。目前只有屏幕 / 文本缓冲组件使用。
 *
 * 1.21.1 迁移要点：
 *  - `World#provider.dimensionId`（Int）→ `Level#dimension()`（`ResourceKey[Level]`）：
 *    1.21.1 的维度是注册表键，不再有稳定的数字 id，因此缓存键改为维度键。
 *  - `world.isRemote` → `world.isClientSide`（见 client / server 两侧的子类）。
 *  - 不再使用 `@SubscribeEvent`：NeoForge 对 Scala object 的注解扫描不可靠，
 *    改为 [[initialize]] 里显式 `addListener`，由
 *    [[li.cil.oc.common.Proxy.init]] 调用一次（1.7.10 里由 `Proxy.init` 里的
 *    `MinecraftForge.EVENT_BUS.register(ComponentTracker)` 完成同样的事）。
 *
 * ==注册入口为什么单独放一个对象、且不叫 ComponentTracker==
 * 1.7.10 的 `common.ComponentTracker` 也是一个抽象类，但当时客户端 / 服务端两个子类
 * 各自用 `@SubscribeEvent` 注解被总线扫描到，所以「谁被扫描、谁就只清自己那份缓存」。
 * 1.21.1 改成显式注册后，注册动作必须绑定到**具体单例**：直接在这里写
 * `li.cil.oc.common.ComponentTracker.initialize()` 是编译不过的（抽象类不是值），
 * 而两个子类（[[li.cil.oc.server.ComponentTracker]] / `li.cil.oc.client.ComponentTracker`）
 * 又各自只有一份缓存，谁没被调用谁就泄漏。
 * 因此这里把注册收进 [[ComponentTrackerRegistry]]：监听器只注册一次，触发时遍历
 * **所有已创建**的实例，各实例仍按自己的 [[clear]] 条件决定是否清空（服务端只清服务端世界、
 * 客户端只清客户端世界，集成服务器里两者共处同一 JVM 也互不干扰）。
 *
 * 名字**故意**不叫 `ComponentTracker`：本包一旦有了同名 term，凡是
 * `import li.cil.oc.common._` 的文件里那个裸名 `ComponentTracker` 就会被它遮蔽 ——
 * 例如 [[li.cil.oc.common.component.TextBuffer]] 用的其实是**同包**的
 * `li.cil.oc.common.component.ComponentTracker`（另一套按维度 location 建的缓存），
 * 加同名伴生对象会让那 6 处调用全部解析错位（已实测报
 * `value add is not a member of object li.cil.oc.common.ComponentTracker`）。
 */
abstract class ComponentTracker {
  private val worlds = mutable.Map.empty[ResourceKey[Level], Cache[String, ManagedEnvironment]]

  // 构造期把自己登记进注册表的实例集合，之后由统一入口注册的那个监听器负责清理。
  // 只保存引用、不访问成员，因此构造期把 `this` 交出去是安全的。
  ComponentTrackerRegistry.register(this)

  private def components(world: Level) = {
    worlds.getOrElseUpdate(world.dimension(),
      com.google.common.cache.CacheBuilder.newBuilder().
        weakValues().
        asInstanceOf[CacheBuilder[String, ManagedEnvironment]].
        build[String, ManagedEnvironment]())
  }

  def add(world: Level, address: String, component: ManagedEnvironment): Unit = {
    this.synchronized {
      components(world).put(address, component)
    }
  }

  def remove(world: Level, component: ManagedEnvironment): Unit = {
    this.synchronized {
      val cache = components(world)
      val addresses = cache.asMap().asScala.collect { case (address, env) if env == component => address }.toSeq
      cache.invalidateAll(addresses.asJava)
      cache.cleanUp()
    }
  }

  def get(world: Level, address: String): Option[ManagedEnvironment] = this.synchronized {
    components(world).cleanUp()
    Option(components(world).getIfPresent(address))
  }

  /**
   * 世界卸载时清空缓存。
   *
   * 1.21.1 的 `LevelEvent.Unload` 拿到的是 `LevelAccessor`（客户端为 `ClientLevel`、
   * 服务端为 `ServerLevel`，两者都是 `Level`），因此这里做一次类型判定。
   */
  def onWorldUnload(e: LevelEvent.Unload): Unit = e.getLevel match {
    case world: Level => clear(world)
    case _ => // 不是普通世界（理论上不会发生），忽略。
  }

  /**
   * 注册世界卸载监听。
   *
   * 1.7.10：`@SubscribeEvent` 注解 + 总线扫描（每个子类单例各注册一份）。
   * 1.21.1：委托给统一入口 [[ComponentTrackerRegistry.initialize]]，
   * **幂等**——重复调用（例如 `common.Proxy.init`、`server.Proxy.init` 与
   * [[li.cil.oc.common.event.EventHandlers]] 三条链路都跑到）不会重复注册监听器。
   */
  def initialize(): Unit = ComponentTrackerRegistry.initialize()

  protected def clear(world: Level): Unit = this.synchronized {
    components(world).invalidateAll()
    components(world).cleanUp()
  }
}

/**
 * [[ComponentTracker]] 的注册表：承载「进程内唯一一次」的世界卸载监听注册。
 *
 * 监听器触发时遍历 [[instances]]（服务端 / 客户端两个单例在各自首次被访问时自动登记），
 * 因此不需要任何一方显式调用也能覆盖到：1.21.1 里主类目前只实例化 `common.Proxy`，
 * `server.Proxy.init` 那条链路并不一定会跑到。
 *
 * 命名说明见 [[ComponentTracker]] 的类注释（不能叫 `ComponentTracker`，否则会遮蔽
 * `li.cil.oc.common.component` 包里的同名对象）。
 */
object ComponentTrackerRegistry {
  /** 已创建的跟踪表实例（跟随各自 object 永久存活，因此不需要弱引用）。 */
  private val instances = mutable.Set.empty[ComponentTracker]

  /** 是否已注册过监听器；保证 [[initialize]] 幂等。 */
  private var initialized = false

  /** 由 [[ComponentTracker]] 的构造期调用。 */
  private[common] def register(tracker: ComponentTracker): Unit = this.synchronized {
    instances += tracker
  }

  /**
   * 注册 `LevelEvent.Unload` 监听：世界卸载时清空所有已登记实例的缓存。
   *
   * 1.7.10 的维度 id 换成了 `ResourceKey[Level]`（见 [[ComponentTracker]] 的类注释），
   * 事件类型本身没有变化，仍然只在世界卸载时触发。
   */
  def initialize(): Unit = this.synchronized {
    if (initialized) return
    initialized = true
    NeoForge.EVENT_BUS.addListener((e: LevelEvent.Unload) => this.synchronized {
      instances.foreach(_.onWorldUnload(e))
    })
  }
}
