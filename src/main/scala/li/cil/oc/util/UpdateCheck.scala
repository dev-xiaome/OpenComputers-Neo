package li.cil.oc.util

import java.io.InputStreamReader
import java.net.URL
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import net.neoforged.fml.ModList

import java.util.Objects
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

/**
 * 启动时的版本检查（查询 GitHub releases）。
 *
 * 1.21.1 迁移要点：
 *  - `cpw.mods.fml.common.Loader.instance.getIndexedModList` → `ModList.get().getMods`，
 *    当前 mod 版本通过 [[li.cil.oc.OpenComputers.Version]] 读取（构建时即已确定）。
 *  - `cpw.mods.fml.common.versioning.ComparableVersion` 在新版不存在，这里用本地
 *    的 [[compareVersions]] 做数值段比较，仅用于“是否有更新”的判断。
 */
object UpdateCheck {
  private val releasesUrl = new URL("https://api.github.com/repos/MightyPirates/OpenComputers/releases")

  var info: Future[Option[Release]] = Future {
    initialize()
  }

  private def initialize(): Option[Release] = {
    // Keep the version template split up so it's not replaced with the actual version...
    if (Settings.get.updateCheck && OpenComputers.Version != ("@" + "VERSION" + "@")) {
      try {
        OpenComputers.log.info("Starting OpenComputers version check.")
        val reader = new JsonReader(new InputStreamReader(releasesUrl.openStream()))
        reader.beginArray()
        val candidates = mutable.ArrayBuffer.empty[Release]
        while (reader.hasNext) {
          val release: Release = new Gson().fromJson(reader, classOf[Release])
          if (!release.prerelease) {
            // Handle the newer version format: mcVersion/release
            var versionMatch = true
            if (release.tag_name.contains("/")) {
              val tagNameParts = release.tag_name.split("/", 2)
              if (tagNameParts.length >= 2) {
                release.tag_name = tagNameParts(1)
                versionMatch = Objects.equals(OpenComputers.McVersion, tagNameParts(0))
              }
            }
            if (versionMatch) {
              candidates += release
            }
          }
        }
        reader.endArray()
        if (candidates.nonEmpty) {
          // 注意：不能直接用 maxBy，因为 (Array[Int], String) 没有隐式 Ordering。
          val latest = candidates.reduceLeft((a, b) =>
            if (compareVersions(versionSegments(a.tag_name.stripPrefix("v")), versionSegments(b.tag_name.stripPrefix("v"))) >= 0) a else b)
          val remoteVersion = versionSegments(latest.tag_name.stripPrefix("v"))
          val localVersion = versionSegments(OpenComputers.Version)
          if (compareVersions(remoteVersion, localVersion) > 0) {
            OpenComputers.log.info(s"A newer version of OpenComputers is available: ${latest.tag_name}.")
            return Some(latest)
          }
        }
        OpenComputers.log.info("Running the latest OpenComputers version.")
      }
      catch {
        case t: Throwable => OpenComputers.log.warn("Update check for OpenComputers failed.", t)
      }
    }
    None
  }

  /**
   * 把版本串按“数字段 + 预发布后缀”解析：`1.8.10-beta.2` → `(1, 8, 10)` + `beta.2`。
   * 不构建 ModList 相关的类型，也不依赖 Maven 的 ComparableVersion。
   */
  private def versionSegments(version: String): (Array[Int], String) = {
    val core = version.takeWhile(c => c.isDigit || c == '.')
    val numbers = core.split('.').filter(_.nonEmpty).map {
      case digits if digits.forall(_.isDigit) => digits.toInt
      case digits => digits.takeWhile(_.isDigit).toInt
    }
    (numbers, version.drop(core.length))
  }

  /** 先比较数字段，数字段相同时：有预发布后缀的视为更旧。 */
  private def compareVersions(a: (Array[Int], String), b: (Array[Int], String)): Int = {
    val (aNumbers, aSuffix) = a
    val (bNumbers, bSuffix) = b
    val length = math.max(aNumbers.length, bNumbers.length)
    var i = 0
    while (i < length) {
      val av = if (i < aNumbers.length) aNumbers(i) else 0
      val bv = if (i < bNumbers.length) bNumbers(i) else 0
      if (av != bv) {
        return av.compareTo(bv)
      }
      i += 1
    }
    if (aSuffix.isEmpty && bSuffix.nonEmpty) 1
    else if (aSuffix.nonEmpty && bSuffix.isEmpty) -1
    else aSuffix.compareTo(bSuffix)
  }

  /** 是否有比当前版本更新的版本（供 GUI / 命令查询）。 */
  def isUpdateAvailable: Boolean = info.value.exists(_.exists(_.nonEmpty))

  /** 当前已安装的 mod 版本号（来自 `ModList`；未加载时回退到内置版本常量）。 */
  def localVersion: String =
    ModList.get().getMods.asScala.find(_.getModId == OpenComputers.ID)
      .map(_.getVersion.toString)
      .getOrElse(OpenComputers.Version)

  class Release {
    var tag_name = ""
    var body = ""
    var prerelease = false
  }

}
