package li.cil.oc.common.menu;

import li.cil.oc.OpenComputers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, OpenComputers.ID());

    public static final RegistryObject<MenuType<Adapter>> ADAPTER =
            MENU_TYPES.register("adapter", () -> IForgeMenuType.create(
                    (id, plr, buff) -> new Adapter(id, plr, new SimpleContainer(1))));

    public static final RegistryObject<MenuType<Assembler>> ASSEMBLER =
            MENU_TYPES.register("assembler", () -> IForgeMenuType.create(
                    (id, plr, buff) -> new Assembler(id, plr, new SimpleContainer(22))));

    public static final RegistryObject<MenuType<Case>> CASE =
            MENU_TYPES.register("case", () -> IForgeMenuType.create((id, plr, buff) -> {
                int invSize = buff.readVarInt();
                int tier = buff.readVarInt();
                return new Case(id, plr, new SimpleContainer(invSize), tier);
            }));

    public static final RegistryObject<MenuType<Charger>> CHARGER =
            MENU_TYPES.register("charger", () -> IForgeMenuType.create(
                    (id, plr, buff) -> new Charger(id, plr, new SimpleContainer(1))));

    public static final RegistryObject<MenuType<Database>> DATABASE =
            MENU_TYPES.register("database", () -> IForgeMenuType.create((id, plr, buff) -> {
                ItemStack containerStack = buff.readItem();
                int invSize = buff.readVarInt();
                int tier = buff.readVarInt();
                return new Database(id, plr, containerStack, new SimpleContainer(invSize), tier);
            }));

    public static final RegistryObject<MenuType<Disassembler>> DISASSEMBLER =
            MENU_TYPES.register("disassembler", () -> IForgeMenuType.create(
                    (id, plr, buff) -> new Disassembler(id, plr, new SimpleContainer(1))));

    public static final RegistryObject<MenuType<DiskDrive>> DISK_DRIVE =
            MENU_TYPES.register("disk_drive", () -> IForgeMenuType.create(
                    (id, plr, buff) -> new DiskDrive(id, plr, new SimpleContainer(1))));

    public static final RegistryObject<MenuType<HoloScreen>> HOLO_SCREEN =
            MENU_TYPES.register("holo_screen", () -> IForgeMenuType.create(
                    (id, plr, buff) -> new HoloScreen(id, plr, new SimpleContainer(1))));

    public static final RegistryObject<MenuType<Drone>> DRONE =
            MENU_TYPES.register("drone", () -> IForgeMenuType.create((id, plr, buff) -> {
                int invSize = buff.readVarInt();
                return new Drone(id, plr, new SimpleContainer(8), invSize);
            }));

    public static final RegistryObject<MenuType<Printer>> PRINTER =
            MENU_TYPES.register("printer", () -> IForgeMenuType.create(
                    (id, plr, buff) -> new Printer(id, plr, new SimpleContainer(3))));

    public static final RegistryObject<MenuType<Rack>> RACK =
            MENU_TYPES.register("rack", () -> IForgeMenuType.create(
                    (id, plr, buff) -> new Rack(id, plr, new SimpleContainer(4))));

    public static final RegistryObject<MenuType<Raid>> RAID =
            MENU_TYPES.register("raid", () -> IForgeMenuType.create(
                    (id, plr, buff) -> new Raid(id, plr, new SimpleContainer(3))));

    public static final RegistryObject<MenuType<Relay>> RELAY =
            MENU_TYPES.register("relay", () -> IForgeMenuType.create(
                    (id, plr, buff) -> new Relay(id, plr, new SimpleContainer(4))));

    public static final RegistryObject<MenuType<Robot>> ROBOT =
            MENU_TYPES.register("robot", () -> IForgeMenuType.create((id, plr, buff) -> {
                RobotInfo info = RobotInfo$.MODULE$.readRobotInfo(buff);
                return new Robot(id, plr, new SimpleContainer(100), info);
            }));

    public static final RegistryObject<MenuType<Server>> SERVER =
            MENU_TYPES.register("server", () -> IForgeMenuType.create((id, plr, buff) -> {
                ItemStack containerStack = buff.readItem();
                int invSize = buff.readVarInt();
                int tier = buff.readVarInt();
                int rackSlot = buff.readVarInt() - 1;
                return new Server(id, plr, containerStack, new SimpleContainer(invSize), tier, rackSlot);
            }));

    public static final RegistryObject<MenuType<Tablet>> TABLET =
            MENU_TYPES.register("tablet", () -> IForgeMenuType.create((id, plr, buff) -> {
                ItemStack containerStack = buff.readItem();
                int invSize = buff.readVarInt();
                String slot1 = buff.readUtf(32);
                int tier1 = buff.readVarInt();
                return new Tablet(id, plr, containerStack, new SimpleContainer(invSize), slot1, tier1);
            }));

    public static void openAdapterGui(ServerPlayer player, li.cil.oc.common.blockentity.Adapter adapter) {
        NetworkHooks.openScreen(player, adapter);
    }

    public static void openAssemblerGui(ServerPlayer player, li.cil.oc.common.blockentity.Assembler assembler) {
        NetworkHooks.openScreen(player, assembler);
    }

    public static void openCaseGui(ServerPlayer player, li.cil.oc.common.blockentity.Case computer) {
        NetworkHooks.openScreen(player, computer, buff -> {
            buff.writeVarInt(computer.getContainerSize());
            buff.writeVarInt(computer.tier());
        });
    }

    public static void openChargerGui(ServerPlayer player, li.cil.oc.common.blockentity.Charger charger) {
        NetworkHooks.openScreen(player, charger);
    }

    public static void openDatabaseGui(ServerPlayer player, li.cil.oc.common.container.DatabaseInventory database) {
        NetworkHooks.openScreen(player, database, buff -> {
            buff.writeItem(database.container());
            buff.writeVarInt(database.getContainerSize());
            buff.writeVarInt(database.tier());
        });
    }

    public static void openDisassemblerGui(ServerPlayer player, li.cil.oc.common.blockentity.Disassembler disassembler) {
        NetworkHooks.openScreen(player, disassembler);
    }

    public static void openDiskDriveGui(ServerPlayer player, li.cil.oc.common.blockentity.DiskDrive diskDrive) {
        NetworkHooks.openScreen(player, diskDrive);
    }

    public static void openDiskDriveGui(ServerPlayer player, li.cil.oc.server.component.DiskDriveMountable diskDrive) {
        NetworkHooks.openScreen(player, diskDrive);
    }

    public static void openDiskDriveGui(ServerPlayer player, li.cil.oc.common.container.DiskDriveMountableInventory diskDrive) {
        NetworkHooks.openScreen(player, diskDrive);
    }

    public static void openHoloScreenGui(ServerPlayer player, li.cil.oc.common.blockentity.HoloScreen screen) {
        NetworkHooks.openScreen(player, screen);
    }

    public static void openDroneGui(ServerPlayer player, li.cil.oc.common.entity.Drone drone) {
        NetworkHooks.openScreen(player, drone.containerProvider(), buff -> {
            buff.writeVarInt(drone.mainInventory().getContainerSize());
        });
    }

    public static void openPrinterGui(ServerPlayer player, li.cil.oc.common.blockentity.Printer printer) {
        NetworkHooks.openScreen(player, printer);
    }

    public static void openRackGui(ServerPlayer player, li.cil.oc.common.blockentity.Rack rack) {
        NetworkHooks.openScreen(player, rack);
    }

    public static void openRaidGui(ServerPlayer player, li.cil.oc.common.blockentity.Raid raid) {
        NetworkHooks.openScreen(player, raid);
    }

    public static void openRelayGui(ServerPlayer player, li.cil.oc.common.blockentity.Relay relay) {
        NetworkHooks.openScreen(player, relay);
    }

    public static void openRobotGui(ServerPlayer player, li.cil.oc.common.blockentity.Robot robot) {
        NetworkHooks.openScreen(player, robot, buff -> {
            RobotInfo$.MODULE$.writeRobotInfo(buff, new RobotInfo(robot));
        });
    }

    public static void openServerGui(ServerPlayer player, li.cil.oc.common.container.ServerInventory server, int rackSlot) {
        NetworkHooks.openScreen(player, server, buff -> {
            buff.writeItem(server.container());
            buff.writeVarInt(server.getContainerSize());
            buff.writeVarInt(server.tier());
            buff.writeVarInt(rackSlot + 1);
        });
    }

    public static void openTabletGui(ServerPlayer player, li.cil.oc.common.item.TabletWrapper tablet) {
        NetworkHooks.openScreen(player, tablet, buff -> {
            buff.writeItem(tablet.stack());
            buff.writeVarInt(tablet.getContainerSize());
            buff.writeUtf(tablet.containerSlotType(), 32);
            buff.writeVarInt(tablet.containerSlotTier());
        });
    }

    private MenuTypes() {}
}
