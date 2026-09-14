package li.cil.oc.server.machine

import org.apache.logging.log4j.{LogManager, Logger}

import java.lang.reflect.Modifier

/**
 * 日志入口的“安全访问器”。
 *
 * 正常情况下直接用 `li.cil.oc.OpenComputers.log` 即可，但在增量编译自检里
 * （只把部分包喂给 scalac，`OpenComputers.scala` 可能不在其中）会解析失败。
 * 这里在类初始化时反射查一次，拿到就缓存，拿不到就退回 log4j 的
 * `LogManager.getLogger`，保证同一份源码在两种场景下都能编译。
 */
private[machine] object MachineLog {

  private lazy val resolved: Logger = resolve()

  def log: Logger = resolved

  private def resolve(): Logger = {
    try {
      val clazz = Class.forName("li.cil.oc.OpenComputers$")
      val module = clazz.getField("MODULE$").get(null)
      val method = clazz.getMethod("log")
      method.invoke(module).asInstanceOf[Logger]
    }
    catch {
      case _: Throwable =>
        // 退回到 log4j：优先复用同名 logger，找不到就用模块名。
        // 注意 `LogManager.exists` 返回的是 `org.apache.logging.log4j.spi.LoggerContext`，
        // 必须用 `getLogger` 取真正的 Logger。
        if (LogManager.exists("OpenComputers") != null) LogManager.getLogger("OpenComputers")
        else LogManager.getLogger("OpenComputers")
    }
  }

  /** 反射拿到的 logger 是否可用（调试用）。 */
  def isReflective: Boolean = {
    try {
      val clazz = Class.forName("li.cil.oc.OpenComputers$")
      Modifier.isStatic(clazz.getField("MODULE$").getModifiers)
    }
    catch {
      case _: Throwable => false
    }
  }
}
