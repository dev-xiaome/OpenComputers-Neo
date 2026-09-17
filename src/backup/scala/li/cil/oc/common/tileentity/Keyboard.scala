package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.{Analyzable, Message, Node, SidedEnvironment, Visibility}
import li.cil.oc.api.prefab
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState

import scala.collection.mutable

/**
 * 键盘（对应 1.7.10 的 `common.tileentity.Keyboard`）。
 *
 * 键盘本身只是「贴在某台设备旁边的输入设备」：它把玩家按键翻译成
 * `computer.checked_signal("key_down"/"key_up"/"clipboard", ...)` 发给所连接的网络，
 * 并且只在「贴在屏幕上的那一面」提供节点（见 [[hasNodeOnSide]]）。
 *
 * 纹理：所有面 = Keyboard（朝向由 `pitch` / `yaw` 决定，贴在墙上时键帽朝外）。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `ForgeDirection` → `Direction`（`ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`）。
 *  - `player.getDistanceSq(x, y, z)` → `player.distanceToSqr(x, y, z)`；
 *    `player.getCommandSenderName` → `player.getGameProfile.getName`。
 *  - 删除 `@SideOnly(Side.CLIENT)`；[[canConnect]] 本来就只在客户端调用。
 *  - `readFromNBTForServer` / `writeToNBTForServer` 在 1.21.1 是 `protected` 钩子。
 *
 * ==降级说明==
 *  - TODO(integration.opencomputers.DriverKeyboard / server.component): 原实现用
 *    `api.Driver.driverFor(keyboardItem, getClass).createEnvironment(keyboardItem, this)`
 *    创建键盘组件。键盘物品驱动与 `li.cil.oc.server.component.Keyboard` 都尚未移植
 *    （运行期 `driverFor` 返回 null），因此这里改用内置的 [[Keyboard.Placeholder]]：
 *    节点形状（组件名 `keyboard`）与 `node` / `load` / `save` / `setUsableOverride` /
 *    `releasePressedKeys` / `isUseableByPlayer` 等对外名字保持一致，
 *    按键信号格式也照搬原组件，只是设备信息（DeviceInfo）暂缺。
 *  - TODO(client.KeyBindings): 客户端按键采集（`li.cil.oc.client.KeyBindings` 与客户端 GUI
 *    输入层）尚未移植，因此目前没有任何东西会调用 [[keyDown]] / [[keyUp]] /
 *    [[setSpeed]]，这几个入口先作为 API 保留。
 */
class Keyboard(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.Rotatable with traits.ImmibisMicroblock with SidedEnvironment with Analyzable {

  override def validFacings: Array[Direction] = Direction.values()

  val keyboard: api.internal.Keyboard = new Keyboard.Placeholder(this)

  override def node: Node = keyboard.node

  def hasNodeOnSide(side: Direction): Boolean =
    side != facing && (isOnWall || side != forward.getOpposite)

  // ----------------------------------------------------------------------- //

  // 只应在客户端渲染时调用（原 `@SideOnly(Side.CLIENT)`，1.21.1 已删除该注解）。
  override def canConnect(side: Direction): Boolean = hasNodeOnSide(side)

  override def sidedNode(side: Direction): Node = if (hasNodeOnSide(side)) node else null

  // Override automatic analyzer implementation for sided environments.
  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = Array(node)

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = false

  // ----------------------------------------------------------------------- //
  // 客户端输入入口（原客户端输入层直接驱动组件，这里转发给组件占位实现）
  // ----------------------------------------------------------------------- //

  def keyDown(player: Player, character: Char, code: Int): Unit =
    Keyboard.placeholderOf(keyboard).foreach(_.keyDown(player, character, code))

  def keyUp(player: Player, character: Char, code: Int): Unit =
    Keyboard.placeholderOf(keyboard).foreach(_.keyUp(player, character, code))

  def clipboard(player: Player, value: String): Unit =
    Keyboard.placeholderOf(keyboard).foreach(_.clipboard(player, value))

  /**
   * 按键处理速率。
   *
   * TODO(client.KeyBindings): 1.7.10 的键盘组件没有这个成员；移植规范要求保留
   * `setSpeed` 这个名字（客户端按键重复速率的入口），这里转发给组件占位属性，
   * 等客户端输入层移植后接上真实语义（真实组件上用 `placeholderOf` 之外的分支处理）。
   */
  def setSpeed(value: Double): Unit =
    Keyboard.placeholderOf(keyboard).foreach(_.setSpeed(value))

  def getSpeed: Double = Keyboard.placeholderOf(keyboard).fold(0.0)(_.getSpeed)

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (isServer) {
      keyboard.load(nbt.getCompound(Settings.namespace + "keyboard"))
    }
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    if (isServer) {
      // `keyboard.save` 是接口（`Persistable`）上的抽象方法，不能直接当函数值传，这里显式包一层。
      nbt.setNewCompoundTag(Settings.namespace + "keyboard", (tag: CompoundTag) => keyboard.save(tag))
    }
  }

  // ----------------------------------------------------------------------- //

  private def isOnWall: Boolean = facing != Direction.UP && facing != Direction.DOWN

  private def forward: Direction = if (isOnWall) Direction.UP else yaw
}

object Keyboard {

  /** 取出内置的占位键盘组件；接回真实组件后返回 `None`（见 [[Placeholder]] 的说明）。 */
  private[tileentity] def placeholderOf(keyboard: api.internal.Keyboard): Option[Placeholder] = keyboard match {
    case placeholder: Placeholder => Some(placeholder)
    case _ => None
  }

