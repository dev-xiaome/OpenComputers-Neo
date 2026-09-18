package li.cil.oc.common

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.bus.api.SubscribeEvent

import scala.collection.JavaConverters.asJavaIterable
import scala.collection.convert.ImplicitConversionsToJava._
import scala.collection.convert.ImplicitConversionsToScala._
import scala.collection.mutable

/**
 * Keeps track of loaded components by ID. Used to send messages between
 * component representation on server and client without knowledge of their
 * containers. For now this is only used for screens / text buffer components.
 */
abstract class ComponentTracker {
  private val worlds = mutable.Map.empty[ResourceKey[Level], Cache[String, ManagedEnvironment]]

  private def components(level: Level) = {
    worlds.getOrElseUpdate(level.dimension,
      com.google.common.cache.CacheBuilder.newBuilder().
        weakValues().
        asInstanceOf[CacheBuilder[String, ManagedEnvironment]].
        build[String, ManagedEnvironment]())
  }

  def add(level: Level, address: String, component: ManagedEnvironment): Unit = {
    this.synchronized {
      components(level).put(address, component)
    }
  }

  def remove(level: Level, component: ManagedEnvironment): Unit = {
    this.synchronized {
      components(level).invalidateAll(asJavaIterable(components(level).asMap().filter(_._2 == component).keys))
      components(level).cleanUp()
    }
  }

  def get(level: Level, address: String): Option[ManagedEnvironment] = this.synchronized {
    components(level).cleanUp()
    Option(components(level).getIfPresent(address))
  }

  /// NeoForge 不允许「被注册对象的**父类**带 `@SubscribeEvent` 方法」——
  /// `client.ComponentTracker` / `server.ComponentTracker` 都是本类的子类，直接注册会抛
  /// `IllegalArgumentException` 并让整个集成初始化中断（一个 driver 都注册不上）。
  /// 因此这里去掉注解，改由子类通过本方法显式注册监听器。
  def registerOn(bus: net.neoforged.bus.api.IEventBus): Unit =
    bus.addListener((e: LevelEvent.Unload) => onWorldUnload(e))

  def onWorldUnload(e: LevelEvent.Unload): Unit = e.getLevel match {
    case level: Level => clear(level)
    case _ =>
  }

  protected def clear(level: Level): Unit = this.synchronized {
    components(level).invalidateAll()
    components(level).cleanUp()
  }
}
