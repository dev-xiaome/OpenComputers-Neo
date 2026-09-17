package net.minecraft.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public class StringUtil {
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private static final Pattern LINE_PATTERN = Pattern.compile("\\r\\n|\\v");
    private static final Pattern LINE_END_PATTERN = Pattern.compile("(?:\\r\\n|\\v)$");

    public static String formatTickDuration(int ticks, float ticksPerSecond) {
        int i = Mth.floor((float)ticks / ticksPerSecond);
        int j = i / 60;
        i %= 60;
        int k = j / 60;
        j %= 60;
        return k > 0 ? String.format(Locale.ROOT, "%02d:%02d:%02d", k, j, i) : String.format(Locale.ROOT, "%02d:%02d", j, i);
    }

    public static String stripColor(String text) {
        return STRIP_COLOR_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * Returns a value indicating whether the given string is null or empty.
     */
    public static boolean isNullOrEmpty(@Nullable String string) {
        return StringUtils.isEmpty(string);
    }

    public static String truncateStringIfNecessary(String string, int maxSize, boolean addEllipsis) {
        if (string.length() <= maxSize) {
            return string;
        } else {
            return addEllipsis && maxSize > 3 ? string.substring(0, maxSize - 3) + "..." : string.substring(0, maxSize);
        }
    }

    public static int lineCount(String string) {
        if (string.isEmpty()) {
            return 0;
        } else {
            Matcher matcher = LINE_PATTERN.matcher(string);
            int i = 1;

            while (matcher.find()) {
                i++;
            }

            return i;
        }
    }

    public static boolean endsWithNewLine(String string) {
        return LINE_END_PATTERN.matcher(string).find();
    }

    public static String trimChatMessage(String string) {
        return truncateStringIfNecessary(string, 256, false);
    }

    public static boolean isAllowedChatCharacter(char character) {
        return character != 167 && character >= ' ' && character != 127;
    }

    public static boolean isValidPlayerName(String playerName) {
        return playerName.length() > 16 ? false : playerName.chars().filter(p_332111_ -> p_332111_ <= 32 || p_332111_ >= 127).findAny().isEmpty();
    }

    public static String filterText(String text) {
        return filterText(text, false);
    }

    public static String filterText(String text, boolean allowLineBreaks) {
        StringBuilder stringbuilder = new StringBuilder();

        for (char c0 : text.toCharArray()) {
            if (isAllowedChatCharacter(c0)) {
                stringbuilder.append(c0);
            } else if (allowLineBreaks && c0 == '\n') {
                stringbuilder.append(c0);
            }
        }

        return stringbuilder.toString();
    }

    public static boolean isWhitespace(int character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    public static boolean isBlank(@Nullable String string) {
        return string != null && string.length() != 0 ? string.chars().allMatch(StringUtil::isWhitespace) : true;
    }
}
