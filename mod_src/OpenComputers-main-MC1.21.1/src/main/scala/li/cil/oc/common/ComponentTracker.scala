package li.cil.oc.common

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.world.level.Level
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.bus.api.SubscribeEvent

import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * Keeps track of loaded components by ID. Used to send messages between
 * component representation on server and client without knowledge of their
 * containers. For now this is only used for screens / text buffer components.
 */
abstract class ComponentTracker {
  // Track components per actual Level instance, not merely by dimension key.
  //
  // During an integrated-server/world reload the old and new overworld both
  // use minecraft:overworld. Keying by ResourceKey meant an unload event from
  // the old Level could clear screen registrations belonging to the newly
  // loaded Level, leaving robot screens/keyboard routing dead after reload.
  private val worlds = mutable.AnyRefMap.empty[Level, Cache[String, ManagedEnvironment]]

  private def components(level: Level) = {
    worlds.getOrElseUpdate(level,
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
      components(level).invalidateAll(components(level).asMap().asScala.filter(_._2 == component).keys.asJava)
      components(level).cleanUp()
    }
  }

  def get(level: Level, address: String): Option[ManagedEnvironment] = this.synchronized {
    components(level).cleanUp()
    Option(components(level).getIfPresent(address))
  }

  def worldUnloaded(e: LevelEvent.Unload): Unit = e.getLevel match {
    case level: Level => clear(level)
    case _ =>
  }

  protected def clear(level: Level): Unit = this.synchronized {
    worlds.remove(level).foreach { cache =>
      cache.invalidateAll()
      cache.cleanUp()
    }
  }
}
