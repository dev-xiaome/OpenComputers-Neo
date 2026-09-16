package li.cil.oc.common.openprinter.printer;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PrinterConfig {
    public static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue ENABLE_NAME_TAGS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("printer");
        ENABLE_NAME_TAGS = builder.comment("Allow computers to print named name tags.")
                .define("enableNameTagPrinting", true);
        builder.pop();
        SPEC = builder.build();
    }

    private PrinterConfig() {}
}
