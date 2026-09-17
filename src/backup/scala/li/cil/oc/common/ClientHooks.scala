package li.cil.oc.common

import li.cil.oc.OpenComputers
import net.neoforged.fml.loading.FMLEnvironment

/**
 * 「客户端监听器注册器」的契约。
 *
 * 实现必须放在 `li.cil.oc.client` 包里（见 [[ClientHooks]] 的说明）。
 */
trait ClientListenerRegistrar {
  /** 注册全部客户端专属监听器；重复调用应当是安全的。 */
  def initialize(): Unit
}

/**
 * `common` → `client` 的**软引用**桥。
 *
 * ==为什么需要它==
 * `li.cil.oc.common` 下有几个处理器天然要注册「客户端专属」的监听器
 * （客户端 tick / 登录清理、纳米机器 HUD、机架覆盖层渲染、客户端组件跟踪表）。
 * 但工程是**按包增量编译**的（`gradle.properties` 的 `scala_ported_packages`）：
 * 只要 `common` 里出现一处 `li.cil.oc.client.Xxx` 的编译期引用，`client` 包就会被
 * 迫进入编译集 —— 而 `client` 目前还有大量文件未收敛。因此这里不写死类引用，
 * 改成按**字符串类名**在运行期解析：
 *
 *  - 编译期：`common` 完全不引用 `client` 包，`client` 不必进编译集；
 *  - 运行期：只有 `FMLEnvironment.dist.isClient`（物理客户端）时才会去加载
 *    `li.cil.oc.client.ClientListeners`，专用服务端上连 `Class.forName` 都不会执行，
 *    因此不会出现「服务端加载客户端类」的 `NoClassDefFoundError`。
 *
 * 这与原代码「`if (FMLEnvironment.dist.isClient) Client.initialize()`」的运行期语义完全一致，
 * 只是把「编译期硬引用」换成了「运行期软引用」。
 *
 * ==如何接线==
 * 实现方（`li.cil.oc.client.ClientListeners`）继承 [[ClientListenerRegistrar]]，
 * 本对象无需任何额外配置；调用点是
 * [[li.cil.oc.common.event.EventHandlers.initialize]]（mod 初始化时一次）。
 */
object ClientHooks {
  /**
   * 客户端注册器的**类名**（不是 `Class` 对象 —— 那会造成编译期依赖）。
   *
   * Scala 的 `object` 会编译成 `Xxx$`，因此取模块实例时要拼上 `$` 并从 `MODULE$` 字段读。
   */
  final val RegistrarClassName = "li.cil.oc.client.ClientListeners"

  private var initialized = false

  /**
   * 在物理客户端上初始化客户端监听器；服务端上是空操作，失败只记日志、不抛异常
   * （客户端监听器缺失只影响表现，不该把启动流程带崩）。
   */
  def initializeClientListeners(): Unit = {
    if (initialized) return
    if (!FMLEnvironment.dist.isClient) return
    initialized = true

    try {
      val module = Class.forName(RegistrarClassName + "$")
      module.getField("MODULE$").get(null) match {
        case registrar: ClientListenerRegistrar =>
          registrar.initialize()
          OpenComputers.log.debug("Initialized client-side event listeners via {}.", RegistrarClassName)
        case other =>
          OpenComputers.log.error("{} does not implement ClientListenerRegistrar (got {}).",
            RegistrarClassName, if (other == null) "null" else other.getClass.getName)
      }
    }
    catch {
      case t: Throwable =>
        OpenComputers.log.error("Failed to initialize client-side event listeners via " +
          RegistrarClassName + "; client-only features (nanomachine HUD, rack overlays, " +
          "pet renderer, client component tracker) are unavailable.", t)
    }
  }
}
