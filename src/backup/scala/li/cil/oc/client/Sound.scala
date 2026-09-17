package li.cil.oc.client

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.{AbstractTickableSoundInstance, SoundInstance}
import net.minecraft.client.sounds.SoundManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.{SoundEvent, SoundSource}
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.LevelEvent

import scala.collection.mutable
import scala.ref.WeakReference

/**
 * 客户端循环音效管理（例如正在运行的计算机 / 机器人本体音）。
 *
 * ==1.7.10 -> 1.21.1 的结构性差异（本文件的核心降级点）==
 * 1.7.10 直接操作 paulscode 的 `SoundSystem`（`SoundManager.sndSystem`）来自行
 * `newSource` / `setVolume` / `setPosition`，从而做到「循环播放 + 动态音量 / 位置」。
 * 1.21.1 里 paulscode 仍在，但 `SoundManager#sndSystem` 已经不可访问，因此改为使用
 * 原生的循环音：
 *
 *  - 用 [[net.minecraft.client.resources.sounds.AbstractTickableSoundInstance]] 的子类，
 *    `isLooping` 恒为 true，位置 / 音量通过覆写 `getX/getY/getZ/getVolume` 按需计算；
 *  - 用 `Minecraft#getSoundManager` 的 `play` / `stop` 播放与停止；
 *  - 原版 [[net.minecraft.client.sounds.SoundEngine]] 每 tick 会把音量与位置写回声道，
 *    所以不需要（也无法）手动 setVolume / setPosition；
 *  - 「方块」音效类别的音量（1.7.10 的 `lastVolume`）由原版引擎按 `SoundSource.BLOCKS`
 *    自动叠加，这里只需要再乘上 OC 自己的配置音量 `Settings.get.soundVolume`。
 *
 * 线程模型保持不变：`startLoop` / `stopLoop` / `updatePosition` 可能从服务端线程调用，
 * 因此它们只往 [[commandQueue]] 里塞命令，真正的引擎调用统一在客户端 tick 里做。
 *
 * ==已降级的部分==
 * 1.7.10 的 `onTick` 里还有一段「读 `sounds/preload.cfg`，用 `SoundSystem#newSource`
 * 逐个预热音频缓冲」的逻辑。1.21.1 里 paulscode 的 `SoundSystem` 不可访问，而原版
 * `SoundEngine#requestPreload` 又不对外暴露（`SoundManager` 没有转发），因此这块整体
 * 降级为空实现，详见 [[preloadConfiguredSounds]]。
 * 相对地，1.7.10 的「零音量预热」（`startLoop(null, ...)`，由 `common.EventHandler`
 * 在登录时调用）仍然有效：见 [[LoopingSoundInstance.canStartSilent]]。
 *
 * 接线方式（`client.Proxy#clientSetup` 里已经有一行）：
 * {{{
 *   li.cil.oc.client.Sound.initialize()
 * }}}
 */
object Sound {
  /** 正在循环播放的音效：宿主方块实体 -> 循环音实例。 */
  private val sources = mutable.WeakHashMap.empty[BlockEntity, LoopingSoundInstance]

  /** 待处理命令队列（最大堆：`when` 最小的排在堆顶）。 */
  private val commandQueue = mutable.PriorityQueue.empty[Command]

  /** 客户端音效管理器；tick 时刷新。 */
  private var soundManager: SoundManager = _

  private var initialized = false

