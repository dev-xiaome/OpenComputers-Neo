package li.cil.oc.server.machine

/**
 * 公共的反射工具。
 *
 * 移植期间有若干“已知存在但暂时不在编译集里”的类（`li.cil.oc.common.tileentity`
 * 与 `li.cil.oc.server.PacketSender`）。直接 import 会让整个 `server/machine`
 * 无法通过增量编译自检，而这些类一旦补齐又不想再改回来，因此统一走这里的
 * 反射入口：编译期不依赖，运行期按类名解析。
 *
 * 所有降级点都带 `TODO(...)` 注释，方便后续一键回退到直接 import。
 */
private[machine] object ReflectionCompat {

  /** 解析一个类；找不到返回 `null`。 */
  def resolveClass(name: String): Class[_] = try Class.forName(name) catch {
    case _: Throwable => null
  }

  /** 取得单例对象（Scala `object` 的 `MODULE$`）。 */
  def resolveModule(name: String): AnyRef = {
    val clazz = resolveClass(name)
    if (clazz == null) null
    else try clazz.getField("MODULE$").get(null) catch {
      case _: Throwable => null
    }
  }

  /**
   * 在 `instances` 中挑选第一个带有指定方法签名的实例并调用。
   *
   * @param instances 候选调用目标，按顺序尝试（Scala object 优先，其次静态类）
   * @param method    方法名
   * @param argTypes  参数类型
   * @param args      实参
   * @return 调用结果；无可用目标或调用失败时返回 `null`
   */
  def invokeStatic(instances: Seq[AnyRef], method: String, argTypes: Array[Class[_]], args: Array[AnyRef]): AnyRef = {
    for (instance <- instances if instance != null) {
      try {
        val m = instance.getClass.getMethod(method, argTypes: _*)
        return m.invoke(instance, args: _*)
      }
      catch {
        case _: NoSuchMethodException => // 试下一个
        case t: Throwable =>
          MachineLog.log.warn(s"Reflective call to $method failed.", t)
          return null
      }
    }
    null
  }

  /** 类是否已经存在于运行期（用于诊断降级原因）。 */
  def isAvailable(name: String): Boolean = resolveClass(name) != null

  // ---- PacketSender 入口 ----

  private lazy val packetSenderInstances: Seq[AnyRef] = Seq(
    resolveModule("li.cil.oc.server.PacketSender$"),
    resolveClass("li.cil.oc.server.PacketSender"))

  /** 播放一段蜂鸣（对应 `PacketSender.sendSound` 的数值版本）。 */
  def sendSound(world: AnyRef, x: Double, y: Double, z: Double, frequency: Int, duration: Int): Unit = {
    invokeStatic(packetSenderInstances, "sendSound",
      Array(classOf[net.minecraft.world.level.Level], classOf[Double], classOf[Double], classOf[Double], classOf[Int], classOf[Int]),
      Array(world, Double.box(x), Double.box(y), Double.box(z), Integer.valueOf(frequency), Integer.valueOf(duration)))
    ()
  }

  /** 播放一段蜂鸣图案（对应 `PacketSender.sendSound` 的字符串版本）。 */
  def sendSound(world: AnyRef, x: Double, y: Double, z: Double, pattern: String): Unit = {
    invokeStatic(packetSenderInstances, "sendSound",
      Array(classOf[net.minecraft.world.level.Level], classOf[Double], classOf[Double], classOf[Double], classOf[String]),
      Array(world, Double.box(x), Double.box(y), Double.box(z), pattern))
    ()
  }

  /** 推送机器用户列表（对应 `PacketSender.sendComputerUserList`）。 */
  def sendComputerUserList(computer: AnyRef, list: Array[String]): Unit = {
    val computerClass = resolveClass("li.cil.oc.common.tileentity.traits.Computer")
    if (computerClass != null && computerClass.isInstance(computer)) {
      invokeStatic(packetSenderInstances, "sendComputerUserList",
        Array(computerClass, classOf[Array[String]]),
        Array(computer, list))
    }
    ()
  }
}

/**
 * `li.cil.oc.server.fs.FileSystem.asManagedEnvironment` 的反射替身。
 *
 * 上游签名（1.21.1 目标侧）：
 * `asManagedEnvironment(fileSystem, label, host, accessSound, speed)` →
 * 内部构造 `li.cil.oc.server.component.FileSystem(fs, label, host, accessSound, speed)`。
 *
 * TODO(server.component): 等 `server/component` + `common/item` 进入编译集后，
 * 直接把 `Machine.scala` 里的 `ReflectFilesystem.asManagedEnvironment` 换成
 * `li.cil.oc.server.fs.FileSystem.asManagedEnvironment(..., "tmpfs", null, null, 5)`。
 */
private[machine] object ReflectFilesystem {

  private lazy val componentClass: Class[_] =
    ReflectionCompat.resolveClass("li.cil.oc.server.component.FileSystem")

  private lazy val readOnlyLabelClass: Class[_] =
    ReflectionCompat.resolveClass("li.cil.oc.server.fs.FileSystem$ReadOnlyLabel")

  /** 是否具备构造条件（用于诊断）。 */
  def available: Boolean = componentClass != null && readOnlyLabelClass != null

  def asManagedEnvironment(fileSystem: li.cil.oc.api.fs.FileSystem, label: String): Option[li.cil.oc.api.network.ManagedEnvironment] = {
    if (fileSystem == null || componentClass == null || readOnlyLabelClass == null) return None
    val labelObject: AnyRef = try {
      readOnlyLabelClass.getConstructor(classOf[String]).newInstance(label).asInstanceOf[AnyRef]
    }
    catch {
      case t: Throwable =>
        MachineLog.log.warn("Failed creating a file system label.", t)
        return None
    }
    try {
      // 第二个参数必须是**声明类型** `api.fs.Label`，不能写 `ReadOnlyLabel`：
      // `Class#getConstructor` 要求精确匹配，而构造器的形参类型是 `Label`
      // （`ReadOnlyLabel` 只是它的一个实例类型），写窄了会 `NoSuchMethodException`，
      // 结果是 `/tmp` 的 tmpfs 静默不可用。
      val ctor = componentClass.getConstructor(
        classOf[li.cil.oc.api.fs.FileSystem],
        classOf[li.cil.oc.api.fs.Label],
        classOf[Option[_]],
        classOf[Option[_]],
        classOf[Int])
      Option(ctor.newInstance(
        fileSystem,
        labelObject,
        None,
        None,
        Integer.valueOf(5)).asInstanceOf[li.cil.oc.api.network.ManagedEnvironment])
    }
    catch {
      case t: Throwable =>
        MachineLog.log.warn("Failed creating the tmpfs environment; tmpfs will be unavailable.", t)
        None
    }
  }
}
