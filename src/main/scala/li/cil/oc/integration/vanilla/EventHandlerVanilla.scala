package li.cil.oc.integration.vanilla

import li.cil.oc.Settings
import li.cil.oc.api.event.GeolyzerEvent
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.{Blocks, CocoaBlock, CropBlock, NetherWartBlock}
import net.neoforged.neoforge.common.NeoForge

/**
 * 原版相关的事件处理器（地质扫描仪的数据来源）。
 *
 * 1.21.1 迁移要点：
 *  - **不再使用 `@SubscribeEvent` + `NeoForge.EVENT_BUS.register`**：
 *    NeoForge 对 Scala `object` 的注解扫描不可靠（注解一旦没被扫到就是静默失效），
 *    因此与 `common/EventHandler` 保持一致，改为 [[initialize]] 里显式 `addListener`。
 *  - `world.rand`（`java.util.Random`）→ `world.getRandom`（`RandomSource`），
 *    它没有 `nextBytes`，改为逐个取 `0..255` 再截断成 `Byte`。
 *  - `world.blockExists(x, y, z)` → `world.isLoaded(new BlockPos(x, y, z))`
 *  - `world.isAirBlock(x, y, z)` → `world.getBlockState(pos).isAir`
 *  - `block.isReplaceable(world, x, y, z)` → `state.canBeReplaced`
 *  - `block.getBlockHardness(world, x, y, z)` → `state.getDestroySpeed(world, pos)`
 *  - 方块 metadata 已移除：`getBlockMetadata` 一律退化为 `0`，
 *    作物成熟度改读 `BlockState` 的 `AGE` 属性（见下方 TODO）。
 */
object EventHandlerVanilla {
  /** 注册所有监听器；由 [[ModVanilla]] 在集成初始化时调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: GeolyzerEvent.Scan) => onGeolyzerScan(e))
    NeoForge.EVENT_BUS.addListener((e: GeolyzerEvent.Analyze) => onGeolyzerAnalyze(e))
  }

  def onGeolyzerScan(e: GeolyzerEvent.Scan): Unit = {
    val world = e.host.world
    val blockPos = BlockPosition(e.host)
    val includeReplaceable = e.options.get("includeReplaceable") match {
      case value: java.lang.Boolean => value.booleanValue()
      case _ => true
    }

    val noise = new Array[Byte](e.data.length)
    // TODO(port): 1.21.1 的 `RandomSource` 没有 `nextBytes`，这里手工生成等价的随机字节。
    val random = world.getRandom
    for (i <- noise.indices) noise(i) = random.nextInt(256).toByte
    // Map to [-1, 1). The additional /33f is for normalization below.
    noise.map(_ / 128f / 33f).copyToArray(e.data)

    val w = e.maxX - e.minX + 1
    val d = e.maxZ - e.minZ + 1
    for (ry <- e.minY to e.maxY; rz <- e.minZ to e.maxZ; rx <- e.minX to e.maxX) {
      val x = blockPos.x + rx
      val y = blockPos.y + ry
      val z = blockPos.z + rz
      val index = (rx - e.minX) + ((rz - e.minZ) + (ry - e.minY) * d) * w
      val position = new BlockPos(x, y, z)
      if (world.isLoaded(position) && !world.getBlockState(position).isAir) {
        val state = world.getBlockState(position)
        if (includeReplaceable || isFluid(state) || !state.canBeReplaced) {
          val dx = blockPos.x - x
          val dy = blockPos.y - y
          val dz = blockPos.z - z
          val distance = math.sqrt(dx * dx + dy * dy + dz * dz).toFloat
          e.data(index) = e.data(index) * distance * Settings.get.geolyzerNoise + state.getDestroySpeed(world, position)
        }
        else e.data(index) = 0
      }
      else e.data(index) = 0
    }
  }

  /** 旧版 `FluidRegistry.lookupFluidForBlock(block) != null` 的 1.21.1 等价实现。 */
  private def isFluid(state: BlockState): Boolean = {
    val fluidState = state.getFluidState
    fluidState != null && !fluidState.isEmpty
  }

  def onGeolyzerAnalyze(e: GeolyzerEvent.Analyze): Unit = {
    val world = e.host.world
    val blockPos = BlockPosition(e.x, e.y, e.z, world)
    val position = blockPos.toChunkCoordinates
    val state = world.getBlockState(position)
    val block = state.getBlock

    e.data.put("name", BuiltInRegistries.BLOCK.getKey(block).toString)
    // TODO(port): 1.21.1 已移除方块 metadata，状态改由 `BlockState` 属性承载；
    // 为保持 Lua API 兼容仍然返回该键，但取值恒为 0。
    e.data.put("metadata", Int.box(0))
    e.data.put("hardness", Float.box(state.getDestroySpeed(world, position)))
    // TODO(port): 1.21.1 已移除按方块的采掘等级 / 采掘工具查询（改为标签 + 工具组件驱动），
    // 这里分别退化为 0 与空字符串。
    e.data.put("harvestLevel", Int.box(0))
    e.data.put("harvestTool", "")
    e.data.put("color", Int.box(world.getBlockMapColor(blockPos)))

    if (Settings.get.insertIdsInConverters) {
      e.data.put("id", Int.box(BuiltInRegistries.BLOCK.getId(block)))
    }

    // TODO(port): 1.7.10 直接读 `getBlockMetadata()/7f`；1.21.1 改读 `AGE` 属性。
    // 甜菜根等作物的最大 age 小于 7，这里仍然除以 7 以保持与旧版一致的数值范围。
    if (block.isInstanceOf[CropBlock] || state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)) {
      e.data.put("growth", Float.box((state.getValue(BlockStateProperties.AGE_7).intValue / 7f) max 0 min 1))
    }
    if (state.is(Blocks.COCOA)) {
      // 1.7.10 的 metadata 低 2 位是朝向、其余是成熟度，因此旧代码做了 `>> 2`；
      // 1.21.1 的 `CocoaBlock.AGE` 已经是 0..2 的纯成熟度。
      e.data.put("growth", Float.box((state.getValue(CocoaBlock.AGE).intValue / 2f) max 0 min 1))
    }
    if (state.is(Blocks.NETHER_WART)) {
      e.data.put("growth", Float.box((state.getValue(NetherWartBlock.AGE).intValue / 3f) max 0 min 1))
    }
    if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE)) {
      e.data.put("growth", Float.box(1f))
    }
  }
}
