package li.cil.oc.server.component

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.api.prefab.AbstractValue
import li.cil.oc.common.item.data.DebugCardData
import li.cil.oc.server.PacketSender
import li.cil.oc.server.network.DebugNetwork
import li.cil.oc.server.network.DebugNetwork.DebugNode
import li.cil.oc.server.component.DebugCard.{CommandSender, serverOf}
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedBlock._
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ExtendedWorld._
import li.cil.oc.util.FluidUtils
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.{BuiltInRegistries, Registries}
import net.minecraft.commands.{CommandResultCallback, CommandSourceStack}
import net.minecraft.nbt.{CompoundTag, Tag, TagParser}
import net.minecraft.network.chat.Component
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.{ServerLevel, ServerPlayer}
import net.minecraft.sounds.{SoundEvent, SoundSource}
import net.minecraft.world.entity.{Entity, LivingEntity}
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.item.{Item, ItemStack, Items}
import net.minecraft.world.level.{GameType, Level}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.storage.ServerLevelData
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.{FakePlayer, FakePlayerFactory}
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.server.ServerLifecycleHooks

import scala.jdk.CollectionConverters._
import scala.collection.mutable

class DebugCard(host: EnvironmentHost) extends prefab.ManagedEnvironment with DebugNode {
  override val node = Network.newNode(this, Visibility.Neighbors).
    withComponent("debug").
    withConnector().
    create()

  // Used to detect disconnects.
  private var remoteNode: Option[Node] = None

  // Used for delayed connecting to remote node again after loading.
  private var remoteNodePosition: Option[(Int, Int, Int)] = None

  // Player this card is bound to (if any) to use for permissions.
  // 1.21.1：`AccessContext` 已并入 `li.cil.oc.Settings`（`common/item/data/DebugCardData`
  // 同样直接使用它，见那里的说明），这里不再自定义一份，避免出现两个互不兼容的同名类型。
  implicit var access: Option[Settings.AccessContext] = None

  def player = access.map(_.player)

  private lazy val CommandSender = {
    // 1.21.1 的 `FakePlayerFactory` 与 1.7.10 一样只接受 `ServerLevel`；调试卡只在服务端存在。
    def defaultFakePlayer = FakePlayerFactory.get(host.world.asInstanceOf[ServerLevel], Settings.get.fakePlayerProfile)
    new CommandSender(host, player match {
      // 1.7.10 的 `MinecraftServer.getServer.getConfigurationManager.func_152612_a` →
      // 1.21.1 的 `MinecraftServer#getPlayerList#getPlayerByName`。
      case Some(name) => serverOf(host.world).map(_.getPlayerList.getPlayerByName(name)).orNull match {
        case playerEntity: ServerPlayer => playerEntity
        case _ => defaultFakePlayer
      }
      case _ => defaultFakePlayer
    })
  }

  // ----------------------------------------------------------------------- //

  import li.cil.oc.server.component.DebugCard.checkAccess

  @Callback(doc = """function(value:number):number -- Changes the component network's energy buffer by the specified delta.""")
  def changeBuffer(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    result(node.changeBuffer(args.checkDouble(0)))
  }

  @Callback(doc = """function():number -- Get the container's X position in the world.""")
  def getX(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    result(host.xPosition)
  }

  @Callback(doc = """function():number -- Get the container's Y position in the world.""")
  def getY(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    result(host.yPosition)
  }

  @Callback(doc = """function():number -- Get the container's Z position in the world.""")
  def getZ(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    result(host.zPosition)
  }

  @Callback(doc = """function([id:number]):userdata -- Get the world object for the specified dimension ID, or the container's.""")
  def getWorld(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    if (args.count() > 0) {
      // 1.7.10 的 `DimensionManager.getWorld(id)` → 见 DebugCard.serverLevel（1.21.1 无数字维度 ID）。
      DebugCard.serverLevel(serverOf(host.world), args.checkInteger(0)) match {
        case Some(level) => result(new DebugCard.WorldValue(level))
        case _ => result(Unit, "no such dimension")
      }
    }
    else result(new DebugCard.WorldValue(host.world))
  }

  @Callback(doc = """function():table -- Get a list of all world IDs, loaded and unloaded.""")
  def getWorlds(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    // 1.7.10 返回数字维度 ID 列表；1.21.1 已没有维度 ID，改为返回维度 key
    // （如 `minecraft:overworld`），可再传给 getWorld。
    serverOf(host.world) match {
      case Some(server) => result(server.getAllLevels.asScala.map(level => level.dimension().location().toString).toArray)
      case _ => result(Array.empty[AnyRef])
    }
  }

  @Callback(doc = """function(name:string):userdata -- Get the entity of a player.""")
  def getPlayer(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    result(new DebugCard.PlayerValue(args.checkString(0)))
  }

  @Callback(doc = """function():table -- Get a list of currently logged-in players.""")
  def getPlayers(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    // 1.7.10 的 `getAllUsernames` → 1.21.1 的 `getPlayerNames`。
    result(serverOf(host.world).map(_.getPlayerNames).getOrElse(Array.empty[String]))
  }


