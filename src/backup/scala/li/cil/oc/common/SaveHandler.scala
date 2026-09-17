package li.cil.oc.common

import java.io
import java.io._
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.SafeThreadPool
import li.cil.oc.util.ThreadPoolFactory
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks

import scala.collection.mutable

/**
 * 原生 Lua 状态用来把内核（kernel）与栈（stack）数据存到独立文件里，
 * 而不是直接塞进方块实体的 NBT，避免方块实体数据过大。
 *
 * 1.21.1 迁移要点：
 *  - `DimensionManager.getCurrentSaveRootDirectory` 已移除，改用
 *    `MinecraftServer#getWorldPath(LevelResource.ROOT)`；服务端实例从
 *    `ServerLifecycleHooks.getCurrentServer` 取（与存档目录同一来源）。
 *  - 维度不再有数字 id：目录名与 NBT 里的 `dimension` 改为
 *    `ResourceKey#location` 的字符串形式（`minecraft:overworld` → `minecraft_overworld`，
 *    冒号在 Windows 文件名里非法，必须替换）。
 *  - `ChunkCoordIntPair` → `ChunkPos`，`chunkXPos / chunkZPos` → `x / z`。
 *  - `NBTTagCompound` 存取方法改写为 1.21.1 名字（`putInt / getInt / putString / ...`）。
 *  - 原实现按 `SystemUtils.isJavaVersionAtLeast(JAVA_1_7)` 在 Java 6 回退实现之间切换；
 *    本工程最低 Java 21，`visitJava16` 已删除，只保留 `Files.walkFileTree` 版本。
 *  - 不再使用 `@SubscribeEvent`，改为 [[initialize]] 里显式注册（Scala object 的
 *    注解在 NeoForge 下不可靠）。
 *
 * 说明：本文件**不涉及** `SavedData` / `DimensionDataStorage`——原实现就是把
 * 辅助数据写进存档目录下的普通文件（`opencomputers_neo/state/<维度>/<区块>/<名字>`），
 * 与 1.7.10 的 `ISaveHandler` / `WorldSavedData` 无关。
 */
object SaveHandler {
  private val uuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"

  private val TimeToHoldOntoOldSaves = 60 * 1000

  // 这是极其丑陋的一个 hack，但它能工作；否则就得把这份状态一路透传给所有会被保存的东西，
  // 而其中 99% 根本不需要知道它。目的：在给客户端发描述包时跳过文件系统刷盘与机器状态持久化，
  // 那些操作既慢又完全没有必要。
  var savingForClients = false

  class SaveDataEntry(val data: Array[Byte], val pos: ChunkPos, val name: String, val dimension: String) extends Runnable {
    override def run(): Unit = {
      val path = statePath
      val dimPath = new io.File(path, dimension)
      val chunkPath = new io.File(dimPath, s"${this.pos.x}.${this.pos.z}")
      chunkDirs.add(chunkPath)
      if (!chunkPath.exists()) {
        chunkPath.mkdirs()
      }
      val file = new io.File(chunkPath, this.name)
      try {
        // val fos = new GZIPOutputStream(new io.FileOutputStream(file))
        val fos = new io.BufferedOutputStream(new io.FileOutputStream(file))
        fos.write(this.data)
        fos.close()
      }
      catch {
        case e: io.IOException => OpenComputers.log.warn(s"Error saving auxiliary tile entity data to '${file.getAbsolutePath}.", e)
      }
    }
  }

  val stateSaveHandler: SafeThreadPool = ThreadPoolFactory.createSafePool("SaveHandler", 1)

  val chunkDirs = new ConcurrentLinkedDeque[io.File]()
  val saving = mutable.HashMap.empty[String, Future[_]]

  /**
   * 存档根目录（`saves/<世界>`）。服务端未启动（例如关闭流程中）时退化为当前工作目录，
   * 保证不会抛 NPE。
   */
  private def saveRoot: io.File = Option(ServerLifecycleHooks.getCurrentServer) match {
    case Some(server) => server.getWorldPath(LevelResource.ROOT).toFile
    case None => new io.File(".")
  }

