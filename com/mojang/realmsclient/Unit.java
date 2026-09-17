package com.mojang.realmsclient;

import java.util.Locale;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum Unit {
    B,
    KB,
    MB,
    GB;

    private static final int BASE_UNIT = 1024;

    public static Unit getLargest(long bytes) {
        if (bytes < 1024L) {
            return B;
        } else {
            try {
                int i = (int)(Math.log((double)bytes) / Math.log(1024.0));
                String s = String.valueOf("KMGTPE".charAt(i - 1));
                return valueOf(s + "B");
            } catch (Exception exception) {
                return GB;
            }
        }
    }

    public static double convertTo(long bytes, Unit unit) {
        return unit == B ? (double)bytes : (double)bytes / Math.pow(1024.0, (double)unit.ordinal());
    }

    public static String humanReadable(long bytes) {
        int i = 1024;
        if (bytes < 1024L) {
            return bytes + " B";
        } else {
            int j = (int)(Math.log((double)bytes) / Math.log(1024.0));
            String s = "KMGTPE".charAt(j - 1) + "";
            return String.format(Locale.ROOT, "%.1f %sB", (double)bytes / Math.pow(1024.0, (double)j), s);
        }
    }

    public static String humanReadable(long bytes, Unit unit) {
        return String.format(Locale.ROOT, "%." + (unit == GB ? "1" : "0") + "f %s", convertTo(bytes, unit), unit.name());
    }
}