  @Callback(doc = "function(x: number, y: number, z: number[, worldId: number]):boolean, string, table -- returns contents at the location in world by id (default host world)")
  def scanContentsAt(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    val x = args.checkInteger(0)
    val y = args.checkInteger(1)
    val z = args.checkInteger(2)
    val worldServer = if (args.count() > 3) DebugCard.serverLevel(serverOf(host.world), args.checkInteger(3)).orNull else host.world
    val world = worldServer match {
      case level: Level => level
      case _ => return result(Unit, "no such dimension")
    }

    val position: BlockPosition = new BlockPosition(x, y, z, Option(world))
    val fakePlayer = FakePlayerFactory.get(world.asInstanceOf[ServerLevel], Settings.get.fakePlayerProfile)
    // 1.7.10 直接写 `posX/posY/posZ` 字段；1.21.1 用 `setPos`。
    fakePlayer.setPos(position.x + 0.5, position.y + 0.5, position.z + 0.5)

    // 1.21.1 用 `getEntitiesOfClass` 取代了 `findNearestEntityWithinAABB`，取包围盒内第一个实体。
    world.getEntitiesOfClass(classOf[Entity], position.bounds).asScala.headOption match {
      case Some(living: LivingEntity) => result(true, "LivingEntity", living)
      case Some(minecart: AbstractMinecart) => result(true, "AbstractMinecart", minecart)
      case _ =>
        val state = world.getBlockState(position.toChunkCoordinates)
        val block = state.getBlock
        if (state.isAir) {
          result(false, "air", block)
        }
        else if (FluidUtils.lookupFluidStateForBlock(world, position) != null) {
          // 1.7.10 的 `BlockEvent.BreakEvent(x, y, z, world, block, meta, player)` →
          // 1.21.1 的 `(level, pos, state, player)`；`isCanceled` 取代旧的 `Event.Result`。
          val event = new BlockEvent.BreakEvent(world, position.toChunkCoordinates, state, fakePlayer)
          NeoForge.EVENT_BUS.post(event)
          result(event.isCanceled, "liquid", block)
        }
        else if (state.canBeReplaced) {
          val event = new BlockEvent.BreakEvent(world, position.toChunkCoordinates, state, fakePlayer)
          NeoForge.EVENT_BUS.post(event)
          result(event.isCanceled, "replaceable", block)
        }
        else if (state.getCollisionShape(world, position.toChunkCoordinates).isEmpty) {
          result(true, "passable", block)
        }
        else {
          result(true, "solid", block)
        }
    }
  }

  @Callback(doc = """function(name:string):boolean -- Get whether a mod or API is loaded.""")
  def isModLoaded(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    val name = args.checkString(0)
    // 1.7.10 的 `Loader.isModLoaded` → 1.21.1 的 `ModList`。
    // 旧 `ModAPIManager.hasAPI`（FML 的旧式 API 注册表）在 1.21.1 已被移除，
    // 其语义（把「提供 API 的 mod」也算作已加载）没有等价物，故不再判定。
    result(net.neoforged.fml.ModList.get().isLoaded(name))
  }

  @Callback(doc = """function(command:string):number -- Runs an arbitrary command using a fake player.""")
  def runCommand(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    val commands =
      // 1.7.10 的 `collectionAsScalaIterable` 已被移除，改用 CollectionConverters 的 `.asScala`。
      if (args.isTable(0)) args.checkTable(0).values().asScala
      else Iterable(args.checkString(0))

    CommandSender.synchronized {
      CommandSender.prepare()
      var value = 0
      for (command <- commands) {
        // 1.7.10 的 `MinecraftServer.getServer.getCommandManager` →
        // 1.21.1 的 `MinecraftServer#getCommands`。`Commands` 上已没有返回 int 的
        // `executeCommand`：带 `/` 前缀的旧写法对应 `performPrefixedCommand`（无返回值），
        // 执行结果由 `CommandSender` 的 `CommandResultCallback` 记录在 `lastResult` 里。
        serverOf(host.world).foreach(_.getCommands.performPrefixedCommand(CommandSender.commandStack, command.toString))
        value = CommandSender.lastResult
      }
      result(value, CommandSender.messages.orNull)
    }
  }

  @Callback(doc = """function(x:number, y:number, z:number):boolean -- Add a component block at the specified coordinates to the computer network.""")
  def connectToBlock(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    val x = args.checkInteger(0)
    val y = args.checkInteger(1)
    val z = args.checkInteger(2)
    findNode(x, y, z) match {
      case Some(other) =>
        remoteNode.foreach(other => node.disconnect(other))
        remoteNode = Some(other)
        remoteNodePosition = Some((x, y, z))
        node.connect(other)
        result(true)
      case _ =>
        result(Unit, "no node found at this position")
    }
  }

  private def findNode(x: Int, y: Int, z: Int): Option[Node] = {
    val position = new BlockPosition(x, y, z, None)
    // 1.7.10 的 `World#blockExists` → 1.21.1 的 `Level#isLoaded`，
    // `World#getTileEntity` → `Level#getBlockEntity`。
    if (host.world.isLoaded(position.toChunkCoordinates)) {
      host.world.getBlockEntity(position.toChunkCoordinates) match {
        // 1.7.10 的 `ForgeDirection.VALID_DIRECTIONS` → 1.21.1 的 `Direction.values`。
        case env: SidedEnvironment => Direction.values.map(env.sidedNode).find(_ != null)
        case env: Environment => Option(env.node)
        case _ => None
      }
    }
    else None
  }

