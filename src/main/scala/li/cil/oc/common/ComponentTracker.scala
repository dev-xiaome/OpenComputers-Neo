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
 *    改为 [[initialize]] 里显式 `addListener`，由主类（或
 *    [[li.cil.oc.common.event.EventHandlers]]）调用一次。
 */
abstract class ComponentTracker {
  private val worlds = mutable.Map.empty[ResourceKey[Level], Cache[String, ManagedEnvironment]]

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

  /** 注册世界卸载监听；由主类在 mod 初始化时调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: LevelEvent.Unload) => onWorldUnload(e))
  }

  protected def clear(world: Level): Unit = this.synchronized {
    components(world).invalidateAll()
    components(world).cleanUp()
  }
}
