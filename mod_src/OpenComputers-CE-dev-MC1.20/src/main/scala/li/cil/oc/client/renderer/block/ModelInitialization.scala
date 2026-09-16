package li.cil.oc.client.renderer.block

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.item.{Tablet, TabletWrapper}
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.model.{BakedModel, ModelResourceLocation}
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.{DyeColor, Item, ItemStack}
import net.minecraft.world.level.ItemLike
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.util.RandomSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.{Dist, OnlyIn}
import net.minecraftforge.client.event.ModelEvent
import net.minecraftforge.client.model.data.ModelData
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.registries.ForgeRegistries

import scala.collection.mutable

@OnlyIn(Dist.CLIENT)
object ModelInitialization {
  final val CableBlockLocation           = loc(Constants.BlockName.Cable,             "")
  final val CableItemLocation            = loc(Constants.BlockName.Cable,             "inventory")
  final val NetSplitterBlockLocation     = loc(Constants.BlockName.NetSplitter,       "")
  final val NetSplitterItemLocation      = loc(Constants.BlockName.NetSplitter,       "inventory")
  final val PrintBlockLocation           = loc(Constants.BlockName.Print,             "")
  final val PrintItemLocation            = loc(Constants.BlockName.Print,             "inventory")
  final val RobotBlockLocation           = loc(Constants.BlockName.Robot,             "")
  final val RobotItemLocation            = loc(Constants.BlockName.Robot,             "inventory")
  final val RobotAfterimageBlockLocation = loc(Constants.BlockName.RobotAfterimage,   "")
  final val RackBlockLocation            = loc(Constants.BlockName.Rack,              "")