  @Callback(doc = """function():userdata -- Test method for user-data and general value conversion.""")
  def test(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()

    val v1 = mutable.Map("a" -> true, "b" -> "test")
    val v2 = Map(10 -> "zxc", false -> v1)
    v1 += "c" -> v2

    result(v2, new DebugCard.TestValue(), host.world)
  }

  // ----------------------------------------------------------------------- //

  @Callback(doc = """function(player:string, text:string) -- Sends text to the specified player's clipboard if possible.""")
  def sendToClipboard(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    Option(serverOf(host.world).map(_.getPlayerList.getPlayerByName(args.checkString(0))).orNull) match {
      case Some(player) =>
        PacketSender.sendClipboard(player, args.checkString(1))
        result(true)
      case _ =>
        result(false, "no such player")
    }
  }

  @Callback(doc = """function(address:string, data...) -- Sends data to the debug card with the specified address.""")
  def sendToDebugCard(context: Context, args: Arguments): Array[AnyRef] = {
    checkAccess()
    val destination = args.checkString(0)
    DebugNetwork.getEndpoint(destination).filter(_ != this).foreach{endpoint =>
      // 1.21.1 的 `Arguments` 不再继承 Scala 的 `Seq`，没有 `drop`；
      // 先转成数组再丢弃第一个参数（目的地址）。
      val packet = Network.newPacket(node.address, destination, 0, args.toArray.drop(1))
      endpoint.receivePacket(packet)
    }
    result()
  }

  override def receivePacket(packet: Packet): Unit = {
    val distance = 0
    node.sendToReachable("computer.signal", Seq("debug_message", packet.source, Int.box(packet.port), Double.box(distance)) ++ packet.data.toSeq: _*)
  }

  override def address: String = if(node != null) node.address() else "debug"

  // ----------------------------------------------------------------------- //

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      DebugNetwork.add(this)
      remoteNodePosition.foreach {
        case (x, y, z) =>
          remoteNode = findNode(x, y, z)
          remoteNode match {
            case Some(other) => node.connect(other)
            case _ => remoteNodePosition = None
          }
      }
    }
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      DebugNetwork.remove(this)
      remoteNode.foreach(other => other.disconnect(node))
    }
    else if (remoteNode.contains(node)) {
      remoteNode = None
      remoteNodePosition = None
    }
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    access = DebugCard.loadAccess(nbt)
    if (nbt.contains(Settings.namespace + "remoteX")) {
      val x = nbt.getInt(Settings.namespace + "remoteX")
      val y = nbt.getInt(Settings.namespace + "remoteY")
      val z = nbt.getInt(Settings.namespace + "remoteZ")
      remoteNodePosition = Some((x, y, z))
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    access.foreach(saveAccess(_, nbt))
    remoteNodePosition.foreach {
      case (x, y, z) =>
        nbt.putInt(Settings.namespace + "remoteX", x)
        nbt.putInt(Settings.namespace + "remoteY", y)
        nbt.putInt(Settings.namespace + "remoteZ", z)
    }
  }
}

object DebugCard {
  def checkAccess()(implicit ctx: Option[Settings.AccessContext]): Unit =
    for (msg <- Settings.get.debugCardAccess.checkAccess(ctx))
      throw new Exception(msg)

  def serverOf(level: Level): Option[MinecraftServer] =
    if (level == null) None else Option(level.getServer)

  /**
   * 1.7.10 的 `DimensionManager.getWorld(id)` 接受数字维度 ID；1.21.1 已没有数字维度 ID
   * （`Level#dimension()` 返回 `ResourceKey[Level]`），因此这里用维度 key 的字符串哈希当作 ID，
   * 保证同一维度在会话之间稳定可得。**注意**：数值与旧版数字 ID 不再对应，
   * 0/-1/1 也不再分别指向主世界 / 下界 / 末地。
   */
  def serverLevel(server: Option[MinecraftServer], dimensionId: Int): Option[ServerLevel] =
    server.flatMap(_.getAllLevels.asScala.find(level => dimensionIdOf(level) == dimensionId))

  /** 把维度 key 映射成稳定的伪数字 ID（见 [[serverLevel]]）。 */
  def dimensionIdOf(level: Level): Int =
    if (level == null) 0 else level.dimension().location().toString.hashCode

  /**
   * 按维度 key 取世界。1.7.10 用 `DimensionManager.getProvider(id).worldObj`；
   * 1.21.1 改为 `MinecraftServer#getLevel(ResourceKey)`。
   */
  def serverLevelAt(location: ResourceLocation): Option[ServerLevel] =
    if (location == null) None
    else Option(ServerLifecycleHooks.getCurrentServer).
      flatMap(server => Option(server.getLevel(ResourceKey.create(Registries.DIMENSION, location))))

