package li.cil.oc.common

import li.cil.oc.OpenComputers

import java.lang.reflect.{Method, Modifier}

/**
 * 静态方法调用帮助（原 1.7.10 里放在 `common.IMC` 中）。
 *
 * 1.21.1 的跨模组回调改成走 NeoForge 的 `InterModComms`，但「按全限定名解析一个静态方法再调用」
 * 这套机制本身与平台无关，所以拆出来单独放，供 IMC 与其它模块复用。
 */
object Reflection {

  /** 解析形如 `com.example.Foo.bar` 的静态方法；签名不匹配或不是静态方法时抛异常。 */
  def getStaticMethod(name: String, signature: Class[_]*): Method = {
    val nameSplit = name.lastIndexOf('.')
    if (nameSplit < 0) throw new IllegalArgumentException(s"Invalid method name: $name")
    val className = name.substring(0, nameSplit)
    val methodName = name.substring(nameSplit + 1)
    val clazz = Class.forName(className)
    val method = clazz.getDeclaredMethod(methodName, signature.toSeq: _*)
    if (!Modifier.isStatic(method.getModifiers)) throw new IllegalArgumentException(s"Method $name is not static.")
    method
  }

  /** 调用静态方法并返回结果；失败时记日志并返回 `default`。 */
  def tryInvokeStatic[T](method: Method, args: AnyRef*)(default: T): T = try {
    method.invoke(null, args.toSeq: _*).asInstanceOf[T]
  }
  catch {
    case t: Throwable =>
      OpenComputers.log.warn(s"Error invoking callback ${method.getDeclaringClass.getCanonicalName + "." + method.getName}.", t)
      default
  }

  /** 调用无返回值的静态方法；失败时记日志。 */
  def tryInvokeStaticVoid(method: Method, args: AnyRef*): Unit = try {
    method.invoke(null, args.toSeq: _*)
  }
  catch {
    case t: Throwable =>
      OpenComputers.log.warn(s"Error invoking callback ${method.getDeclaringClass.getCanonicalName + "." + method.getName}.", t)
  }
}
