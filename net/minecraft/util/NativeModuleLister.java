package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.logging.LogUtils;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Version;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.Tlhelp32.MODULEENTRY32W;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import net.minecraft.CrashReportCategory;
import org.slf4j.Logger;

public class NativeModuleLister {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LANG_MASK = 65535;
    private static final int DEFAULT_LANG = 1033;
    private static final int CODEPAGE_MASK = -65536;
    private static final int DEFAULT_CODEPAGE = 78643200;

    public static List<NativeModuleLister.NativeModuleInfo> listModules() {
        if (!Platform.isWindows()) {
            return ImmutableList.of();
        } else {
            int i = Kernel32.INSTANCE.GetCurrentProcessId();
            Builder<NativeModuleLister.NativeModuleInfo> builder = ImmutableList.builder();

            for (MODULEENTRY32W moduleentry32w : Kernel32Util.getModules(i)) {
                String s = moduleentry32w.szModule();
                Optional<NativeModuleLister.NativeModuleVersion> optional = tryGetVersion(moduleentry32w.szExePath());
                builder.add(new NativeModuleLister.NativeModuleInfo(s, optional));
            }

            return builder.build();
        }
    }

    private static Optional<NativeModuleLister.NativeModuleVersion> tryGetVersion(String filename) {
        try {
            IntByReference intbyreference = new IntByReference();
            int i = Version.INSTANCE.GetFileVersionInfoSize(filename, intbyreference);
            if (i == 0) {
                int i1 = Native.getLastError();
                if (i1 != 1813 && i1 != 1812) {
                    throw new Win32Exception(i1);
                } else {
                    return Optional.empty();
                }
            } else {
                Pointer pointer = new Memory((long)i);
                if (!Version.INSTANCE.GetFileVersionInfo(filename, 0, i, pointer)) {
                    throw new Win32Exception(Native.getLastError());
                } else {
                    IntByReference intbyreference1 = new IntByReference();
                    Pointer pointer1 = queryVersionValue(pointer, "\\VarFileInfo\\Translation", intbyreference1);
                    int[] aint = pointer1.getIntArray(0L, intbyreference1.getValue() / 4);
                    OptionalInt optionalint = findLangAndCodepage(aint);
                    if (optionalint.isEmpty()) {
                        return Optional.empty();
                    } else {
                        int j = optionalint.getAsInt();
                        int k = j & 65535;
                        int l = (j & -65536) >> 16;
                        String s = queryVersionString(pointer, langTableKey("FileDescription", k, l), intbyreference1);
                        String s1 = queryVersionString(pointer, langTableKey("CompanyName", k, l), intbyreference1);
                        String s2 = queryVersionString(pointer, langTableKey("FileVersion", k, l), intbyreference1);
                        return Optional.of(new NativeModuleLister.NativeModuleVersion(s, s2, s1));
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.info("Failed to find module info for {}", filename, exception);
            return Optional.empty();
        }
    }

    private static String langTableKey(String key, int lang, int codepage) {
        return String.format(Locale.ROOT, "\\StringFileInfo\\%04x%04x\\%s", lang, codepage, key);
    }

    private static OptionalInt findLangAndCodepage(int[] versionValue) {
        OptionalInt optionalint = OptionalInt.empty();

        for (int i : versionValue) {
            if ((i & -65536) == 78643200 && (i & 65535) == 1033) {
                return OptionalInt.of(i);
            }

            optionalint = OptionalInt.of(i);
        }

        return optionalint;
    }

    private static Pointer queryVersionValue(Pointer block, String subBlock, IntByReference size) {
        PointerByReference pointerbyreference = new PointerByReference();
        if (!Version.INSTANCE.VerQueryValue(block, subBlock, pointerbyreference, size)) {
            throw new UnsupportedOperationException("Can't get version value " + subBlock);
        } else {
            return pointerbyreference.getValue();
        }
    }

    private static String queryVersionString(Pointer block, String subBlock, IntByReference size) {
        try {
            Pointer pointer = queryVersionValue(block, subBlock, size);
            byte[] abyte = pointer.getByteArray(0L, (size.getValue() - 1) * 2);
            return new String(abyte, StandardCharsets.UTF_16LE);
        } catch (Exception exception) {
            return "";
        }
    }

    public static void addCrashSection(CrashReportCategory crashSection) {
        crashSection.setDetail(
            "Modules",
            () -> listModules()
                    .stream()
                    .sorted(Comparator.comparing(p_184685_ -> p_184685_.name))
                    .map(p_339486_ -> "\n\t\t" + p_339486_)
                    .collect(Collectors.joining())
        );
    }

    public static class NativeModuleInfo {
        public final String name;
        public final Optional<NativeModuleLister.NativeModuleVersion> version;

        public NativeModuleInfo(String name, Optional<NativeModuleLister.NativeModuleVersion> version) {
            this.name = name;
            this.version = version;
        }

        @Override
        public String toString() {
            return this.version.<String>map(p_339487_ -> this.name + ":" + p_339487_).orElse(this.name);
        }
    }

    public static class NativeModuleVersion {
        public final String description;
        public final String version;
        public final String company;

        public NativeModuleVersion(String description, String version, String company) {
            this.description = description;
            this.version = version;
            this.company = company;
        }

        @Override
        public String toString() {
            return this.description + ":" + this.version + ":" + this.company;
        }
    }
}