  def savePath = new io.File(saveRoot, Settings.savePath)

  def statePath = new io.File(savePath, "state")

  /** 维度的目录名 / NBT 表示：`ResourceKey#location` 的字符串，冒号替换为下划线。 */
  private def dimensionName(level: Level): String =
    Option(level.dimension()).map(_.location().toString.replace(':', '_')).getOrElse("unknown")

  def scheduleSave(host: MachineHost, nbt: CompoundTag, name: String, data: Array[Byte]): Unit = {
    scheduleSave(BlockPosition(host), nbt, name, data)
  }

  def scheduleSave(host: MachineHost, nbt: CompoundTag, name: String, save: CompoundTag => Unit): Unit = {
    scheduleSave(host, nbt, name, writeNBT(save))
  }

  def scheduleSave(host: EnvironmentHost, nbt: CompoundTag, name: String, save: CompoundTag => Unit): Unit = {
    scheduleSave(BlockPosition(host), nbt, name, writeNBT(save))
  }

  def scheduleSave(world: Level, x: Double, z: Double, nbt: CompoundTag, name: String, data: Array[Byte]): Unit = {
    scheduleSave(BlockPosition(x, 0, z, world), nbt, name, data)
  }

  def scheduleSave(world: Level, x: Double, z: Double, nbt: CompoundTag, name: String, save: CompoundTag => Unit): Unit = {
    scheduleSave(world, x, z, nbt, name, writeNBT(save))
  }

  def scheduleSave(position: BlockPosition, nbt: CompoundTag, name: String, data: Array[Byte]): Unit = {
    position.world.foreach(world => scheduleSave(world, position, nbt, name, data))
  }

  /**
   * 把数据登记到后台线程池，并把维度 / 区块坐标写进 NBT。
   *
   * 必须记录维度与区块坐标：读档时拿不到位置（计算机可能已被搬走）。
   */
  private def scheduleSave(world: Level, position: BlockPosition, nbt: CompoundTag, name: String, data: Array[Byte]): Unit = {
    // 尽量排除被包装的 / 客户端的世界：只有服务端世界才有存档目录。
    world match {
      case _: ServerLevel =>
        val dimension = dimensionName(world)
        val chunk = new ChunkPos(position.x >> 4, position.z >> 4)

        nbt.putString("dimension", dimension)
        nbt.putInt("chunkX", chunk.x)
        nbt.putInt("chunkZ", chunk.z)

        scheduleSave(dimension, chunk, name, data)
      case _ =>
    }
  }

  private def writeNBT(save: CompoundTag => Unit) = {
    val tmpNbt = new CompoundTag()
    save(tmpNbt)
    val baos = new ByteArrayOutputStream()
    val dos = new DataOutputStream(baos)
    NbtIo.write(tmpNbt, dos)
    dos.flush()
    baos.toByteArray
  }

  def loadNBT(nbt: CompoundTag, name: String): CompoundTag = {
    val data = load(nbt, name)
    if (data.length > 0) {
      try {
        val bais = new ByteArrayInputStream(data)
        val dis = new DataInputStream(bais)
        NbtIo.read(dis)
      }
      catch {
        case t: Throwable =>
          OpenComputers.log.warn("There was an error trying to restore a block's state from external data. This indicates that data was somehow corrupted.", t)
          new CompoundTag()
      }
    }
    else new CompoundTag()
  }

  def load(nbt: CompoundTag, name: String): Array[Byte] = {
    // 此时还没有世界，只能依赖存档时记录的维度与区块；这同时也让被搬动过的计算机
    // （例如 Redstone in Motion）能正确读回数据。
    val dimension = nbt.getString("dimension")
    val chunk = new ChunkPos(nbt.getInt("chunkX"), nbt.getInt("chunkZ"))

    // 等待同一个文件的上一次保存任务完成，避免读到旧版本。
    saving.get(name).foreach(f => try {
      f.get(120L, TimeUnit.SECONDS)
    } catch {
      case e: TimeoutException => OpenComputers.log.warn("Waiting for state data to save took two minutes! Aborting.")
      case e: CancellationException => // NO-OP
    })
    saving.remove(name)

    load(dimension, chunk, name)
  }