  private def loc(name: String, variant: String) =
    new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, name), variant)

  private case class DynamicItemModel(
                                       getLocation:    ItemStack => ModelResourceLocation,
                                       bakeAdditional: ModelEvent.RegisterAdditional => Unit
                                     )

  private val dynamicItems    = mutable.ArrayBuffer.empty[(Item, DynamicItemModel)]
  private val modelRemappings = mutable.Map.empty[ModelResourceLocation, ModelResourceLocation]

  // Called from ClientProxy.preInit().
  def preInit(): Unit = {
    registerBlockRemapping(Constants.BlockName.Cable,             CableBlockLocation,           CableItemLocation)
    registerBlockRemapping(Constants.BlockName.NetSplitter,       NetSplitterBlockLocation,     NetSplitterItemLocation)
    registerBlockRemapping(Constants.BlockName.Print,             PrintBlockLocation,           PrintItemLocation)
    registerBlockRemapping(Constants.BlockName.Robot,             RobotBlockLocation,           RobotItemLocation)
    registerBlockRemapping(Constants.BlockName.RobotAfterimage,   RobotAfterimageBlockLocation, null)

    registerDroneModel()
    registerTabletModel()
    registerTerminalModel()

    registerItemColors()
  }

  // Called from ClientProxy for each item that previously used registerModel(ItemLike, String).
  // In 1.20.1 item models are auto-loaded from assets, so this is a no-op.
  def registerModel(instance: ItemLike, id: String): Unit = {}

  // ── Dynamic item models ────────────────────────────────────────────────────

  private def registerDroneModel(): Unit = {
    val location = loc(Constants.ItemName.Drone, "inventory")
    withItem(Constants.ItemName.Drone) { item =>
      dynamicItems += item -> DynamicItemModel(_ => location, _.register(location))
    }
  }

  private def registerTabletModel(): Unit = {
    def tabletLoc(running: Option[Boolean]) = loc(
      Constants.ItemName.Tablet + (running match {
        case Some(true)  => "_on"
        case Some(false) => "_off"
        case _           => ""
      }),
      "inventory"
    )

    withItem(Constants.ItemName.Tablet) { item =>
      dynamicItems += item -> DynamicItemModel(
        stack => tabletLoc(Tablet.Client.getWeak(stack) match {
          case Some(t: TabletWrapper) => Some(t.data.isRunning)
          case _                      => None
        }),
        event => Seq(None, Some(true), Some(false)).foreach(s => event.register(tabletLoc(s)))
      )
    }
  }

  private def registerTerminalModel(): Unit = {
    def termLoc(hasServer: Boolean) =
      loc(Constants.ItemName.Terminal + (if (hasServer) "_on" else "_off"), "inventory")

    withItem(Constants.ItemName.Terminal) { item =>
      dynamicItems += item -> DynamicItemModel(
        stack => termLoc(stack.hasTag && stack.getTag.contains(Settings.namespace + "server")),
        event => Seq(true, false).foreach(s => event.register(termLoc(s)))
      )
    }
  }

  // ── Item colors ────────────────────────────────────────────────────────────

  private def registerItemColors(): Unit = {
    withItem(Constants.ItemName.Floppy) { item =>
      Minecraft.getInstance.getItemColors.register(
        (stack: ItemStack, tintIndex: Int) => {
          if (tintIndex == 1) {
            val color =
              if (stack.hasTag && stack.getTag.contains(Settings.namespace + "color"))
                stack.getTag.getInt(Settings.namespace + "color")
              else
                DyeColor.GRAY.getId

            val rgb = DyeColor.byId(color max 0 min 15).getTextureDiffuseColors

            val r = (rgb(0) * 255.0f).toInt
            val g = (rgb(1) * 255.0f).toInt
            val b = (rgb(2) * 255.0f).toInt

            (r << 16) | (g << 8) | b
          }
          else 0xFFFFFF
        },
        item
      )
    }
  }

  private def withItem(name: String)(f: Item => Unit): Unit =
    Option(api.Items.get(name)).map(_.item()).filter(_ != null).foreach(f)

  // ── Block model state remapping ────────────────────────────────────────────

  private def registerBlockRemapping(
                                      blockName:     String,
                                      blockLocation: ModelResourceLocation,
                                      itemLocation:  ModelResourceLocation
                                    ): Unit = {
    val descriptor = api.Items.get(blockName)
    if (descriptor == null) return

    if (itemLocation != null) {
      val stack = descriptor.createItemStack(1)
      if (!stack.isEmpty) {
        val shaper = Minecraft.getInstance.getItemRenderer.getItemModelShaper
        shaper.register(stack.getItem, itemLocation)
      }
    }

    if (blockLocation != null) {
      val block = descriptor.block()
      if (block != null)
        block.getStateDefinition.getPossibleStates.forEach { state =>
          modelRemappings += stateToModelLocation(state) -> blockLocation
        }
    }
  }

  private def stateToModelLocation(state: BlockState): ModelResourceLocation = {
    import scala.jdk.CollectionConverters._

    val blockKey = BuiltInRegistries.BLOCK.getKey(state.getBlock)

    val variant = state.getValues.entrySet().asScala.toSeq
      .sortBy(_.getKey.getName)
      .map(e => s"${e.getKey.getName}=${ModelInitializationHelper.getPropertyName(e.getKey, e.getValue)}")
      .mkString(",")

    new ModelResourceLocation(blockKey, if (variant.isEmpty) "normal" else variant)
  }

  // ── Event handlers ─────────────────────────────────────────────────────────

  @SubscribeEvent
  def onRegisterAdditionalModel(e: ModelEvent.RegisterAdditional): Unit =
    dynamicItems.foreach(_._2.bakeAdditional(e))

  @SubscribeEvent
  def onModifyBakingResult(e: ModelEvent.ModifyBakingResult): Unit = {
    val registry = e.getModels

    registry.put(CableBlockLocation,           CableModel)
    registry.put(CableItemLocation,            CableModel)
    registry.put(NetSplitterBlockLocation,     NetSplitterModel)
    registry.put(NetSplitterItemLocation,      NetSplitterModel)
    registry.put(PrintBlockLocation,           PrintModel)
    registry.put(PrintItemLocation,            PrintModel)
    registry.put(RobotBlockLocation,           NullModel)
    registry.put(RobotItemLocation,            RobotModel)
    registry.put(RobotAfterimageBlockLocation, NullModel)
    registry.put(loc(Constants.ItemName.Drone, "inventory"), DroneModel)

    for ((item, model) <- dynamicItems) {
      val originalLocation =
        new ModelResourceLocation(ForgeRegistries.ITEMS.getKey(item), "inventory")

      registry.get(originalLocation) match {
        case original: BakedModel =>
          val overrides = new ItemOverrides {
            override def resolve(
                                  base: BakedModel,
                                  stack: ItemStack,
                                  world: ClientLevel,
                                  holder: LivingEntity,
                                  seed: Int
                                ): BakedModel =
              registry.get(model.getLocation(stack)) match {
                case null => original
                case m    => m
              }
          }

          val fake = new SmartBlockModelBase {
            override def getQuads(
                                   state: BlockState,
                                   dir: Direction,
                                   rand: RandomSource
                                 ): java.util.List[net.minecraft.client.renderer.block.model.BakedQuad] =
              original.getQuads(state, dir, rand)

            override def getQuads(
                                   state: BlockState,
                                   dir: Direction,
                                   rand: RandomSource,
                                   data: ModelData,
                                   renderType: RenderType
                                 ): java.util.List[net.minecraft.client.renderer.block.model.BakedQuad] =
              original.getQuads(state, dir, rand, data, renderType)

            override def useAmbientOcclusion() = original.useAmbientOcclusion
            override def isGui3d()             = original.isGui3d
            override def usesBlockLight()      = original.usesBlockLight
            override def isCustomRenderer()    = original.isCustomRenderer

            @Deprecated
            override def getParticleIcon() = original.getParticleIcon

            @Deprecated
            override def getTransforms() = original.getTransforms

            override def getOverrides() = overrides
          }

          registry.put(originalLocation, fake)

        case _ =>
      }
    }

    val modelOverrides = Map[String, BakedModel => BakedModel](
      Constants.BlockName.ScreenTier1 -> (_ => ScreenModel),
      Constants.BlockName.ScreenTier2 -> (_ => ScreenModel),
      Constants.BlockName.ScreenTier3 -> (_ => ScreenModel),
      Constants.BlockName.ScreenTier4 -> (_ => ScreenModel),
      Constants.BlockName.Rack        -> (parent => new ServerRackModel(parent))
    )

    registry.keySet.toArray.foreach {
      case location: ModelResourceLocation =>
        for ((name, model) <- modelOverrides) {
          val pattern = s"^${Settings.resourceDomain}:$name#.*"
          if (location.toString.matches(pattern))
            registry.put(location, model(registry.get(location)))
        }

      case _ =>
    }

    for ((real, virtual) <- modelRemappings)
      registry.put(real, registry.get(virtual))
  }
}