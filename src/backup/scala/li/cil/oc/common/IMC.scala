package li.cil.oc.common

import java.lang.reflect.Method

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.template.AssemblerTemplates
import li.cil.oc.common.template.DisassemblerTemplates
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.{CompoundTag, StringTag, Tag}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.InterModComms
import net.neoforged.fml.InterModComms.IMCMessage

import scala.jdk.CollectionConverters._

/**
 * 跨模组消息（IMC）的接收侧。
 *
 * 1.21.1 迁移要点：
 *  - 旧版由 Forge 在 `IMCEvent` 里把消息推给 mod；NeoForge 1.21.1 已没有 IMC 事件，
 *    改为**主动拉取**：[[processMessages]] 用
 *    [[net.neoforged.fml.InterModComms#getMessages(String)]] 取出所有发给本 mod 的消息再分发。
 *    主类应在 `FMLCommonSetupEvent` 的 `enqueueWork` 里调用一次（发送侧见
 *    `li.cil.oc.api.IMC`，目标 mod id 为 `opencomputers_neo`）；
 *  - `IMCMessage` 的取值方式改为 `getSenderModId()` / `getMethod()` / `getMessageSupplier()`，
 *    负载可能是 `String` 也可能是 `CompoundTag`；
 *  - `CompoundTag#getInteger` → `getInt`，`ItemStack.loadItemStackFromNBT` → `ItemStack.parseOptional`；
 *  - **尚未纳入编译集的包的接线方式**：`li.cil.oc.integration.util.{Wrench,ItemCharge}`、
 *    `li.cil.oc.server.driver.Registry`、`li.cil.oc.server.machine.ProgramLocations` 目前不在
 *    `scala_ported_packages` 里，common 层不能对它们产生编译期依赖，因此这几处仍沿用本文件既有的
 *    「按全限定名反射」风格做**延迟绑定**（见 [[dispatchByName]]）。
 *    TODO(integration/server): 这些包移植完成后，请把下面的字符串查找换成直接调用，
 *    以免拼写错误只能在运行期暴露。
 */
object IMC {
  /**
   * 取出并处理所有发给 OpenComputers 的跨模组消息。
   *
   * 由主类在 `FMLCommonSetupEvent` 里调用（NeoForge 1.21.1 不再提供 IMC 事件）。
   */
  def processMessages(): Unit = {
    InterModComms.getMessages(OpenComputers.ID).iterator().asScala.foreach(handle)
  }

  /** 兼容 1.7.10 的入口名（原为 `handleEvent(e: IMCEvent)`）。 */
  def handleEvent(): Unit = processMessages()

