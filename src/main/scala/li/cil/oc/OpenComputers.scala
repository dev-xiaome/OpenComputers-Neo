package li.cil.oc

import org.apache.logging.log4j.Logger

import java.io.File

/**
 * OpenComputers Neo 的核心常量与生命周期入口（Scala 侧）。
 *
 * 原版是带 `@Mod` 注解的 Scala object；1.21.1 下入口改为 Java 的
 * [[li.cil.oc.OpenComputersNeo]]，这里仅保留常量与由 Java 侧驱动的初始化逻辑。
 */
object OpenComputers {
  final val ID = "open_computers_neo"

  final val Name = "OpenComputers Neo"

  final val McVersion = "1.21.1"

  final val Version = "1.0.0"

  def log: Logger = OpenComputersNeo.log

  /** 加载配置文件并初始化设置。 */
  def loadSettings(configFile: File): Unit = {
    Settings.load(configFile)
    log.info("Loaded settings from '{}'.", configFile.getName)
  }
}
