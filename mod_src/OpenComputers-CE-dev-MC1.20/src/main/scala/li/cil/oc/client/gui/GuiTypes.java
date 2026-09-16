package li.cil.oc.client.gui;

import li.cil.oc.common.menu.MenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class GuiTypes {
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent e) {
        // ScreenManager.register is not thread-safe.
        e.enqueueWork(() -> {
            MenuScreens.register(MenuTypes.ADAPTER.get(), Adapter::new);
            MenuScreens.register(MenuTypes.ASSEMBLER.get(), Assembler::new);
            MenuScreens.register(MenuTypes.CASE.get(), Case::new);
            MenuScreens.register(MenuTypes.CHARGER.get(), Charger::new);
            MenuScreens.register(MenuTypes.DATABASE.get(), Database::new);
            MenuScreens.register(MenuTypes.DISASSEMBLER.get(), Disassembler::new);
            MenuScreens.register(MenuTypes.DISK_DRIVE.get(), DiskDrive::new);
            MenuScreens.register(MenuTypes.HOLO_SCREEN.get(), HoloScreen::new);
            MenuScreens.register(MenuTypes.DRONE.get(), Drone::new);
            MenuScreens.register(MenuTypes.PRINTER.get(), Printer::new);
            MenuScreens.register(MenuTypes.RACK.get(), Rack::new);
            MenuScreens.register(MenuTypes.RAID.get(), Raid::new);
            MenuScreens.register(MenuTypes.RELAY.get(), Relay::new);
            MenuScreens.register(MenuTypes.ROBOT.get(), Robot::new);
            MenuScreens.register(MenuTypes.SERVER.get(), Server::new);
            MenuScreens.register(MenuTypes.TABLET.get(), Tablet::new);
        });
    }

    private GuiTypes() {
        throw new Error();
    }
}