  private def handle(message: IMCMessage): Unit = {
    val sender = message.getSenderModId
    val key = message.getMethod
    val value: AnyRef = try message.getMessageSupplier[AnyRef]().get() catch {
      case t: Throwable =>
        OpenComputers.log.warn(s"Failed getting IMC payload for message '$key' from mod $sender.", t)
        null
    }
    val nbtValue = value match {
      case tag: CompoundTag => tag
      case _ => null
    }
    val stringValue = value match {
      case s: String => s
      case _ => null
    }

    if (key == "registerAssemblerTemplate" && nbtValue != null) {
      if (nbtValue.contains("name", Tag.TAG_STRING))
        OpenComputers.log.debug(s"Registering new assembler template '${nbtValue.getString("name")}' from mod $sender.")
      else
        OpenComputers.log.debug(s"Registering new, unnamed assembler template from mod $sender.")
      try AssemblerTemplates.add(nbtValue) catch {
        case t: Throwable => OpenComputers.log.warn("Failed registering assembler template.", t)
      }
    }
    else if (key == "registerDisassemblerTemplate" && nbtValue != null) {
      if (nbtValue.contains("name", Tag.TAG_STRING))
        OpenComputers.log.debug(s"Registering new disassembler template '${nbtValue.getString("name")}' from mod $sender.")
      else
        OpenComputers.log.debug(s"Registering new, unnamed disassembler template from mod $sender.")
      try DisassemblerTemplates.add(nbtValue) catch {
        case t: Throwable => OpenComputers.log.warn("Failed registering disassembler template.", t)
      }
    }
    else if (key == "registerToolDurabilityProvider" && stringValue != null) {
      OpenComputers.log.debug(s"Registering new tool durability provider '$stringValue' from mod $sender.")
      try ToolDurabilityProviders.add(Reflection.getStaticMethod(stringValue, classOf[ItemStack])) catch {
        case t: Throwable => OpenComputers.log.warn("Failed registering tool durability provider.", t)
      }
    }
    else if (key == "registerWrenchTool" && stringValue != null) {
      OpenComputers.log.debug(s"Registering new wrench tool usage '$stringValue' from mod $sender.")
      try dispatchByName("li.cil.oc.integration.util.Wrench", "addUsage", Seq(classOf[Method]),
        Seq(Reflection.getStaticMethod(stringValue, classOf[Player], classOf[Int], classOf[Int], classOf[Int], classOf[Boolean]))) catch {
        case t: Throwable => OpenComputers.log.warn("Failed registering wrench usage.", t)
      }
    }
    else if (key == "registerWrenchToolCheck" && stringValue != null) {
      OpenComputers.log.debug(s"Registering new wrench tool check '$stringValue' from mod $sender.")
      try dispatchByName("li.cil.oc.integration.util.Wrench", "addCheck", Seq(classOf[Method]),
        Seq(Reflection.getStaticMethod(stringValue, classOf[ItemStack]))) catch {
        case t: Throwable => OpenComputers.log.warn("Failed registering wrench check.", t)
      }
    }
    else if (key == "registerItemCharge" && nbtValue != null) {
      OpenComputers.log.debug(s"Registering new item charge implementation '${nbtValue.getString("name")}' from mod $sender.")
      try dispatchByName("li.cil.oc.integration.util.ItemCharge", "add", Seq(classOf[Method], classOf[Method]),
        Seq(
          Reflection.getStaticMethod(nbtValue.getString("canCharge"), classOf[ItemStack]),
          Reflection.getStaticMethod(nbtValue.getString("charge"), classOf[ItemStack], classOf[Double], classOf[Boolean]))) catch {
        case t: Throwable => OpenComputers.log.warn("Failed registering item charge implementation.", t)
      }
    }
    else if (key == "blacklistPeripheral" && stringValue != null) {
      OpenComputers.log.debug(s"Blacklisting CC peripheral '$stringValue' as requested by mod $sender.")
      try {
        // TODO(Settings): `Config#getStringList` 返回的是不可变列表，这里的 `add` 在 1.21.1 的
        // typesafe config 上会抛 UnsupportedOperationException；等 Settings 的持有者把它换成
        // 可变副本（或单独的 mutable 黑名单集合）后即可正常生效。
        val blacklist = Settings.get.peripheralBlacklist
        if (!blacklist.contains(stringValue)) {
          blacklist.add(stringValue)
        }
      }
      catch {
        case t: Throwable => OpenComputers.log.debug(s"Could not add '$stringValue' to the peripheral blacklist.", t)
      }
    }
    else if (key == "blacklistHost" && nbtValue != null) {
      OpenComputers.log.debug(s"Blacklisting component '${nbtValue.getString("name")}' for host '${nbtValue.getString("host")}' as requested by mod $sender.")
      try {
        val stack = ItemStack.parseOptional(fallbackRegistry, nbtValue.getCompound("item"))
        if (stack == null || stack.isEmpty) {
          OpenComputers.log.warn(s"Failed blacklisting component: message from mod $sender carried no item stack.")
        }
        else {
          dispatchByName("li.cil.oc.server.driver.Registry", "blacklistHost", Seq(classOf[ItemStack], classOf[Class[_]]),
            Seq(stack, Class.forName(nbtValue.getString("host"))))
        }
      }
      catch {
        case t: Throwable => OpenComputers.log.warn("Failed blacklisting component.", t)
      }
    }
    else if (key == "registerAssemblerFilter" && stringValue != null) {
      OpenComputers.log.debug(s"Registering new assembler template filter '$stringValue' from mod $sender.")
      try AssemblerTemplates.addFilter(stringValue) catch {
        case t: Throwable => OpenComputers.log.warn("Failed registering assembler template filter.", t)
      }
    }
    else if (key == "registerInkProvider" && stringValue != null) {
      OpenComputers.log.debug(s"Registering new ink provider '$stringValue' from mod $sender.")
      try PrintData.addInkProvider(Reflection.getStaticMethod(stringValue, classOf[ItemStack])) catch {
        case t: Throwable => OpenComputers.log.warn("Failed registering ink provider.", t)
      }
    }
    else if (key == "registerProgramDiskLabel" && nbtValue != null) {
      OpenComputers.log.debug(s"Registering new program location mapping for program '${nbtValue.getString("program")}' being on disk '${nbtValue.getString("label")}' from mod $sender.")
      try {
        val architectures = nbtValue.getList("architectures", Tag.TAG_STRING).map((tag: StringTag) => tag.getAsString).toSeq
        dispatchByName("li.cil.oc.server.machine.ProgramLocations", "addMapping",
          Seq(classOf[String], classOf[String], classOf[scala.collection.immutable.Seq[_]]),
          Seq(nbtValue.getString("program"), nbtValue.getString("label"), architectures))
      }
      catch {
        case t: Throwable => OpenComputers.log.warn("Failed registering program disk label.", t)
      }
    }
    else if (key == "registerCustomPowerSystem") {
      OpenComputers.log.debug(s"Mod $sender reported a custom power system.")
    }
    else {
      OpenComputers.log.warn(s"Got an unrecognized or invalid IMC message '$key' from mod $sender.")
    }
  }

  /**
   * 按全限定名调用一个静态方法（延迟绑定，避免对尚未移植的包产生编译期依赖）。
   *
   * @param className 目标类名（Scala `object` 的静态转发方法同样适用）
   * @param methodName 方法名
   * @param signature 参数类型（与 Java 反射一致，`Int`/`Boolean` 用 `classOf[Int]`/`classOf[Boolean]`）
   * @param args 实参
   */
  private def dispatchByName(className: String, methodName: String, signature: Seq[Class[_]], args: Seq[AnyRef]): Unit = {
    val method = Reflection.getStaticMethod(className + "." + methodName, signature: _*)
    Reflection.tryInvokeStaticVoid(method, args: _*)
  }

  // ----------------------------------------------------------------------- //
  // 兼容层：1.7.10 里这些帮助方法定义在 IMC 中，现已移到
  // `li.cil.oc.common.Reflection`；`integration.util.{Wrench,ItemCharge}` 等
  // 已移植代码仍在调用旧名字，故保留转发。
  // ----------------------------------------------------------------------- //

  def getStaticMethod(name: String, signature: Class[_]*): Method =
    Reflection.getStaticMethod(name, signature: _*)

  def tryInvokeStatic[T](method: Method, args: AnyRef*)(default: T): T =
    Reflection.tryInvokeStatic(method, args: _*)(default)

  def tryInvokeStaticVoid(method: Method, args: AnyRef*): Unit =
    Reflection.tryInvokeStaticVoid(method, args: _*)
}
