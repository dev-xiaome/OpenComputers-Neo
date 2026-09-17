package li.cil.oc.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * OpenComputers 的音效事件。
 * <p>
 * 1.7.10 直接按名字播放音效；1.21.1 需要把音效注册进 {@code SOUND_EVENT} 注册表，
 * 客户端才能解析到 {@code assets/opencomputers_neo/sounds.json} 里的定义。
 */
public final class SoundEvents {
    /** 与 li.cil.oc.OpenComputersNeo.MODID 保持一致（Java 侧不能引用 Scala 常量）。 */
    public static final String MOD_ID = "opencomputers_neo";

    public static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> COMPUTER_RUNNING = register("computer_running");

    public static final DeferredHolder<SoundEvent, SoundEvent> FLOPPY_ACCESS = register("floppy_access");

    public static final DeferredHolder<SoundEvent, SoundEvent> FLOPPY_EJECT = register("floppy_eject");

    public static final DeferredHolder<SoundEvent, SoundEvent> FLOPPY_INSERT = register("floppy_insert");

    public static final DeferredHolder<SoundEvent, SoundEvent> HDD_ACCESS = register("hdd_access");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTRY.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, name)));
    }

    /** 按 1.7.10 的音效名取事件，未知名字返回 {@code null}。 */
    public static SoundEvent byName(String name) {
        switch (name) {
            case "computer_running":
                return COMPUTER_RUNNING.get();
            case "floppy_access":
                return FLOPPY_ACCESS.get();
            case "floppy_eject":
                return FLOPPY_EJECT.get();
            case "floppy_insert":
                return FLOPPY_INSERT.get();
            case "hdd_access":
                return HDD_ACCESS.get();
            default:
                return null;
        }
    }

    private SoundEvents() {
    }
}
