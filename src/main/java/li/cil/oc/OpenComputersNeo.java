package li.cil.oc;

import li.cil.oc.api.CreativeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * OpenComputers Neo 主入口类。
 * <p>
 * 原版 OpenComputers 使用 Scala 的 {@code @Mod} 注解，这里改为 Java 主类，
 * 由它驱动 Scala 侧的 {@link li.cil.oc.OpenComputers} 完成实际初始化。
 */
@Mod(OpenComputersNeo.MODID)
public class OpenComputersNeo {
    public static final String MODID = "open_computers_neo";
    public static final String NAME = "OpenComputers Neo";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(NAME);

    public OpenComputersNeo(IEventBus modBus, ModContainer container) {
        LOGGER.info("Greetings, user! Booting OpenComputers Neo.");

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);

        CreativeTab.register(modBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            File configFile = FMLPaths.CONFIGDIR.get().resolve("opencomputers_neo.conf").toFile();
            li.cil.oc.OpenComputers.loadSettings(configFile);
        });
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // 客户端初始化（渲染器、GUI 等）将在后续阶段接入。
    }
}