  /** 在 NeoForge 事件总线上挂上客户端 tick 与世界卸载监听（只生效一次）。 */
  def initialize(): Unit = {
    if (initialized) return
    initialized = true
    preloadConfiguredSounds()
    NeoForge.EVENT_BUS.addListener((e: ClientTickEvent.Post) => onTick())
    // 1.7.10 的 `WorldEvent.Unload` -> 1.21.1 的 `LevelEvent.Unload`，优先级保持 LOWEST。
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (e: LevelEvent.Unload) => onWorldUnload(e))
  }

  /**
   * 预热 `assets/opencomputers_neo/sounds/preload.cfg` 里列出的音频。
   *
   * TODO(client.sound): 目前是空实现。1.7.10 直接调 paulscode 的
   * `SoundSystem#newSource` / `activate` / `removeSource` 把缓冲塞进音效库；1.21.1 里
   * `SoundManager#sndSystem` 已不可访问，而唯一可用的替代 `SoundEngine#requestPreload`
   * 既没有从 `SoundManager` 转发出来，也需要先拿到内部的 `Sound` 对象（`resolve` 之后
   * 才有）。后续可选的补法：
   *  1. 反射/AT 访问 `SoundManager#soundEngine`，再对每个 `ResourceLocation` 走
   *     `getSoundEvent(...).getSound(random)` 拿 `Sound` 后调 `requestPreload`；
   *  2. 或者退一步：在首次需要时依赖原版自身的缓冲缓存（当前的实际行为）。
   * 这一段缺失只影响「首次播放的加载延迟」，不影响功能。
   */
  private def preloadConfiguredSounds(): Unit = {
    // 有意为空，见上面的 TODO。
  }

  /** 客户端音效管理器（1.7.10 的 `manager` 字段；`sndSystem` 已不可访问，见类注释）。 */
  def manager: SoundManager = soundManager

  /** 开始循环播放某个音效。`delay` 是毫秒。 */
  def startLoop(tileEntity: BlockEntity, name: String, volume: Float = 1f, delay: Long = 0): Unit = {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new StartCommand(System.currentTimeMillis() + delay, new WeakReference[BlockEntity](tileEntity), name, volume, tileEntity == null)
      }
    }
  }

  /** 停止某个宿主上的循环音效。 */
  def stopLoop(tileEntity: BlockEntity): Unit = {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new StopCommand(new WeakReference[BlockEntity](tileEntity), tileEntity == null)
      }
    }
  }

  /**
   * 通知某个宿主的位置变了。
   *
   * 1.7.10 需要显式 `soundSystem.setPosition`；1.21.1 的循环音位置是每 tick 从宿主
   * 重新计算的（覆写了 `getX/getY/getZ`），因此这里只保留调用面与命令队列的顺序语义，
   * 实际不做额外工作。
   */
  def updatePosition(tileEntity: BlockEntity): Unit = {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new UpdatePositionCommand(new WeakReference[BlockEntity](tileEntity), tileEntity == null)
      }
    }
  }

  /**
   * 客户端 tick：刷新音量并处理命令队列。
   *
   * 1.7.10 用 `java.util.Timer` 每 50ms 把工作投递给客户端 tick 执行（因为当时是直接操作
   * 线程安全的 paulscode `SoundSystem`）。1.21.1 的原版音效引擎有线程约束（只能在客户端
   * 线程调用），所以这里直接在本 tick 里处理，行为等价且少一个定时器线程。
   */
  private def onTick(): Unit = {
    val mc = Minecraft.getInstance()
    if (mc == null) return
    soundManager = mc.getSoundManager
    if (soundManager == null) return

    processQueue()
  }

  /** 执行所有到期的命令；宿主已被回收（弱引用为空）的命令直接丢弃。 */
  private def processQueue(): Unit = {
    if (commandQueue.nonEmpty) {
      commandQueue.synchronized {
        val now = System.currentTimeMillis()
        while (commandQueue.nonEmpty && commandQueue.head.when <= now) {
          val command = commandQueue.dequeue()
          // `isPreload` 的命令（1.7.10 的 `startLoop(null, ...)` 预热）本来就没有宿主，
          // 不能按「弱引用为空」丢掉；它的位置取原点，播一 tick 后自行停止。
          if (command.isPreload || command.tileEntity.get.isDefined) {
            try command() catch {
              case t: Throwable => OpenComputers.log.warn("Error processing sound command.", t)
            }
          }
        }
      }
    }
  }

  /**
   * 世界卸载：停掉所有循环音并清空队列。
   *
   * 1.7.10 的事件是 `WorldEvent.Unload`（`cpw.mods.fml`），1.21.1 换成 NeoForge 的
   * [[net.neoforged.neoforge.event.level.LevelEvent.Unload]]。
   */
  private def onWorldUnload(event: LevelEvent.Unload): Unit = {
    commandQueue.synchronized(commandQueue.clear())
    sources.synchronized {
      for (sound <- sources.values) {
        try sound.stopPlayback() catch {
          case _: Throwable => // 忽略：世界卸载时任何异常都不该影响退出流程。
        }
      }
    }
    sources.clear()
  }

  // ----------------------------------------------------------------------- //
  // 命令队列
  // ----------------------------------------------------------------------- //

  private abstract class Command(val when: Long, val tileEntity: WeakReference[BlockEntity], val isPreload: Boolean) extends Ordered[Command] {
    def apply(): Unit

    /** 最大堆语义：`when` 越小越先出队（`that.when - when` 会有 Long 溢出风险，故显式比较）。 */
    override def compare(that: Command): Int = java.lang.Long.compare(that.when, when)
  }

  private class StartCommand(when: Long, tileEntity: WeakReference[BlockEntity], val name: String, val volume: Float, isPreload: Boolean) extends Command(when, tileEntity, isPreload) {
    override def apply(): Unit = {
      sources.synchronized {
        val host = tileEntity.get.orNull
        val sound = sources.get(host) match {
          case Some(existing) if !existing.isStopped => existing
          case _ =>
            val fresh = new LoopingSoundInstance(host, name, volume)
            sources.update(host, fresh)
            fresh
        }
        sound.play()
      }
    }
  }

  private class StopCommand(tileEntity: WeakReference[BlockEntity], isPreload: Boolean) extends Command(System.currentTimeMillis() + 1, tileEntity, isPreload) {
    override def apply(): Unit = {
      val host = tileEntity.get.orNull
      sources.synchronized {
        sources.remove(host) match {
          case Some(sound) => sound.stopPlayback()
          case _ =>
        }
      }
      commandQueue.synchronized {
        // 移除该宿主剩余的排队命令。原来的写法是
        // `commandQueue ++= commandQueue.dequeueAll.filter(...)`——`dequeueAll` 已经把这些
        // 元素从队列里摘掉了，再 `++=` 回去等于空操作；这里按注释描述的意图实现成
        // 「真的移掉」，避免宿主已经没了还在排队播放。
        val remaining = commandQueue.dequeueAll.filterNot(_.tileEntity.get.orNull == host)
        commandQueue ++= remaining
      }
    }
  }

  private class UpdatePositionCommand(tileEntity: WeakReference[BlockEntity], isPreload: Boolean) extends Command(System.currentTimeMillis(), tileEntity, isPreload) {
    override def apply(): Unit = {
      sources.synchronized {
        sources.get(tileEntity.get.orNull) match {
          case Some(sound) => sound.updatePosition()
          case _ =>
        }
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // 循环音实例
  // ----------------------------------------------------------------------- //

  /**
   * 循环音实例（1.7.10 里叫 `PseudoLoopingStream`）。
   *
   * 与 1.7.10 的差别：
   *  - 不再有 `newSource` / `activate` / `play` / `stop` 这一套 paulscode 调用，
   *    改为原版 `SoundManager#play` / `stop`；
   *  - 位置与音量改成覆写 getter 按需计算，因此 `updatePosition` / `updateVolume`
   *    不再需要手动推送（保留方法只为兼容调用面）；
   *  - `host` 为 null 时（1.7.10 的「预热」调用 `startLoop(null, ...)`）位置按原点算。
   */
  private class LoopingSoundInstance(host: BlockEntity, name: String, val configuredVolume: Float)
    extends AbstractTickableSoundInstance(
      SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, name)),
      SoundSource.BLOCKS,
      SoundInstance.createUnseededRandom) {

    /** 宿主的弱引用；不再让循环音持有宿主，避免阻止区块卸载。 */
    private val owner = new WeakReference[BlockEntity](host)

    /** 循环播放：`SoundEngine` 会以 `isLooping` 判断是否无缝循环。 */
    override def isLooping: Boolean = true

    /**
     * 音量 = 实例音量乘 OC 配置音量。
     *
     * 1.7.10 还乘了「方块」音效类别的音量（`lastVolume`），1.21.1 由原版引擎按
     * [[net.minecraft.sounds.SoundSource.BLOCKS]] 自动叠加，这里不能再乘一遍。
     */
    override def getVolume: Float = configuredVolume * Settings.get.soundVolume

    override def getX: Double = hostPosition.map(_._1).getOrElse(0d)

    override def getY: Double = hostPosition.map(_._2).getOrElse(0d)

    override def getZ: Double = hostPosition.map(_._3).getOrElse(0d)

    /** 宿主方块中心；宿主已回收时返回 None。 */
    private def hostPosition: Option[(Double, Double, Double)] = owner.get.map { entity =>
      val pos = entity.getBlockPos
      (pos.getX + 0.5, pos.getY + 0.5, pos.getZ + 0.5)
    }

    /** 兼容 1.7.10 的 `soundSystem.setPosition`：位置按需计算，无需动作。 */
    def updatePosition(): Unit = ()

    /** 兼容 1.7.10 的 `soundSystem.setVolume`：音量按需计算，无需动作。 */
    def updateVolume(): Unit = ()

    /** 开始（或继续）播放；已经在播时不重复 `play`，避免重复占用声道。 */
    def play(): Unit = {
      val sm = soundManager
      if (sm != null && !isStopped && !sm.isActive(this)) {
        sm.play(this)
      }
    }

    /**
     * 停止播放。
     *
     * 不能直接叫 `stop`：父类的 `AbstractTickableSoundInstance#stop` 是 protected final。
     */
    def stopPlayback(): Unit = {
      stop()
      val sm = soundManager
      if (sm != null) {
        // 立刻静音（`stop()` 只置标志，引擎最迟下一 tick 才会真正停声道）。
        try sm.stop(this) catch {
          case _: Throwable => // 引擎尚未加载时忽略。
        }
      }
    }

    override def tick(): Unit = {
      if (owner.get.isEmpty || owner.get.exists(_.isRemoved)) {
        // 宿主没了（区块卸载 / 方块被拆 / 实体被移除）：循环音必须自己收尾，
        // 否则会永远挂在 ticking sounds 里。
        stopPlayback()
      }
    }

    /**
     * 预热语义：1.7.10 会用 `startLoop(null, "computer_running", 0f, 0)` 把音效
     * 「激活一下就摘掉」，目的是让音频缓冲先加载好。原版默认会跳过零音量音效，
     * 这里放开，让缓冲区仍然被加载（等价原实现的预热效果）。
     */
    override def canStartSilent(): Boolean = true
  }
}
