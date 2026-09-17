package li.cil.oc.server.machine.luaj

import li.cil.oc.OpenComputers
import li.cil.oc.api.machine.Value
import li.cil.oc.server.machine.ArgumentsImpl
// 与 CE-1.20 一致：值转换走唯一的 server/driver/Registry 实现
// （本包内曾有一份过时的本地替身 server/machine/Registry.scala，已删除）。
import li.cil.oc.server.driver.Registry
import li.cil.oc.util.ScalaClosure._
import li.cil.repack.org.luaj.vm2.LuaValue
import li.cil.repack.org.luaj.vm2.Varargs

import scala.jdk.CollectionConverters._

class UserdataAPI(owner: LuaJLuaArchitecture) extends LuaJAPI(owner) {
  override def initialize(): Unit = {
    val userdata = LuaValue.tableOf()

    userdata.set("apply", (args: Varargs) => {
      val value = args.checkuserdata(1, classOf[Value]).asInstanceOf[Value]
      val params = toSimpleJavaObjects(args, 2)
      // 与 CE-1.20 一致：走 `li.cil.oc.server.driver.Registry.convert`。
      owner.invoke(() => Registry.convert(Array(value.apply(machine, new ArgumentsImpl(params)))))
    })

    userdata.set("unapply", (args: Varargs) => {
      val value = args.checkuserdata(1, classOf[Value]).asInstanceOf[Value]
      val params = toSimpleJavaObjects(args, 2)
      owner.invoke(() => {
        value.unapply(machine, new ArgumentsImpl(params))
        null
      })
    })

    userdata.set("call", (args: Varargs) => {
      val value = args.checkuserdata(1, classOf[Value]).asInstanceOf[Value]
      val params = toSimpleJavaObjects(args, 2)
      owner.invoke(() => Registry.convert(value.call(machine, new ArgumentsImpl(params))))
    })

    userdata.set("dispose", (args: Varargs) => {
      val value = args.checkuserdata(1, classOf[Value]).asInstanceOf[Value]
      try value.dispose(machine) catch {
        case t: Throwable => OpenComputers.log.warn("Error in dispose method of userdata of type " + value.getClass.getName, t)
      }
      LuaValue.NIL
    })

    userdata.set("methods", (args: Varargs) => {
      val value = args.checkuserdata(1, classOf[Value])
      // Scala 2.13：`machine.methods` 返回 java.util.Map，需要显式 asScala，
      // 且 `++`/`flatten` 的组合要显式给元素类型。
      LuaValue.tableOf(machine.methods(value).asScala.flatMap(entry => {
        val (name, annotation) = entry
        Seq(LuaValue.valueOf(name), LuaValue.valueOf(annotation.direct))
      }.toSeq).toArray)
    })

    userdata.set("invoke", (args: Varargs) => {
      val value = args.checkuserdata(1, classOf[Value]).asInstanceOf[Value]
      val method = args.checkjstring(2)
      val params = toSimpleJavaObjects(args, 3)
      owner.invoke(() => machine.invoke(value, method, params.toArray))
    })

    userdata.set("doc", (args: Varargs) => {
      val value = args.checkuserdata(1, classOf[Value]).asInstanceOf[Value]
      val method = args.checkjstring(2)
      // Scala 2.13：java.util.Map 没有 apply，改为显式 get。
      val methods = machine.methods(value)
      owner.documentation(() => Option(methods.get(method)).map(_.doc).orNull)
    })

    lua.set("userdata", userdata)
  }
}
