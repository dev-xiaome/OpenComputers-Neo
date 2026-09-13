package li.cil.oc.util

/**
 * 把 Scala 的数值包装类型（`RichInt` 等，经由 `ScalaNumber`）拆箱成 Java 包装类型，
 * 以便结果能直接交给 Java 侧的 `Object[]` 使用。
 */
object ResultWrapper {
  def result(args: Any*): Array[AnyRef] = {
    def unwrap(arg: Any): AnyRef = arg match {
      case x: ScalaNumber => x.underlying
      case x => x.asInstanceOf[AnyRef]
    }
    args.map(unwrap).toArray
  }
}