  /** 1.21.1 已没有 `NBTBase.NBTTypes`，这里保留同等的信息用于错误提示。 */
  private val NbtTypeNames = Map[Byte, String](
    Tag.TAG_END -> "TAG_End",
    Tag.TAG_BYTE -> "TAG_Byte",
    Tag.TAG_SHORT -> "TAG_Short",
    Tag.TAG_INT -> "TAG_Int",
    Tag.TAG_LONG -> "TAG_Long",
    Tag.TAG_FLOAT -> "TAG_Float",
    Tag.TAG_DOUBLE -> "TAG_Double",
    Tag.TAG_BYTE_ARRAY -> "TAG_Byte_Array",
    Tag.TAG_STRING -> "TAG_String",
    Tag.TAG_LIST -> "TAG_List",
    Tag.TAG_COMPOUND -> "TAG_Compound",
    Tag.TAG_INT_ARRAY -> "TAG_Int_Array",
    Tag.TAG_LONG_ARRAY -> "TAG_Long_Array"
  )

  /**
   * 1.21.1：原 `DebugCard.AccessContext` 已并入 [[li.cil.oc.Settings.AccessContext]]
   * （`common/item/data/DebugCardData` 也直接使用它）。这里直接复用 `DebugCardData`
   * 上的辅助方法，语义与旧实现完全一致，避免两份实现漂移。
   */
  private def loadAccess(nbt: CompoundTag): Option[Settings.AccessContext] =
    DebugCardData.loadAccess(nbt)

  /**
   * 等价于旧 `AccessContext#save(nbt)`。`Settings.AccessContext` 是纯 case class，
   * 自身没有 `save`；而 `DebugCardData` 里的隐式转换在本包中不可用，因此就地写出。
   */
  def saveAccess(ctx: Settings.AccessContext, nbt: CompoundTag): Unit = {
    nbt.putString(Settings.namespace + "player", ctx.player)
    nbt.putString(Settings.namespace + "accessNonce", ctx.nonce)
  }

  class PlayerValue(var name: String)(implicit var ctx: Option[Settings.AccessContext]) extends prefab.AbstractValue {
    def this() = this("")(None) // For loading.

    // ----------------------------------------------------------------------- //

    def withPlayer(f: (ServerPlayer) => Array[AnyRef]) = {
      checkAccess()
      Option(ServerLifecycleHooks.getCurrentServer).
        flatMap(server => Option(server.getPlayerList.getPlayerByName(name))) match {
        case Some(player) => f(player)
        case _ => result(Unit, "player is offline")
      }
    }

    @Callback(doc = """function():userdata -- Get the player's world object.""")
    def getWorld(context: Context, args: Arguments): Array[AnyRef] = {
      withPlayer(player => result(new DebugCard.WorldValue(player.level())))
    }

    @Callback(doc = """function():string -- Get the player's game type.""")
    def getGameType(context: Context, args: Arguments): Array[AnyRef] =
      // 1.7.10 的 `theItemInWorldManager.getGameType` →
      // 1.21.1 的 `ServerPlayer#gameMode.getGameModeForPlayer`。
      withPlayer(player => result(player.gameMode.getGameModeForPlayer.getName))

    @Callback(doc = """function(gametype:string) -- Set the player's game type (survival, creative, adventure).""")
    def setGameType(context: Context, args: Arguments): Array[AnyRef] =
      withPlayer(player => {
        val gametype = args.checkString(0)
        // 1.7.10 的 `setGameType` → 1.21.1 的 `setGameMode`；
        // `GameType` 也从 `net.minecraft.world.WorldSettings.GameType` 挪到了 `net.minecraft.world.GameType`。
        player.setGameMode(GameType.values.find(_.getName == gametype).getOrElse(GameType.SURVIVAL))
        null
      })

    @Callback(doc = """function():number, number, number -- Get the player's position.""")
    def getPosition(context: Context, args: Arguments): Array[AnyRef] =
      withPlayer(player => result(player.getX, player.getY, player.getZ))

    @Callback(doc = """function(x:number, y:number, z:number) -- Set the player's position.""")
    def setPosition(context: Context, args: Arguments): Array[AnyRef] =
      withPlayer(player => {
        // 1.7.10 的 `setPositionAndUpdate` → 1.21.1 的 `teleportTo(x, y, z)`。
        player.teleportTo(args.checkDouble(0), args.checkDouble(1), args.checkDouble(2))
        null
      })

    @Callback(doc = """function():number -- Get the player's health.""")
    def getHealth(context: Context, args: Arguments): Array[AnyRef] =
      withPlayer(player => result(player.getHealth))

    @Callback(doc = """function():number -- Get the player's max health.""")
    def getMaxHealth(context: Context, args: Arguments): Array[AnyRef] =
      withPlayer(player => result(player.getMaxHealth))

    @Callback(doc = """function(health:number) -- Set the player's health.""")
    def setHealth(context: Context, args: Arguments): Array[AnyRef] =
      withPlayer(player => {
        player.setHealth(args.checkDouble(0).toFloat)
        null
      })

    // ----------------------------------------------------------------------- //

    override def load(nbt: CompoundTag): Unit = {
      super.load(nbt)
      ctx = DebugCard.loadAccess(nbt)
      name = nbt.getString("name")
    }

    override def save(nbt: CompoundTag): Unit = {
      super.save(nbt)
      ctx.foreach(DebugCard.saveAccess(_, nbt))
      nbt.putString("name", name)
    }
  }

