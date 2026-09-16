package li.cil.oc.integration.minecraft

import li.cil.oc.Settings
import li.cil.oc.api.event.GeolyzerEvent
import li.cil.oc.util.{BlockPosHelper, BlockPosition, ItemUtils}
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.{Block, Blocks, CropBlock, LiquidBlock, StemBlock}
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.neoforged.bus.api.SubscribeEvent

import scala.jdk.CollectionConverters._

object EventHandlerVanilla {
  @SubscribeEvent
  def onGeolyzerScan(e: GeolyzerEvent.Scan): Unit = {
    val world = e.host.getEnvironmentLevel
    val blockPos = BlockPosition(e.host)
    val includeReplaceable = e.options.get("includeReplaceable") match {
      case value: java.lang.Boolean => value.booleanValue()
      case _ => true
    }

    val noise = new Array[Byte](e.data.length)
    for (i <- noise.indices) {
      noise(i) = world.random.nextInt(256).toByte
    }
    // Map to [-1, 1). The additional /33f is for normalization below.
    noise.map(_ / 128f / 33f).copyToArray(e.data)

    val w = e.maxX - e.minX + 1
    val d = e.maxZ - e.minZ + 1
    for (ry <- e.minY to e.maxY; rz <- e.minZ to e.maxZ; rx <- e.minX to e.maxX) {
      val pos = BlockPosHelper.offset(blockPos.toBlockPos, rx, ry, rz)
      val index = (rx - e.minX) + ((rz - e.minZ) + (ry - e.minY) * d) * w
      if (world.isLoaded(pos) && !world.isEmptyBlock(pos)) {
        val blockState = world.getBlockState(pos)
        val block = blockState.getBlock
        val isFluid = block.isInstanceOf[LiquidBlock]
        if (!blockState.isAir && (includeReplaceable || isFluid || !blockState.is(BlockTags.REPLACEABLE))) {
          val distance = math.sqrt(rx * rx + ry * ry + rz * rz).toFloat
          e.data(index) = e.data(index) * distance * Settings.get.geolyzerNoise + blockState.getDestroySpeed(world, pos)
        } else {
          e.data(index) = 0
        }
      }
      else e.data(index) = 0
    }
  }

  private def getGrowth(blockState: BlockState) = {
    blockState.getProperties().asScala.find(prop => {prop.isInstanceOf[IntegerProperty] && prop.getName() == "age"}) match {
      case Some(prop) =>
        val propAge = prop.asInstanceOf[IntegerProperty]
        Some((blockState.getValue(propAge).toFloat / propAge.getPossibleValues.asScala.max) max 0 min 1)
      case None => None
    }
  }

  @SubscribeEvent
  def onGeolyzerAnalyze(e: GeolyzerEvent.Analyze): Unit = {
    val world = e.host.getEnvironmentLevel
    val blockState = world.getBlockState(e.pos)
    val block = blockState.getBlock
    val blockName = BuiltInRegistries.BLOCK.getKey(block).toString

    e.data.asScala += "name" -> blockName
    e.data.asScala += "hardness" -> Float.box(blockState.getDestroySpeed(world, e.pos))
    e.data.asScala += "harvestLevel" -> Int.box(ItemUtils.getHarvestLevel(blockState))
    e.data.asScala += "harvestTool" -> ItemUtils.getHarvestTool(blockState)
    e.data.asScala += "color" -> Int.box(blockState.getMapColor(world, e.pos).col)

    // backward compatibility
    e.data.asScala += "metadata" -> Int.box(0)

    e.data.asScala += "properties" -> {
      var props: Map[String, Any] = Map()
      for (prop <- blockState.getProperties.asScala) {
        props += prop.getName() -> blockState.getValue(prop)
      }
      props
    }

    if (Settings.get.insertIdsInConverters) {
      e.data.asScala += "id" -> Int.box(Block.getId(blockState))
    }

    {
      if (block.isInstanceOf[CropBlock] || block.isInstanceOf[StemBlock] || block == Blocks.COCOA || block == Blocks.NETHER_WART || block == Blocks.CHORUS_FLOWER) {
        getGrowth(blockState)
      } else if (block == Blocks.MELON || block == Blocks.PUMPKIN || block == Blocks.CACTUS || block == Blocks.SUGAR_CANE || block == Blocks.CHORUS_PLANT) {
        Some(1f)
      } else {
        None
      }
    } foreach { growth =>
      e.data.asScala += "growth" -> Float.box(growth)
    }
  }
}
