package li.cil.oc.server.machine

import li.cil.oc.api.machine.Callback

import java.lang.annotation.Annotation
import java.lang.reflect.Proxy

/**
 * 外围设备回调（`ManagedPeripheral#methods`）用的伪注解实例。
 *
 * 上游是一个 Java 类 `PeripheralAnnotation.java`，由于它位于 `src/main/scala`
 * 目录下、而本工程的 Scala 与 Java 是两个独立的 source set，`compileJava`
 * 不会编译它，`compileScala` 也不会（scalac 不处理 `.java`）。
 *
 * 这里用 JDK 动态代理构造一个 `machine.Callback` 的运行时实现，语义与上游
 * 完全一致（`direct = true`、`limit = 100`，其余取注解默认值），且不依赖
 * 构建脚本改动。
 *
 * TODO(1.21.1): 如果后续把 `src/main/scala` 里的 Java 文件挪到 `src/main/java`，
 * 可以直接改用原始那份 `PeripheralAnnotation.java`。
 */
private[machine] object PeripheralAnnotation {

  /** 构造一个名为 `name` 的伪 `Callback` 注解实例。 */
  def apply(name: String): Callback = {
    val handler = new java.lang.reflect.InvocationHandler {
      override def invoke(proxy: AnyRef, method: java.lang.reflect.Method, args: Array[AnyRef]): AnyRef =
        method.getName match {
          case "value" => name
          case "direct" => java.lang.Boolean.TRUE
          case "limit" => Integer.valueOf(100)
          case "doc" => ""
          case "getter" => java.lang.Boolean.FALSE
          case "setter" => java.lang.Boolean.FALSE
          case "annotationType" => classOf[Callback]
          case "toString" => s"@${classOf[Callback].getName}(value=$name, direct=true, limit=100)"
          case "hashCode" => Integer.valueOf(name.hashCode)
          case "equals" => java.lang.Boolean.valueOf(args != null && args.length == 1 && (args(0) eq proxy))
          case other =>
            throw new UnsupportedOperationException(s"Unsupported annotation member: $other")
        }
    }
    Proxy.newProxyInstance(
      classOf[Callback].getClassLoader,
      Array[Class[_]](classOf[Callback], classOf[Annotation]),
      handler).asInstanceOf[Callback]
  }
}
