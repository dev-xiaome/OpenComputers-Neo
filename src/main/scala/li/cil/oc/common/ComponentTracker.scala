package li.cil.oc.common

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import net.neoforged.bus.api.SubscribeEvent
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.world.level.Level
import net.neoforged.neoforge.event.level.LevelEvent

import scala.jdk.CollectionConverters._
import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * Keeps track of loaded components by ID. Used to send messages between
 * component representation on server and client without knowledge of their
 * containers. For now this is only used for screens / text buffer components.
 */
abstract class ComponentTracker {
  private val worlds = mutable.Map.empty[Int, Cache[String, ManagedEnvironment]]

  private def components(world: Level) = {
    worlds.getOrElseUpdate(world.provider.dimensionId,
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
      components(world).invalidateAll(asJavaIterable(components(world).asMap().filter(_._2 == component).map(_._1)))
      components(world).cleanUp()
    }
  }

  def get(world: Level, address: String): Option[ManagedEnvironment] = this.synchronized {
    components(world).cleanUp()
    Option(components(world).getIfPresent(address))
  }

  @SubscribeEvent
  def onWorldUnload(e: WorldEvent.Unload): Unit = clear(e.world)

  protected def clear(world: Level): Unit = this.synchronized {
    components(world).invalidateAll()
    components(world).cleanUp()
  }
}