  class WorldValue(var world: Level)(implicit var ctx: Option[Settings.AccessContext]) extends prefab.AbstractValue {
    def this() = this(null)(None) // For loading.

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function():number -- Gets the numeric id of the current dimension.""")
    def getDimensionId(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `world.provider.dimensionId` 在 1.21.1 已无对应物，返回伪 ID（见 DebugCard.serverLevel）。
      result(dimensionIdOf(world))
    }

    @Callback(doc = """function():string -- Gets the name of the current dimension.""")
    def getDimensionName(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `WorldProvider#getDimensionName` 在 1.21.1 已移除，
      // 这里按维度 key 还原旧版显示名，未知维度回退到 key 本身。
      result(dimensionNameOf(world))
    }

    @Callback(doc = """function():number -- Gets the seed of the world.""")
    def getSeed(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `World#getSeed` 在 1.21.1 只在服务端世界（`ServerLevel#getSeed`）可用。
      result(world match {
        case server: ServerLevel => server.getSeed
        case _ => 0L
      })
    }

    @Callback(doc = """function():boolean -- Returns whether it is currently raining.""")
    def isRaining(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      result(world.isRaining)
    }

    @Callback(doc = """function(value:boolean) -- Sets whether it is currently raining.""")
    def setRaining(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `WorldInfo#setRaining` → 1.21.1 的 `ServerLevelData#setRaining`。
      world.getLevelData.asInstanceOf[ServerLevelData].setRaining(args.checkBoolean(0))
      null
    }

    @Callback(doc = """function():boolean -- Returns whether it is currently thundering.""")
    def isThundering(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      result(world.isThundering)
    }

    @Callback(doc = """function(value:boolean) -- Sets whether it is currently thundering.""")
    def setThundering(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      world.getLevelData.asInstanceOf[ServerLevelData].setThundering(args.checkBoolean(0))
      null
    }

    @Callback(doc = """function():number -- Get the current world time.""")
    def getTime(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `World#getWorldTime` → 1.21.1 的 `Level#getDayTime`。
      result(world.getDayTime)
    }

    @Callback(doc = """function(value:number) -- Set the current world time.""")
    def setTime(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `World#setWorldTime` → 1.21.1 的 `ServerLevel#setDayTime`。
      world match {
        case server: ServerLevel => server.setDayTime(args.checkDouble(0).toLong)
        case _ => // 客户端世界没有可写的世界时间。
      }
      null
    }

    @Callback(doc = """function():number, number, number -- Get the current spawn point coordinates.""")
    def getSpawnPoint(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `WorldInfo#getSpawnX/Y/Z` → 1.21.1 的 `Level#getSharedSpawnPos`。
      val spawn = world.getSharedSpawnPos
      result(spawn.getX, spawn.getY, spawn.getZ)
    }

    @Callback(doc = """function(x:number, y:number, z:number) -- Set the spawn point coordinates.""")
    def setSpawnPoint(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `WorldInfo#setSpawnPosition(x, y, z)` →
      // 1.21.1 的 `WritableLevelData#setSpawn(BlockPos, angle)`。
      world.getLevelData.asInstanceOf[ServerLevelData].setSpawn(
        new BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2)), 0.0F)
      null
    }

    @Callback(doc = """function(x:number, y:number, z:number, sound:string, range:number) -- Play a sound at the specified coordinates.""")
    def playSoundAt(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      val (x, y, z) = (args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      val sound = args.checkString(3)
      val range = args.checkInteger(4)
      // 1.7.10 的 `World#playSoundEffect(x, y, z, name, volume, pitch)` 在 1.21.1 拆成
      // 「按注册名查 SoundEvent」+「playSound」，这里按旧名做一次宽松匹配，未命中则忽略。
      soundByName(sound) match {
        case Some(event) => world.playSound(null, x + 0.5, y + 0.5, z + 0.5, event, SoundSource.BLOCKS, range / 15 + 0.5F, 1.0F)
        case _ => // 未知音效名，忽略。
      }
      null
    }

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function(x:number, y:number, z:number):number -- Get the ID of the block at the specified coordinates.""")
    def getBlockId(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.21.1 的方块是注册表对象，没有跨会话稳定的数字 ID，这里返回注册表原始序号
      // （`Block.getIdFromBlock` 的等价物，仅在同一会话内有效）。
      result(BuiltInRegistries.BLOCK.getId(blockAt(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))))
    }

    @Callback(doc = """function(x:number, y:number, z:number):number -- Get the metadata of the block at the specified coordinates.""")
    def getMetadata(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // TODO(标签): 1.21.1 已移除方块 metadata，状态改由 BlockState 属性承载，
      // 无法还原成单个数字；这里固定返回 0（与 ExtendedWorld.getBlockMetadata 一致）。
      result(0)
    }

    @Callback(doc = """function(x:number, y:number, z:number):number -- Check whether the block at the specified coordinates is loaded.""")
    def isLoaded(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `World#blockExists` → 1.21.1 的 `Level#isLoaded`。
      result(world.isLoaded(blockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))))
    }

    @Callback(doc = """function(x:number, y:number, z:number):number -- Check whether the block at the specified coordinates has a tile entity.""")
    def hasTileEntity(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      val (x, y, z) = (args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      // 1.7.10 的 `World#getTileEntity` → 1.21.1 的 `Level#getBlockEntity`。
      result(world.getBlockEntity(blockPos(x, y, z)) != null)
    }

    @Callback(doc = """function(x:number, y:number, z:number):table -- Get the NBT of the block at the specified coordinates.""")
    def getTileNBT(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      val (x, y, z) = (args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      world.getBlockEntity(blockPos(x, y, z)) match {
        // 1.7.10 的 `TileEntity#writeToNBT` → 1.21.1 的 `BlockEntity#saveWithFullMetadata`
        // （1.21.1 需要注册表访问器来序列化物品等组件）。
        case tileEntity: BlockEntity => result(tileEntity.saveWithFullMetadata(world.registryAccess()).toTypedMap)
        case _ => null
      }
    }

    @Callback(doc = """function(x:number, y:number, z:number, nbt:table):boolean -- Set the NBT of the block at the specified coordinates.""")
    def setTileNBT(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      val (x, y, z) = (args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      world.getBlockEntity(blockPos(x, y, z)) match {
        case tileEntity: BlockEntity =>
          typedMapToNbt(args.checkTable(3).asScala.toMap) match {
            case nbt: CompoundTag =>
              // 1.7.10 的 `TileEntity#readFromNBT` → 1.21.1 的 `loadWithComponents`；
              // `markDirty` → `setChanged`。
              tileEntity.loadWithComponents(nbt, world.registryAccess())
              tileEntity.setChanged()
              world.markBlockForUpdate(BlockPosition(x, y, z))
              result(true)
            case nbt => result(Unit, s"nbt tag compound expected, got '${NbtTypeNames.getOrElse(nbt.getId, "UNKNOWN")}'")
          }
        case _ => result(Unit, "no tile entity")
      }
    }

    @Callback(doc = """function(x:number, y:number, z:number):number -- Get the light opacity of the block at the specified coordinates.""")
    def getLightOpacity(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `World#getBlockLightOpacity` → 1.21.1 的 `BlockState#getLightBlock`。
      val pos = blockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      result(world.getBlockState(pos).getLightBlock(world, pos))
    }

    @Callback(doc = """function(x:number, y:number, z:number):number -- Get the light value (emission) of the block at the specified coordinates.""")
    def getLightValue(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `World#getBlockLightValue` → 1.21.1 的 `BlockState#getLightEmission`。
      val pos = blockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      result(world.getBlockState(pos).getLightEmission(world, pos))
    }

    @Callback(doc = """function(x:number, y:number, z:number):number -- Get whether the block at the specified coordinates is directly under the sky.""")
    def canSeeSky(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `World#canBlockSeeTheSky` → 1.21.1 的 `Level#canSeeSky`。
      result(world.canSeeSky(blockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))))
    }

    @Callback(doc = """function(x:number, y:number, z:number, id:number or string, meta:number):number -- Set the block at the specified coordinates.""")
    def setBlock(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // TODO(标签): 1.21.1 已移除方块 metadata，`meta` 参数被忽略
      //（只使用 `Block#defaultBlockState`，与 ExtendedWorld.setBlock 的退化策略一致）。
      val block = blockOf(args, 3)
      result(world.setBlock(blockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2)), block.defaultBlockState(), 3))
    }

    @Callback(doc = """function(x1:number, y1:number, z1:number, x2:number, y2:number, z2:number, id:number or string, meta:number):number -- Set all blocks in the area defined by the two corner points (x1, y1, z1) and (x2, y2, z2).""")
    def setBlocks(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      val (xMin, yMin, zMin) = (args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
      val (xMax, yMax, zMax) = (args.checkInteger(3), args.checkInteger(4), args.checkInteger(5))
      // TODO(标签): 同 setBlock，`meta`（第 8 个参数）在 1.21.1 无对应物，被忽略。
      val block = blockOf(args, 6)
      for (x <- math.min(xMin, xMax) to math.max(xMin, xMax)) {
        for (y <- math.min(yMin, yMax) to math.max(yMin, yMax)) {
          for (z <- math.min(zMin, zMax) to math.max(zMin, zMax)) {
            world.setBlock(blockPos(x, y, z), block.defaultBlockState(), 3)
          }
        }
      }
      null
    }

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function(id:string, count:number, damage:number, nbt:string, x:number, y:number, z:number, side:number):boolean - Insert an item stack into the inventory at the specified location. NBT tag is expected in JSON format.""")
    def insertItem(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `Item.itemRegistry.getObject(id)` → 1.21.1 的物品注册表。
      val item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(args.checkString(0)))
      if (item == null || item == Items.AIR) {
        throw new IllegalArgumentException("invalid item id")
      }
      val count = args.checkInteger(1)
      val damage = args.checkInteger(2)
      val tagJson = args.optString(3, "")
      // 1.7.10 的 `JsonToNBT.func_150315_a` → 1.21.1 的 `TagParser.parseTag`。
      val tag = if (Strings.isNullOrEmpty(tagJson)) null else TagParser.parseTag(tagJson)
      val position = BlockPosition(args.checkDouble(4), args.checkDouble(5), args.checkDouble(6), world)
      val side = args.checkSideAny(7)
      InventoryUtils.inventoryAt(position) match {
        case Some(inventory) =>
          // 1.21.1 的 `ItemStack` 没有 damage 构造参数，旧 `damage` 值改写进
          // `DataComponents.DAMAGE`（无 NBT 时也带上，与原实现总是设置 metadata 一致）。
          val stack = new ItemStack(item, count)
          if (damage != 0) {
            stack.set(DataComponents.DAMAGE, Int.box(damage))
          }
          if (tag != null) {
            stack.setTag(tag)
          }
          result(InventoryUtils.insertIntoInventory(stack, inventory, Option(side)))
        case _ => result(Unit, "no inventory")
      }
    }

    @Callback(doc = """function(x:number, y:number, z:number, slot:number[, count:number]):number - Reduce the size of an item stack in the inventory at the specified location.""")
    def removeItem(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      val position = BlockPosition(args.checkDouble(0), args.checkDouble(1), args.checkDouble(2), world)
      InventoryUtils.inventoryAt(position) match {
        case Some(inventory) =>
          val slot = args.checkSlot(inventory, 3)
          // 1.7.10 的 `IInventory#getInventoryStackLimit` → 1.21.1 的 `IItemHandler#getSlotLimit`。
          val count = args.optInteger(4, inventory.getSlotLimit(slot))
          // 1.7.10 的 `IInventory#decrStackSize` → 1.21.1 的 `IItemHandler#extractItem`
          //（空槽返回 `ItemStack.EMPTY` 而非 `null`）。
          val removed = inventory.extractItem(slot, count, false)
          if (removed == null || removed.isEmpty) result(0)
          else result(removed.getCount)
        case _ => result(Unit, "no inventory")
      }
    }

    @Callback(doc = """function(id:string, amount:number, x:number, y:number, z:number, side:number):boolean - Insert some fluid into the tank at the specified location.""")
    def insertFluid(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      // 1.7.10 的 `FluidRegistry.getFluid(name)` → 1.21.1 的流体注册表。
      // 注意：`FluidStack` 在 1.21.1 接受的是 `Fluid`（或其 `Holder`），不是 `FluidType`。
      val fluid = BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(args.checkString(0))) match {
        case f if f != null && f != Fluids.EMPTY => f
        case _ => null
      }
      if (fluid == null) {
        throw new IllegalArgumentException("invalid fluid id")
      }
      val amount = args.checkInteger(1)
      val position = BlockPosition(args.checkDouble(2), args.checkDouble(3), args.checkDouble(4), world)
      // 1.21.1 的 `fill` 不再需要 side 参数（面过滤由能力提供方完成），
      // 且用 `FluidAction` 取代旧的布尔 `doFill`。
      FluidUtils.fluidHandlerAt(position) match {
        case Some(handler) => result(handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE))
        case _ => result(Unit, "no tank")
      }
    }

    @Callback(doc = """function(amount:number, x:number, y:number, z:number, side:number):boolean - Remove some fluid from a tank at the specified location.""")
    def removeFluid(context: Context, args: Arguments): Array[AnyRef] = {
      checkAccess()
      val amount = args.checkInteger(0)
      val position = BlockPosition(args.checkDouble(1), args.checkDouble(2), args.checkDouble(3), world)
      // 1.21.1 的 `drain(maxDrain, action)` 返回 `FluidStack`，因此取抽出的量作为返回值
      //（1.7.10 的 `drain` 同样返回实际抽出的量）。
      FluidUtils.fluidHandlerAt(position) match {
        case Some(handler) =>
          val drained = handler.drain(amount, IFluidHandler.FluidAction.EXECUTE)
          result(if (drained == null || drained.isEmpty) 0 else drained.getAmount)
        case _ => result(Unit, "no tank")
      }
    }

    // ----------------------------------------------------------------------- //

    override def load(nbt: CompoundTag): Unit = {
      super.load(nbt)
      ctx = DebugCard.loadAccess(nbt)
      // 1.7.10 存数字维度 ID；1.21.1 改存维度 key 字符串（见 DebugCard.serverLevel）。
      world = if (nbt.contains("dimension")) {
        DebugCard.serverLevelAt(ResourceLocation.tryParse(nbt.getString("dimension"))).orNull
      } else null
    }

    override def save(nbt: CompoundTag): Unit = {
      super.save(nbt)
      ctx.foreach(DebugCard.saveAccess(_, nbt))
      if (world != null) {
        nbt.putString("dimension", world.dimension().location().toString)
      }
    }

    // ----------------------------------------------------------------------- //

    private def blockPos(x: Int, y: Int, z: Int) = new BlockPos(x, y, z)

    private def blockAt(x: Int, y: Int, z: Int): Block =
      world.getBlockState(blockPos(x, y, z)).getBlock

    /**
     * 旧接口允许用数字 ID 或注册名指定方块；1.21.1 分别对应注册表序号与注册名
     *（1.7.10 的 `Block.getBlockById` / `Block.getBlockFromName`）。
     */
    private def blockOf(args: Arguments, index: Int): Block =
      if (args.isInteger(index)) BuiltInRegistries.BLOCK.byId(args.checkInteger(index))
      else BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(args.checkString(index)))

    /**
     * 1.7.10 的 `World#playSoundEffect` 接受音效名字符串；1.21.1 的音效是注册表对象，
     * 这里按旧名（大驼峰 → 下划线小写）在注册表中做一次宽松匹配，未命中则返回 `None`。
     */
    private def soundByName(name: String): Option[SoundEvent] = {
      val path = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT)
      val candidates = Seq(path, "entity." + path, "block." + path, "ambient." + path, "ui." + path)
      candidates.iterator.
        flatMap(candidate => Option(ResourceLocation.tryParse(candidate))).
        flatMap(key => Option(BuiltInRegistries.SOUND_EVENT.get(key))).
        nextOption()
    }

    /**
     * 1.7.10 的 `WorldProvider#getDimensionName` 在 1.21.1 已移除，
     * 这里按维度 key 还原三个原版维度的显示名，未知维度回退到 key 本身。
     */
    private def dimensionNameOf(level: Level): String = {
      if (level == null) ""
      else level.dimension().location().toString match {
        case "minecraft:overworld" => "Overworld"
        case "minecraft:the_nether" => "Nether"
        case "minecraft:the_end" => "The End"
        case other => other
      }
    }
  }

  class CommandSender(val host: EnvironmentHost, val underlying: ServerPlayer) extends FakePlayer(underlying.level().asInstanceOf[ServerLevel], underlying.getGameProfile) {
    var messages: Option[String] = None

    /**
     * 1.7.10 里 `CommandSender` 既是假玩家又直接充当命令执行者（`ICommandSender`）；
     * 1.21.1 的命令执行者改为 `CommandSourceStack`：这里基于底层玩家的命令源，
     * 把位置、维度与本对象换成调试卡所在的位置，从而把命令输出截获到 [[messages]]。
     *
     * 注意：`CommandSourceStack.withCallback` 有两个重载（单个 `CommandResultCallback` 与
     * 「回调 + 合并函数」），直接写 lambda 会让 Scala 无法推断参数类型，因此显式构造匿名类。
     */
    private val commandSource: CommandSourceStack = underlying.createCommandSourceStack().
      withLevel(host.world.asInstanceOf[ServerLevel]).
      withPosition(BlockPosition(host).toVec3).
      withCallback(new CommandResultCallback {
        override def onResult(success: Boolean, result: Int): Unit = {
          // 保留旧行为：命令成功但无返回值时视作 1，失败视作 0。
          lastResult = if (result > 0) result else if (success) 1 else 0
        }
      }).
      withSource(this)

    /** 最近一次命令的执行结果（旧版 `executeCommand` 的返回值）。 */
    var lastResult: Int = 0

    def prepare(): Unit = {
      // 1.7.10 直接写 `posX/posY/posZ` 字段；1.21.1 用 `setPos`。
      setPos(host.xPosition + 0.5, host.yPosition + 0.5, host.zPosition + 0.5)
      messages = None
      lastResult = 0
    }

    /** 等价于旧版 `getCommandSenderName`：底层玩家的档案名（命令输出里显示的名字）。 */
    override def getScoreboardName: String = underlying.getScoreboardName

    override def level(): Level = host.world

    /** 截获命令反馈（1.7.10 的 `addChatMessage`），供 `runCommand` 返回给 Lua。 */
    override def sendSystemMessage(message: Component): Unit = {
      messages = Option(messages.fold("")(_ + "\n") + message.getString)
    }

    /**
     * 1.7.10 手工查 OP 名单判断权限；1.21.1 的 `CommandSourceStack#hasPermission`
     * 已经把单人 / OP 名单 / 权限等级统一处理掉了，直接委托给它。
     */
    def canCommandSenderUseCommand(level: Int, command: String): Boolean =
      commandSource.hasPermission(level)

    /** 旧版 `getPlayerCoordinates` 的等价物。 */
    def getPlayerCoordinates: BlockPos = BlockPosition(host).toChunkCoordinates

    /** 该假玩家用于执行命令的命令源栈。 */
    def commandStack: CommandSourceStack = commandSource
  }

  class TestValue extends AbstractValue {
    var value = "hello"

    override def apply(context: Context, arguments: Arguments): AnyRef = {
      OpenComputers.log.info("TestValue.apply(" + arguments.toArray.mkString(", ") + ")")
      value
    }

    override def unapply(context: Context, arguments: Arguments): Unit = {
      OpenComputers.log.info("TestValue.unapply(" + arguments.toArray.mkString(", ") + ")")
      value = arguments.checkString(1)
    }

    override def call(context: Context, arguments: Arguments): Array[AnyRef] = {
      OpenComputers.log.info("TestValue.call(" + arguments.toArray.mkString(", ") + ")")
      result(arguments.toArray.toSeq: _*)
    }

    override def dispose(context: Context): Unit = {
      super.dispose(context)
      OpenComputers.log.info("TestValue.dispose()")
    }

    override def load(nbt: CompoundTag): Unit = {
      super.load(nbt)
      value = nbt.getString("value")
    }

    override def save(nbt: CompoundTag): Unit = {
      super.save(nbt)
      nbt.putString("value", value)
    }
  }

}
