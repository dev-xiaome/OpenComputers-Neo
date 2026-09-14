package li.cil.oc.common.component

import com.google.common.cache.{Cache, CacheBuilder}
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 按「维度 + 节点地址」索引已加载组件的缓存（屏幕 / 文本缓冲用）。
 *
 * 对应 1.7.10 的 `li.cil.oc.client.ComponentTracker` / `li.cil.oc.server.ComponentTracker`
 * 的**数据部分**。原实现的公共父类 `li.cil.oc.common.ComponentTracker` 依赖尚未移植的
 * 1.7.10 维度 id（`Level#provider.dimensionId`），因此这里改用
 * `Level#dimension().location()` 作为键，行为等价且线程安全。
 *
 * 说明：1.7.10 的客户端 / 服务端两个子类只差一个「是否清空」的条件，
 * 1.21.1 侧两者共用同一份实现即可（服务端与客户端在不同的 JVM 中运行）。
 */
object ComponentTracker {
  private val worlds = mutable.Map.empty[ResourceLocation, Cache[String, ManagedEnvironment]]

  private def components(world: Level): Cache[String, ManagedEnvironment] = this.synchronized {
    worlds.getOrElseUpdate(world.dimension().location,
      CacheBuilder.newBuilder().
        weakValues().
        asInstanceOf[CacheBuilder[String, ManagedEnvironment]].
        build[String, ManagedEnvironment]())
  }

  def add(world: Level, address: String, component: ManagedEnvironment): Unit = this.synchronized {
    components(world).put(address, component)
  }

  def remove(world: Level, component: ManagedEnvironment): Unit = this.synchronized {
    val cache = components(world)
    val stale = cache.asMap().entrySet().toArray.toSeq.collect {
      case entry: java.util.Map.Entry[_, _] if entry.getValue == component => entry.getKey.toString
    }
    cache.invalidateAll(stale.toSeq.asJava)
    cache.cleanUp()
  }

  def get(world: Level, address: String): Option[ManagedEnvironment] = this.synchronized {
    val cache = components(world)
    cache.cleanUp()
    Option(cache.getIfPresent(address))
  }

  /** 卸载世界时清空该维度的缓存（由 `LevelEvent.Unload` 触发）。 */
  def clear(world: Level): Unit = this.synchronized {
    val cache = components(world)
    cache.invalidateAll()
    cache.cleanUp()
  }
}
