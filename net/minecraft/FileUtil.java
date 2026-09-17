package net.minecraft;

import com.mojang.serialization.DataResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

public class FileUtil {
    private static final Pattern COPY_COUNTER_PATTERN = Pattern.compile("(<name>.*) \\((<count>\\d*)\\)", 66);
    private static final int MAX_FILE_NAME = 255;
    private static final Pattern RESERVED_WINDOWS_FILENAMES = Pattern.compile(".*\\.|(?:COM|CLOCK\\$|CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?", 2);
    private static final Pattern RESERVED_WINDOWS_FILENAMES_NEOFORGE = Pattern.compile(".*\\.|(?:CON|PRN|AUX|NUL|CLOCK\\$|CONIN\\$|CONOUT\\$|(?:COM|LPT)[¹²³0-9])(?:\\..*)?", 2); // Neo: Fix MC-268617 - See PR #767
    private static final Pattern STRICT_PATH_SEGMENT_CHECK = Pattern.compile("[-._a-z0-9]+");

    public static String sanitizeName(String name) {
        for (char c0 : SharedConstants.ILLEGAL_FILE_CHARACTERS) {
            name = name.replace(c0, '_');
        }

        return name.replaceAll("[./\"]", "_");
    }

    public static String findAvailableName(Path dirPath, String fileName, String fileFormat) throws IOException {
        fileName = sanitizeName(fileName);
        if ((net.neoforged.neoforge.common.NeoForgeMod.getProperFilenameValidation() ? RESERVED_WINDOWS_FILENAMES_NEOFORGE : RESERVED_WINDOWS_FILENAMES).matcher(fileName).matches()) {
            fileName = "_" + fileName + "_";
        }

        Matcher matcher = COPY_COUNTER_PATTERN.matcher(fileName);
        int i = 0;
        if (matcher.matches()) {
            fileName = matcher.group("name");
            i = Integer.parseInt(matcher.group("count"));
        }

        if (fileName.length() > 255 - fileFormat.length()) {
            fileName = fileName.substring(0, 255 - fileFormat.length());
        }

        while (true) {
            String s = fileName;
            if (i != 0) {
                String s1 = " (" + i + ")";
                int j = 255 - s1.length();
                if (fileName.length() > j) {
                    s = fileName.substring(0, j);
                }

                s = s + s1;
            }

            s = s + fileFormat;
            Path path = dirPath.resolve(s);

            try {
                Path path1 = Files.createDirectory(path);
                Files.deleteIfExists(path1);
                return dirPath.relativize(path1).toString();
            } catch (FileAlreadyExistsException filealreadyexistsexception) {
                i++;
            }
        }
    }

    public static boolean isPathNormalized(Path p_path) {
        Path path = p_path.normalize();
        return path.equals(p_path);
    }

    public static boolean isPathPortable(Path p_path) {
        for (Path path : p_path) {
            if ((net.neoforged.neoforge.common.NeoForgeMod.getProperFilenameValidation() ? RESERVED_WINDOWS_FILENAMES_NEOFORGE : RESERVED_WINDOWS_FILENAMES).matcher(path.toString()).matches()) {
                return false;
            }
        }

        return true;
    }

    public static Path createPathToResource(Path dirPath, String locationPath, String fileFormat) {
        String s = locationPath + fileFormat;
        Path path = Paths.get(s);
        if (path.endsWith(fileFormat)) {
            throw new InvalidPathException(s, "empty resource name");
        } else {
            return dirPath.resolve(path);
        }
    }

    public static String getFullResourcePath(String path) {
        return FilenameUtils.getFullPath(path).replace(File.separator, "/");
    }

    public static String normalizeResourcePath(String path) {
        return FilenameUtils.normalize(path).replace(File.separator, "/");
    }

    public static DataResult<List<String>> decomposePath(String path) {
        int i = path.indexOf(47);
        if (i == -1) {
            return switch (path) {
                case "", ".", ".." -> DataResult.error(() -> "Invalid path '" + path + "'");
                default -> !isValidStrictPathSegment(path)
                ? DataResult.error(() -> "Invalid path '" + path + "'")
                : DataResult.success(List.of(path));
            };
        } else {
            List<String> list = new ArrayList<>();
            int j = 0;
            boolean flag = false;

            while (true) {
                String s = path.substring(j, i);
                switch (s) {
                    case "":
                    case ".":
                    case "..":
                        return DataResult.error(() -> "Invalid segment '" + s + "' in path '" + path + "'");
                }

                if (!isValidStrictPathSegment(s)) {
                    return DataResult.error(() -> "Invalid segment '" + s + "' in path '" + path + "'");
                }

                list.add(s);
                if (flag) {
                    return DataResult.success(list);
                }

                j = i + 1;
                i = path.indexOf(47, j);
                if (i == -1) {
                    i = path.length();
                    flag = true;
                }
            }
        }
    }

    public static Path resolvePath(Path path, List<String> subdirectories) {
        int i = subdirectories.size();

        return switch (i) {
            case 0 -> path;
            case 1 -> path.resolve(subdirectories.get(0));
            default -> {
                String[] astring = new String[i - 1];

                for (int j = 1; j < i; j++) {
                    astring[j - 1] = subdirectories.get(j);
                }

                yield path.resolve(path.getFileSystem().getPath(subdirectories.get(0), astring));
            }
        };
    }

    public static boolean isValidStrictPathSegment(String segment) {
        return STRICT_PATH_SEGMENT_CHECK.matcher(segment).matches();
    }

    public static void validatePath(String... elements) {
        if (elements.length == 0) {
            throw new IllegalArgumentException("Path must have at least one element");
        } else {
            for (String s : elements) {
                if (s.equals("..") || s.equals(".") || !isValidStrictPathSegment(s)) {
                    throw new IllegalArgumentException("Illegal segment " + s + " in path " + Arrays.toString((Object[])elements));
                }
            }
        }
    }

    public static void createDirectoriesSafe(Path path) throws IOException {
        Files.createDirectories(Files.exists(path) ? path.toRealPath() : path);
    }
}
