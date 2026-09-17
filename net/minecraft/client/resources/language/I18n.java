package net.minecraft.client.resources.language;

import java.util.IllegalFormatException;
import net.minecraft.locale.Language;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class I18n {
    private static volatile Language language = Language.getInstance();

    private I18n() {
    }

    static void setLanguage(Language p_language) {
        language = p_language;
        net.neoforged.fml.i18n.I18nManager.injectTranslations(p_language.getLanguageData());
    }

    /**
     * Translates the given string and then formats it. Equivalent to {@code String.format(translate(key), parameters)}.
     */
    public static String get(String translateKey, Object... parameters) {
        String s = language.getOrDefault(translateKey);

        try {
            return String.format(s, parameters);
        } catch (IllegalFormatException illegalformatexception) {
            return "Format error: " + s;
        }
    }

    public static boolean exists(String key) {
        return language.has(key);
    }
}
