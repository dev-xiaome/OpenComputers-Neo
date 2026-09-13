package li.cil.oc.util

import scala.annotation.tailrec

/**
 * 基于 Viktor Klang 的 Scala 枚举实现
 * （https://gist.github.com/viktorklang/1057513）。
 *
 * 2.13 迁移：`Vector` / `AtomicReference` 语义未变，仅在 `equals` 中做了空值保护。
 */
trait ScalaEnum {
  import java.util.concurrent.atomic.AtomicReference // Concurrency paranoia

  type EnumVal <: Value // This is a type that needs to be found in the implementing class

  private val _values = new AtomicReference(Vector[EnumVal]()) // Stores our enum values

  // Adds an EnumVal to our storage, uses CCAS to make sure it's thread safe, returns the ordinal
  @tailrec private final def addEnumVal(newVal: EnumVal): Int = {
    import _values.{get, compareAndSet => CAS}
    val oldVec = get
    val newVec = oldVec :+ newVal
    if ((get eq oldVec) && CAS(oldVec, newVec))
      newVec.indexWhere(_ eq newVal)
    else
      addEnumVal(newVal)
  }

  def values: Vector[EnumVal] = _values.get // Here you can get all the enums that exist for this type

  // This is the trait that we need to extend our EnumVal type with, it does the book-keeping for us
  protected trait Value { self: EnumVal => // Enforce that no one mixes in Value in a non-EnumVal type
    final val ordinal = addEnumVal(this) // Adds the EnumVal and returns the ordinal

    def name: String // All enum values should have a name

    override def toString: String = name // And that name is used for the toString operation

    override def equals(other: Any): Boolean = other match {
      case ref: AnyRef => this eq ref
      case _ => false
    }

    override def hashCode: Int = 31 * (this.getClass.## + name.## + ordinal)
  }

}
