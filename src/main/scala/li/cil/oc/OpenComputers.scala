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
  final val ID = "opencomputers_neo"

  final val Name = "OpenComputers Neo"

  final val McVersion = "1.21.1"

  /**
   * 模组版本号。
   *
   * **唯一来源是 `gradle.properties` 里的 `mod_version`**：它经
   * `src/main/templates/META-INF/neoforge.mods.toml` 的 `${mod_version}` 占位符
   * 展开进 mod 元数据，主类在构造期再通过 `ModContainer#getModInfo#getVersion`
   * 读回来（见 [[OpenComputersNeo]] 的构造器）。
   *
   * 因此**改版本号只需要改 `gradle.properties` 一行**，不要在这里硬编码；
   * 未初始化时（单元测试 / 数据生成等场景）退化为 `"dev"`。
   */
  @volatile private var _version: String = "dev"

  def Version: String = _version

  private[oc] def setVersion(value: String): Unit =
    if (value != null && value.nonEmpty) _version = value

  def log: Logger = OpenComputersNeo.log

  /** 加载配置文件并初始化设置。 */
  def loadSettings(configFile: File): Unit = {
    Settings.load(configFile)
    log.info("Loaded settings from '{}'.", configFile.getName)
  }
}