  def scheduleSave(dimension: String, chunk: ChunkPos, name: String, data: Array[Byte]): Unit = {
    if (chunk == null) throw new IllegalArgumentException("chunk is null")
    else {
      // 不检查是否已经有同一文件的保存任务，可以换来更好的并发，代价是多几次写操作。
      stateSaveHandler.withPool(_.submit(new SaveDataEntry(data, chunk, name, dimension))).foreach(saving.put(name, _))
    }
  }

  def load(dimension: String, chunk: ChunkPos, name: String): Array[Byte] = {
    if (chunk == null) throw new IllegalArgumentException("chunk is null")

    val path = statePath
    val dimPath = new io.File(path, dimension)
    val chunkPath = new io.File(dimPath, s"${chunk.x}.${chunk.z}")
    val file = new io.File(chunkPath, name)
    if (!file.exists()) return Array.empty[Byte]
    try {
      // val bis = new io.BufferedInputStream(new GZIPInputStream(new io.FileInputStream(file)))
      val bis = new io.BufferedInputStream(new io.FileInputStream(file))
      val bos = new io.ByteArrayOutputStream
      val buffer = new Array[Byte](8 * 1024)
      var read = 0
      do {
        read = bis.read(buffer)
        if (read > 0) {
          bos.write(buffer, 0, read)
        }
      } while (read >= 0)
      bis.close()
      bos.toByteArray
    }
    catch {
      case e: io.IOException =>
        OpenComputers.log.warn("Error loading auxiliary tile entity data.", e)
        Array.empty[Byte]
    }
  }

  def cleanSaveData(): Unit = {
    // 删除空目录，保持 state 目录整洁。
    val emptyDirs = savePath.listFiles(new FileFilter {
      override def accept(file: File) = file.isDirectory &&
        // 只考虑文件系统目录（UUID 形式）。
        file.getName.matches(uuidRegex) &&
        // 未缓冲文件系统的 save() 会刷新修改时间，用它可以避免删掉正在使用的目录。
        System.currentTimeMillis() - file.lastModified() > TimeToHoldOntoOldSaves && {
        val list = file.list()
        list == null || list.isEmpty
      }
    })
    if (emptyDirs != null) {
      emptyDirs.filter(_ != null).foreach(_.delete())
    }
  }

  def onWorldLoad(e: LevelEvent.Load): Unit = {
    // 世界加载时触摸所有外部保存的数据，避免它们在下一次保存时被删掉
    //（“当前时间 - 保存时间”在重新载入世界后通常会超过超时阈值）。
    // 本工程最低 Java 21，直接使用 Files.walkFileTree 版本。
    if (statePath.isDirectory) SaveHandlerJava17Functionality.visitJava17(statePath)
  }

  def onWorldSave(e: LevelEvent.Save): Unit = {
    stateSaveHandler.withPool(_.submit(new Runnable {
      override def run(): Unit = cleanSaveData()
    }))
  }

  /** 注册世界加载 / 保存监听；由主类在 mod 初始化时调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (e: LevelEvent.Load) => onWorldLoad(e))
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (e: LevelEvent.Save) => onWorldSave(e))
  }
}

object SaveHandlerJava17Functionality {
  def visitJava17(statePath: File): Unit = {
    Files.walkFileTree(statePath.toPath, new FileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes) = {
        file.toFile.setLastModified(System.currentTimeMillis())
        FileVisitResult.CONTINUE
      }

      override def visitFileFailed(file: Path, exc: IOException) = FileVisitResult.CONTINUE

      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = FileVisitResult.CONTINUE

      override def postVisitDirectory(dir: Path, exc: IOException) = FileVisitResult.CONTINUE
    })
  }
}
