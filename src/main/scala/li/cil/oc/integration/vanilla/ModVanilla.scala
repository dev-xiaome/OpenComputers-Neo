package li.cil.oc.integration.vanilla

import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.integration.util.BundledRedstone.RedstoneProvider
import li.cil.oc.integration.{ModProxy, Mods}
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.Direction
import net.minecraft.world.level.block.{Blocks, RedStoneWireBlock}

/**
 * 原版集成代理。
 *
 * 1.21.1 迁移要点：
 *  - `Driver.add` 仍然是 `li.cil.oc.api.Driver` 的静态方法（本项目由
 *    `server.driver.Registry` 实现 `api.detail.DriverAPI`），调用方式不变。
 *  - 事件注册从 `MinecraftForge.EVENT_BUS.register(EventHandlerVanilla)`
 *    改为 [[EventHandlerVanilla.initialize]]：NeoForge 对 Scala `object` 的
 *    `@SubscribeEvent` 注解扫描不可靠，显式 `addListener` 才不会静默失效。
 *  - `ForgeDirection` → `net.minecraft.core.Direction`
 *  - `world.getBlock(pos) == Blocks.redstone_wire` → `blockState.is(Blocks.REDSTONE_WIRE)`
 *  - `world.getBlockMetadata(pos)`（红石线能量）→ `blockState.getValue(RedStoneWireBlock.POWER)`
 *  - `Settings.get.enableInventoryDriver` / `enableTankDriver` / `enableCommandBlockDriver`
 *    在本项目的 `Settings.scala` 中**仍然存在**（对应 `application.conf` 的
 *    `integration.vanilla.*`），因此保持原有的条件注册逻辑。
 */
object ModVanilla extends ModProxy with RedstoneProvider {
  override def getMod = Mods.Minecraft

  override def initialize(): Unit = {
    Driver.add(DriverBeacon)
    Driver.add(DriverBrewingStand)
    Driver.add(DriverComparator)
    Driver.add(DriverFurnace)
    Driver.add(DriverMobSpawner)
    Driver.add(DriverNoteBlock)
    Driver.add(DriverRecordPlayer)

    Driver.add(DriverBeacon.Provider)
    Driver.add(DriverBrewingStand.Provider)
    Driver.add(DriverComparator.Provider)
    Driver.add(DriverFurnace.Provider)
    Driver.add(DriverMobSpawner.Provider)
    Driver.add(DriverNoteBlock.Provider)
    Driver.add(DriverRecordPlayer.Provider)

    if (Settings.get.enableInventoryDriver) {
      Driver.add(DriverInventory)
    }
    if (Settings.get.enableTankDriver) {
      Driver.add(DriverFluidHandler)
    }
    if (Settings.get.enableCommandBlockDriver) {
      Driver.add(DriverCommandBlock)
    }

    Driver.add(ConverterFluidContainerItem)
    Driver.add(ConverterFluidStack)
    Driver.add(ConverterFluidTankInfo)
    Driver.add(ConverterItemStack)
    Driver.add(ConverterNBT)
    Driver.add(ConverterWorld)
    Driver.add(ConverterWorldProvider)

    RecipeHandler.init()

    BundledRedstone.addProvider(this)

    EventHandlerVanilla.initialize()
  }

  override def computeInput(pos: BlockPosition, side: Direction): Int = {
    val world = pos.world.get
    val direction = pos.offset(side)
    val position = direction.toChunkCoordinates
    // 1.21.1 已没有方块 metadata：红石线的输出强度改为方块状态属性 `POWER`。
    val wireSignal = if (world.isLoaded(position) && world.getBlockState(position).is(Blocks.REDSTONE_WIRE)) {
      world.getBlockState(position).getValue(RedStoneWireBlock.POWER).intValue
    }
    else 0
    math.max(world.computeRedstoneSignal(pos, side), wireSignal)
  }

  override def computeBundledInput(pos: BlockPosition, side: Direction): Array[Int] = null
}
