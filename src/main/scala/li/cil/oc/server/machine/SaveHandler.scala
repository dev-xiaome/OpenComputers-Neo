package li.cil.oc.server.machine

import java.io
import java.util.concurrent.{CancellationException, ConcurrentLinkedDeque, Future, TimeUnit, TimeoutException}

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.util.ThreadPoolFactory
import net.minecraft.nbt.{CompoundTag, NbtAccounter, NbtIo}
import net.minecraft.world.level.storage.LevelResource

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 把“辅助数据”（机器的 tmpfs 内容等）写到存档目录下独立文件里，而不是直接塞进
 * 方块实体的 NBT，避免区块数据过大。
 *
 * 上游实现在 `li.cil.oc.common.SaveHandler`，该文件目前仍是 1.7.10 的原始拷贝
 * （引用 `DimensionManager`、`WorldServer`、`ChunkCoordIntPair` 等已不存在的类型），
 * 无法进入编译集，因此这里先行移植一份可用实现。
 *
 * 与上游的差异（降级点）：
 *  - 维度/区块坐标改为从 [[li.cil.oc.api.machine.MachineHost]] 的 `world` /
 *    `xPosition` / `zPosition` 直接推导（1.21.1 里维度是 `ResourceLocation`，
 *    这里用 `dimension().location().hashCode()` 作为稳定的目录名）。
 *  - 存档根目录改为 `MinecraftServer#getWorldPath(LevelResource.ROOT)`。
 *
 * TODO(common.SaveHandler): `common/SaveHandler.scala` 移植完成后删除本文件，
 * 并把 `Machine.scala` 里的调用改回 `li.cil.oc.common.SaveHandler`。
 */
private[machine] object SaveHandler {

  // 与上游同名：发送给客户端的描述包不落盘。
  var savingForClients = false

  private val stateSaveHandler = ThreadPoolFactory.createSafePool("SaveHandler", 1)

  private val saving = mutable.HashMap.empty[String, Future[_]]

  private val chunkDirs = new ConcurrentLinkedDeque[io.File]()

  /** 存档下的 OC 数据根目录。 */
  def savePath: io.File = {
    val server = MachineCompat.server
    if (server == null) new io.File(Settings.savePath)
    else server.getWorldPath(LevelResource.ROOT).resolve(Settings.savePath).toFile
  }

  def statePath: io.File = new io.File(savePath, "state")

  // ----------------------------------------------------------------------- //

  def scheduleSave(host: MachineHost, nbt: CompoundTag, name: String, data: Array[Byte]): Unit = {
    val world = host.world
    if (world != null) {
      val dimension = world.dimension().location().hashCode()
      val chunkX = (host.xPosition.toInt) >> 4
      val chunkZ = (host.zPosition.toInt) >> 4

      // 必须把定位信息写进 NBT：加载时世界/位置可能还不可用，
      // 而且机器可能被移动过。
      nbt.putInt("dimension", dimension)
      nbt.putInt("chunkX", chunkX)
      nbt.putInt("chunkZ", chunkZ)

      scheduleSave(dimension, chunkX, chunkZ, name, data)
    }
  }

  def scheduleSave(host: MachineHost, nbt: CompoundTag, name: String, save: CompoundTag => Unit): Unit = {
    scheduleSave(host, nbt, name, writeNBT(save))
  }

  def scheduleSave(dimension: Int, chunkX: Int, chunkZ: Int, name: String, data: Array[Byte]): Unit = {
    stateSaveHandler.withPool(_.submit(new SaveDataEntry(data, chunkX, chunkZ, name, dimension))).foreach(saving.put(name, _))
  }

  // ----------------------------------------------------------------------- //

  def loadNBT(nbt: CompoundTag, name: String): CompoundTag = {
    val data = load(nbt, name)
    if (data.length > 0) try {
      readNBT(data)
    }
    catch {
      case t: Throwable =>
        OpenComputers.log.warn("There was an error trying to restore a block's state from external data. This indicates that data was somehow corrupted.", t)
        new CompoundTag()
    }
    else new CompoundTag()
  }

  def load(nbt: CompoundTag, name: String): Array[Byte] = {
    // 加载时可能还没有世界上下文，因此依赖保存时记录的维度/区块坐标。
    val dimension = nbt.getInt("dimension")
    val chunkX = nbt.getInt("chunkX")
    val chunkZ = nbt.getInt("chunkZ")

    // 等最后一次同名的保存任务结束，避免读到旧文件。
    saving.remove(name).foreach(f => try {
      f.get(120L, TimeUnit.SECONDS)
    }
    catch {
      case _: TimeoutException => OpenComputers.log.warn("Waiting for state data to save took two minutes! Aborting.")
      case _: CancellationException => // NO-OP
    })

    load(dimension, chunkX, chunkZ, name)
  }

  def load(dimension: Int, chunkX: Int, chunkZ: Int, name: String): Array[Byte] = {
    val file = new io.File(new io.File(new io.File(statePath, dimension.toString), s"$chunkX.$chunkZ"), name)
    if (!file.exists()) return Array.empty[Byte]
    try {
      val bis = new io.BufferedInputStream(new io.FileInputStream(file))
      try {
        val bos = new io.ByteArrayOutputStream
        val buffer = new Array[Byte](8 * 1024)
        var read = bis.read(buffer)
        while (read >= 0) {
          bos.write(buffer, 0, read)
          read = bis.read(buffer)
        }
        bos.toByteArray
      }
      finally bis.close()
    }
    catch {
      case e: io.IOException =>
        OpenComputers.log.warn(s"Error loading auxiliary tile entity data from '${file.getAbsolutePath}'.", e)
        Array.empty[Byte]
    }
  }

  // ----------------------------------------------------------------------- //

  private def writeNBT(save: CompoundTag => Unit): Array[Byte] = {
    val tmpNbt = new CompoundTag()
    save(tmpNbt)
    val baos = new io.ByteArrayOutputStream()
    val dos = new io.DataOutputStream(baos)
    // TODO(1.21.1): 原 `CompressedStreamTools.write`；
    // 1.21.1 改为 `NbtIo.writeCompressed(tag, stream)`。
    NbtIo.writeCompressed(tmpNbt, dos)
    dos.close()
    baos.toByteArray
  }

  private def readNBT(data: Array[Byte]): CompoundTag = {
    val dis = new io.DataInputStream(new io.ByteArrayInputStream(data))
    try {
      // TODO(1.21.1): 原 `CompressedStreamTools.read`；
      // 1.21.1 需要显式给出 `NbtAccounter`。
      NbtIo.readCompressed(dis, NbtAccounter.unlimitedHeap())
    }
    finally dis.close()
  }

  private class SaveDataEntry(val data: Array[Byte], val chunkX: Int, val chunkZ: Int, val name: String, val dimension: Int) extends Runnable {
    override def run(): Unit = {
      val chunkPath = new io.File(new io.File(statePath, dimension.toString), s"$chunkX.$chunkZ")
      chunkDirs.add(chunkPath)
      if (!chunkPath.exists()) {
        chunkPath.mkdirs()
      }
      val file = new io.File(chunkPath, name)
      try {
        val fos = new io.BufferedOutputStream(new io.FileOutputStream(file))
        try fos.write(data)
        finally fos.close()
      }
      catch {
        case e: io.IOException => OpenComputers.log.warn(s"Error saving auxiliary tile entity data to '${file.getAbsolutePath}'.", e)
      }
    }
  }

  /** 供调试/清理用的已写入目录快照。 */
  def snapshotChunkDirs: Seq[io.File] = chunkDirs.iterator().asScala.toSeq
}
