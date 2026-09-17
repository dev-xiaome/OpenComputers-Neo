package li.cil.oc.server.machine

import li.cil.oc.api.machine.Callback

import java.lang.annotation.Annotation

/**
 * 外围设备回调（`ManagedPeripheral#methods`）用的伪注解实例。
 *
 * OCCE 上游是一份 Java 类 `PeripheralAnnotation.java`，它**放在
 * `src/main/scala/li/cil/oc/server/machine/` 目录下**（CE 的构建把 Java 源目录也指向了
 * `src/main/scala`，所以 scalac/compileJava 都能看到它）。本项目的 build.gradle 只把
 * `src/main/scala` 交给 `compileScala`、`src/main/java` 交给 `compileJava`，那份 Java
 * 文件放进来不会被编译，因此这里用**真实的 Scala 类**实现同一个注解接口。
 *
 * 与上游逐字一致的取值：`value = name`、`direct = true`、`limit = 100`、`doc = ""`、
 * `getter / setter = false`、`annotationType = Callback`。
 *
 * 这里刻意**不用** `java.lang.reflect.Proxy`：注解接口的实现类必须是真实类，
 * 动态代理在按接口身份做 `isInstance` / `annotationType` 判断的调用路径上并不可靠，
 * 而且它需要在整个注册表层面被当作注解实例使用。
 *
 * `Callbacks.scala`（主线负责）通过 `PeripheralAnnotation(name)` 取实例，因此保留
 * `apply` 工厂方法。
 *
 * TODO(porting): 如果主线愿意把 `src/main/scala` 追加进
 * `sourceSets.main.java.srcDirs`，可以直接换回上游那份 `PeripheralAnnotation.java`，
 * 并把 `Callbacks.scala` 的调用改成 `new PeripheralAnnotation(name)`。
 */
private[machine] object PeripheralAnnotation {

  /** 构造一个名为 `name` 的伪 `Callback` 注解实例。 */
  def apply(name: String): Callback = new Impl(name)

  private final class Impl(val name: String) extends Callback {
    override def value(): String = name

    override def direct(): Boolean = true

    override def limit(): Int = 100

    override def doc(): String = ""

    override def getter(): Boolean = false

    override def setter(): Boolean = false

    override def annotationType(): Class[_ <: Annotation] = classOf[Callback]
  }
}
