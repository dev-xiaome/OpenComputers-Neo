package li.cil.oc.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

import java.util.function.UnaryOperator;

/**
 * This must be a Java class as Scala object variables are <b>not</b>
 * compiled directly to Java static variables, but NeoForged requires
 * this to be a static field.
 */
public final class RarityExt {
    public static final EnumProxy<Rarity> LEGENDARY = new EnumProxy<>(Rarity.class, -1, "opencomputers:legendary", (UnaryOperator<Style>)(Style s) -> s.withColor(ChatFormatting.GOLD));

    private RarityExt() {}
}
