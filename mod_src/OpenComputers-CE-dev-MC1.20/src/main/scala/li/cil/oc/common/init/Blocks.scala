package li.cil.oc.common.init

import li.cil.oc.Constants
import li.cil.oc.CreativeTab
import li.cil.oc.Settings
import li.cil.oc.common.Tier
import li.cil.oc.common.block._
import li.cil.oc.util.{Rarity => OCRarity}
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.material.MapColor
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object Blocks {
  val BLOCKS: DeferredRegister[Block] = DeferredRegister.create(ForgeRegistries.BLOCKS, Settings.resourceDomain)

  def init(bus: IEventBus): Unit = {
    def defaultProps = Properties.of().mapColor(MapColor.METAL).strength(2, 5)
    def defaultItemProps = new Item.Properties()

    BLOCKS.register(Constants.BlockName.Adapter,           () => Items.registerBlock(new Adapter(defaultProps), Constants.BlockName.Adapter, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Assembler,         () => Items.registerBlock(new Assembler(defaultProps), Constants.BlockName.Assembler, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Cable,             () => Items.registerBlock(new Cable(defaultProps), Constants.BlockName.Cable, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Capacitor,         () => Items.registerBlock(new Capacitor(defaultProps), Constants.BlockName.Capacitor, defaultItemProps))
    BLOCKS.register(Constants.BlockName.CaseTier1,         () => Items.registerBlock(new Case(defaultProps, Tier.One), Constants.BlockName.CaseTier1, defaultItemProps))
    BLOCKS.register(Constants.BlockName.CaseTier2,         () => Items.registerBlock(new Case(defaultProps, Tier.Two), Constants.BlockName.CaseTier2, defaultItemProps.rarity(Rarity.UNCOMMON)))
    BLOCKS.register(Constants.BlockName.CaseTier3,         () => Items.registerBlock(new Case(defaultProps, Tier.Three), Constants.BlockName.CaseTier3, defaultItemProps.rarity(Rarity.RARE)))
    BLOCKS.register(Constants.BlockName.CaseTier4,         () => Items.registerBlock(new Case(defaultProps, Tier.Four), Constants.BlockName.CaseTier4, defaultItemProps.rarity(OCRarity.LEGENDARY)))
    BLOCKS.register(Constants.BlockName.ChameliumBlock,    () => Items.registerBlock(new ChameliumBlock(Properties.of().mapColor(MapColor.STONE).strength(2, 5)), Constants.BlockName.ChameliumBlock, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Charger,           () => Items.registerBlock(new Charger(defaultProps), Constants.BlockName.Charger, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Disassembler,      () => Items.registerBlock(new Disassembler(defaultProps), Constants.BlockName.Disassembler, defaultItemProps))
    BLOCKS.register(Constants.BlockName.DiskDrive,         () => Items.registerBlock(new DiskDrive(defaultProps), Constants.BlockName.DiskDrive, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Geolyzer,          () => Items.registerBlock(new Geolyzer(defaultProps), Constants.BlockName.Geolyzer, defaultItemProps))
    BLOCKS.register(Constants.BlockName.HologramTier1,     () => Items.registerBlock(new Hologram(defaultProps, Tier.One), Constants.BlockName.HologramTier1, defaultItemProps))
    BLOCKS.register(Constants.BlockName.HologramTier2,     () => Items.registerBlock(new Hologram(defaultProps, Tier.Two), Constants.BlockName.HologramTier2, defaultItemProps.rarity(Rarity.UNCOMMON)))
    BLOCKS.register(Constants.BlockName.HologramTier3,     () => Items.registerBlock(new Hologram(defaultProps, Tier.Three), Constants.BlockName.HologramTier3, defaultItemProps.rarity(Rarity.RARE)))
    BLOCKS.register(Constants.BlockName.HoloScreenTier1,   () => Items.registerBlock(new HoloScreen(defaultProps.noOcclusion, Tier.One), Constants.BlockName.HoloScreenTier1, defaultItemProps))
    BLOCKS.register(Constants.BlockName.HoloScreenTier2,   () => Items.registerBlock(new HoloScreen(defaultProps.noOcclusion, Tier.Two), Constants.BlockName.HoloScreenTier2, defaultItemProps.rarity(Rarity.UNCOMMON)))
    BLOCKS.register(Constants.BlockName.HoloScreenTier3,   () => Items.registerBlock(new HoloScreen(defaultProps.noOcclusion, Tier.Three), Constants.BlockName.HoloScreenTier3, defaultItemProps.rarity(Rarity.RARE)))
    BLOCKS.register(Constants.BlockName.HoloScreenTier4,   () => Items.registerBlock(new HoloScreen(defaultProps.noOcclusion, Tier.Four), Constants.BlockName.HoloScreenTier4, defaultItemProps.rarity(OCRarity.LEGENDARY)))
    BLOCKS.register(Constants.BlockName.Keyboard,          () => Items.registerBlock(new Keyboard(Properties.of().mapColor(MapColor.STONE).strength(2, 5).noOcclusion), Constants.BlockName.Keyboard, defaultItemProps))
    BLOCKS.register(Constants.BlockName.MotionSensor,      () => Items.registerBlock(new MotionSensor(defaultProps), Constants.BlockName.MotionSensor, defaultItemProps))
    BLOCKS.register(Constants.BlockName.PowerConverter,    () => Items.registerBlock(new PowerConverter(defaultProps), Constants.BlockName.PowerConverter, defaultItemProps))
    BLOCKS.register(Constants.BlockName.PowerDistributor,  () => Items.registerBlock(new PowerDistributor(defaultProps), Constants.BlockName.PowerDistributor, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Printer,           () => Items.registerBlock(new Printer(defaultProps), Constants.BlockName.Printer, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Raid,              () => Items.registerBlock(new Raid(defaultProps), Constants.BlockName.Raid, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Redstone,          () => Items.registerBlock(new Redstone(defaultProps), Constants.BlockName.Redstone, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Relay,             () => Items.registerBlock(new Relay(defaultProps), Constants.BlockName.Relay, defaultItemProps))
    BLOCKS.register(Constants.BlockName.ScreenTier1,       () => Items.registerBlock(new Screen(defaultProps, Tier.One), Constants.BlockName.ScreenTier1, defaultItemProps))
    BLOCKS.register(Constants.BlockName.ScreenTier2,       () => Items.registerBlock(new Screen(defaultProps, Tier.Two), Constants.BlockName.ScreenTier2, defaultItemProps.rarity(Rarity.UNCOMMON)))
    BLOCKS.register(Constants.BlockName.ScreenTier3,       () => Items.registerBlock(new Screen(defaultProps, Tier.Three), Constants.BlockName.ScreenTier3, defaultItemProps.rarity(Rarity.RARE)))
    BLOCKS.register(Constants.BlockName.ScreenTier4,       () => Items.registerBlock(new Screen(defaultProps, Tier.Four), Constants.BlockName.ScreenTier4, defaultItemProps.rarity(OCRarity.LEGENDARY)))
    BLOCKS.register(Constants.BlockName.FlatScreenBackTier1, () => Items.registerBlock(new FlatScreen(defaultProps.noOcclusion, Tier.One, true), Constants.BlockName.FlatScreenBackTier1, defaultItemProps))
    BLOCKS.register(Constants.BlockName.FlatScreenBackTier2, () => Items.registerBlock(new FlatScreen(defaultProps.noOcclusion, Tier.Two, true), Constants.BlockName.FlatScreenBackTier2, defaultItemProps.rarity(Rarity.UNCOMMON)))
    BLOCKS.register(Constants.BlockName.FlatScreenBackTier3, () => Items.registerBlock(new FlatScreen(defaultProps.noOcclusion, Tier.Three, true), Constants.BlockName.FlatScreenBackTier3, defaultItemProps.rarity(Rarity.RARE)))
    BLOCKS.register(Constants.BlockName.FlatScreenBackTier4, () => Items.registerBlock(new FlatScreen(defaultProps.noOcclusion, Tier.Four, true), Constants.BlockName.FlatScreenBackTier4, defaultItemProps.rarity(OCRarity.LEGENDARY)))
    BLOCKS.register(Constants.BlockName.FlatScreenFrontTier1, () => Items.registerBlock(new FlatScreen(defaultProps.noOcclusion, Tier.One, false), Constants.BlockName.FlatScreenFrontTier1, defaultItemProps))
    BLOCKS.register(Constants.BlockName.FlatScreenFrontTier2, () => Items.registerBlock(new FlatScreen(defaultProps.noOcclusion, Tier.Two, false), Constants.BlockName.FlatScreenFrontTier2, defaultItemProps.rarity(Rarity.UNCOMMON)))
    BLOCKS.register(Constants.BlockName.FlatScreenFrontTier3, () => Items.registerBlock(new FlatScreen(defaultProps.noOcclusion, Tier.Three, false), Constants.BlockName.FlatScreenFrontTier3, defaultItemProps.rarity(Rarity.RARE)))
    BLOCKS.register(Constants.BlockName.FlatScreenFrontTier4, () => Items.registerBlock(new FlatScreen(defaultProps.noOcclusion, Tier.Four, false), Constants.BlockName.FlatScreenFrontTier4, defaultItemProps.rarity(OCRarity.LEGENDARY)))
    BLOCKS.register(Constants.BlockName.Rack,              () => Items.registerBlock(new Rack(defaultProps), Constants.BlockName.Rack, defaultItemProps))
    BLOCKS.register(Constants.BlockName.Waypoint,          () => Items.registerBlock(new Waypoint(defaultProps), Constants.BlockName.Waypoint, defaultItemProps))

    BLOCKS.register(Constants.BlockName.CaseCreative,      () => Items.registerBlock(new Case(defaultProps, Tier.Five), Constants.BlockName.CaseCreative, defaultItemProps.rarity(Rarity.EPIC)))
    BLOCKS.register(Constants.BlockName.Microcontroller,   () => Items.registerBlock(new Microcontroller(defaultProps), Constants.BlockName.Microcontroller, new Item.Properties()))
    BLOCKS.register(Constants.BlockName.Print,             () => Items.registerBlock(new Print(Properties.of().mapColor(MapColor.METAL).strength(1, 5).noOcclusion.dynamicShape), Constants.BlockName.Print, new Item.Properties()))
    BLOCKS.register(Constants.BlockName.RobotAfterimage,   () => Items.registerBlockOnly(new RobotAfterimage(Properties.of().mapColor(MapColor.NONE).noCollission.instabreak.noOcclusion.dynamicShape), Constants.BlockName.RobotAfterimage))
    BLOCKS.register(Constants.BlockName.Robot,             () => Items.registerBlock(new RobotProxy(defaultProps.noOcclusion.dynamicShape), Constants.BlockName.Robot, new Item.Properties()))

    // v1.5.10
    BLOCKS.register(Constants.BlockName.Endstone,          () => Items.registerBlock(new FakeEndstone(Properties.of().mapColor(MapColor.STONE).strength(3, 15)), Constants.BlockName.Endstone, defaultItemProps))

    // v1.5.14
    BLOCKS.register(Constants.BlockName.NetSplitter,       () => Items.registerBlock(new NetSplitter(defaultProps), Constants.BlockName.NetSplitter, defaultItemProps))

    // v1.5.16
    BLOCKS.register(Constants.BlockName.Transposer,        () => Items.registerBlock(new Transposer(defaultProps), Constants.BlockName.Transposer, defaultItemProps))

    // v1.7.2
    BLOCKS.register(Constants.BlockName.CarpetedCapacitor, () => Items.registerBlock(new CarpetedCapacitor(defaultProps), Constants.BlockName.CarpetedCapacitor, defaultItemProps))

    BLOCKS.register(bus)
  }
}
