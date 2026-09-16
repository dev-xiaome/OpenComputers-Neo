package li.cil.oc.api;

import li.cil.oc.util.RarityExt;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * Allows grabbing OpenComputers shiny Legendary rarity.
 */
@ApiStatus.AvailableSince("1.9")
public final class LegendaryRarity {
    private LegendaryRarity() {}

    @ApiStatus.AvailableSince("1.9")
    public static final Rarity VALUE = LegendaryRarity.get();

    private static Rarity get() {
        try {
            Field field = LegendaryRarity.class.getClassLoader()
                    .loadClass("li.cil.oc.util.RarityExt")
                    .getField("LEGENDARY");
            return (Rarity) field.getType().getMethod("getValue").invoke(field.get(null));
        } catch (NoSuchFieldException |
                 ClassNotFoundException |
                 InvocationTargetException |
                 IllegalAccessException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