  /**
   * 键盘组件的**最小占位实现**（原 `li.cil.oc.server.component.Keyboard`）。
   *
   * 保留原组件的公开成员名与行为：
   *  - `node`：组件名 `keyboard`、可达性 `Network`；
   *  - `pressedKeys`：按玩家记录「键码 → 字符」，用于在玩家离开 / 目标断开时补发 `key_up`；
   *  - `setUsableOverride` / `isUseableByPlayer`：可用性判定（默认距离平方不超过 64）；
   *  - `onMessage`：处理 `keyboard.keyDown` / `keyboard.keyUp` / `keyboard.clipboard`，
   *    翻译成 `computer.checked_signal("key_down"/"key_up"/"clipboard", ...)`；
   *  - `keyDown` / `keyUp` / `clipboard` / `releasePressedKeys`：同上的直接调用入口；
   *  - `load` / `save`：节点与按键状态随组件标签持久化。
   *
   * TODO(server.component): `li.cil.oc.server.component.Keyboard` 移植完成后删除本类，
   * 并把方块实体的 `keyboard` 改回由物品驱动创建的环境。
   */
  private[tileentity] class Placeholder(val host: api.network.EnvironmentHost)
    extends prefab.ManagedEnvironment with api.internal.Keyboard {

    setNode(api.Network.newNode(this, Visibility.Network).
      withComponent("keyboard").
      create())

    val pressedKeys = mutable.Map.empty[Player, mutable.Map[Integer, Character]]

    var usableOverride: Option[api.internal.Keyboard.UsabilityChecker] = None

    // TODO(client.KeyBindings): 见方块实体上 setSpeed 的说明；原组件没有这个属性。
    private var speed: Double = 1.0

    override def setUsableOverride(callback: api.internal.Keyboard.UsabilityChecker): Unit =
      usableOverride = Option(callback)

    // --------------------------------------------------------------------- //

    /** 释放某个玩家所有按下的键（原 `releasePressedKeys`）。 */
    def releasePressedKeys(player: Player): Unit = {
      pressedKeys.get(player) match {
        case Some(keys) => for ((code, char) <- keys) {
          signalKey(player, "key_up", char, code)
        }
        case _ =>
      }
      pressedKeys.remove(player)
    }

    def keyDown(player: Player, character: Char, code: Int): Unit = {
      if (isUseableByPlayer(player)) {
        val keys = pressedKeys.getOrElseUpdate(player, mutable.Map.empty[Integer, Character])
        keys += Integer.valueOf(code) -> Character.valueOf(character)
        signalKey(player, "key_down", Character.valueOf(character), Integer.valueOf(code))
      }
    }

    def keyUp(player: Player, character: Char, code: Int): Unit = {
      pressedKeys.get(player) match {
        case Some(keys) if keys.contains(Integer.valueOf(code)) =>
          keys -= Integer.valueOf(code)
          signalKey(player, "key_up", Character.valueOf(character), Integer.valueOf(code))
        case _ =>
      }
    }

    def clipboard(player: Player, value: String): Unit = {
      if (isUseableByPlayer(player)) {
        for (line <- value.linesWithSeparators) {
          if (Settings.get.inputUsername) {
            signal(player, "clipboard", line, player.getGameProfile.getName)
          }
          else {
            signal(player, "clipboard", line)
          }
        }
      }
    }

    def setSpeed(value: Double): Unit = speed = value

    def getSpeed: Double = speed

    // --------------------------------------------------------------------- //

    def isUseableByPlayer(player: Player): Boolean = usableOverride match {
      case Some(callback) => callback.isUsableByPlayer(this, player)
      case _ => player.distanceToSqr(host.xPosition(), host.yPosition(), host.zPosition()) <= 64
    }

    // --------------------------------------------------------------------- //

    override def onMessage(message: Message): Unit = {
      super.onMessage(message)
      message.data match {
        case Array(p: Player, char: Character, code: Integer) if message.name == "keyboard.keyDown" =>
          if (isUseableByPlayer(p)) {
            val keys = pressedKeys.getOrElseUpdate(p, mutable.Map.empty[Integer, Character])
            keys += code -> char
            signalKey(p, "key_down", char, code)
          }
        case Array(p: Player, char: Character, code: Integer) if message.name == "keyboard.keyUp" =>
          pressedKeys.get(p) match {
            case Some(keys) if keys.contains(code) =>
              keys -= code
              signalKey(p, "key_up", char, code)
            case _ =>
          }
        case Array(p: Player, value: String) if message.name == "keyboard.clipboard" =>
          clipboard(p, value)
        case _ =>
      }
    }

    // --------------------------------------------------------------------- //

    private def signalKey(player: Player, name: String, character: Character, code: Integer): Unit = {
      if (Settings.get.inputUsername) {
        signal(player, name, character, code, player.getGameProfile.getName)
      }
      else {
        signal(player, name, character, code)
      }
    }

    protected def signal(args: AnyRef*): Unit =
      node.sendToReachable("computer.checked_signal", args: _*)

    // --------------------------------------------------------------------- //

    override def load(nbt: CompoundTag): Unit = {
      super.load(nbt)
      speed = nbt.getDouble("speed")
    }

    override def save(nbt: CompoundTag): Unit = {
      super.save(nbt)
      nbt.putDouble("speed", speed)
    }
  }
}
