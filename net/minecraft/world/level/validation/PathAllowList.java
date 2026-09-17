package net.minecraft.world.level.validation;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public class PathAllowList implements PathMatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COMMENT_PREFIX = "#";
    private final List<PathAllowList.ConfigEntry> entries;
    private final Map<String, PathMatcher> compiledPaths = new ConcurrentHashMap<>();

    public PathAllowList(List<PathAllowList.ConfigEntry> entries) {
        this.entries = entries;
    }

    public PathMatcher getForFileSystem(FileSystem fileSystem) {
        return this.compiledPaths.computeIfAbsent(fileSystem.provider().getScheme(), p_289958_ -> {
            List<PathMatcher> list;
            try {
                list = this.entries.stream().map(p_289937_ -> p_289937_.compile(fileSystem)).toList();
            } catch (Exception exception) {
                LOGGER.error("Failed to compile file pattern list", (Throwable)exception);
                return p_289987_ -> false;
            }
            return switch (list.size()) {
                case 0 -> p_289982_ -> false;
                case 1 -> (PathMatcher)list.get(0);
                default -> p_289927_ -> {
                for (PathMatcher pathmatcher : list) {
                    if (pathmatcher.matches(p_289927_)) {
                        return true;
                    }
                }

                return false;
            };
            };
        });
    }

    @Override
    public boolean matches(Path path) {
        return this.getForFileSystem(path.getFileSystem()).matches(path);
    }

    public static PathAllowList readPlain(BufferedReader reader) {
        return new PathAllowList(reader.lines().flatMap(p_289962_ -> PathAllowList.ConfigEntry.parse(p_289962_).stream()).toList());
    }

    public static record ConfigEntry(PathAllowList.EntryType type, String pattern) {
        public PathMatcher compile(FileSystem fileSystem) {
            return this.type().compile(fileSystem, this.pattern);
        }

        static Optional<PathAllowList.ConfigEntry> parse(String string) {
            if (string.isBlank() || string.startsWith("#")) {
                return Optional.empty();
            } else if (!string.startsWith("[")) {
                return Optional.of(new PathAllowList.ConfigEntry(PathAllowList.EntryType.PREFIX, string));
            } else {
                int i = string.indexOf(93, 1);
                if (i == -1) {
                    throw new IllegalArgumentException("Unterminated type in line '" + string + "'");
                } else {
                    String s = string.substring(1, i);
                    String s1 = string.substring(i + 1);

                    return switch (s) {
                        case "glob", "regex" -> Optional.of(new PathAllowList.ConfigEntry(PathAllowList.EntryType.FILESYSTEM, s + ":" + s1));
                        case "prefix" -> Optional.of(new PathAllowList.ConfigEntry(PathAllowList.EntryType.PREFIX, s1));
                        default -> throw new IllegalArgumentException("Unsupported definition type in line '" + string + "'");
                    };
                }
            }
        }

        static PathAllowList.ConfigEntry glob(String glob) {
            return new PathAllowList.ConfigEntry(PathAllowList.EntryType.FILESYSTEM, "glob:" + glob);
        }

        static PathAllowList.ConfigEntry regex(String regex) {
            return new PathAllowList.ConfigEntry(PathAllowList.EntryType.FILESYSTEM, "regex:" + regex);
        }

        static PathAllowList.ConfigEntry prefix(String prefix) {
            return new PathAllowList.ConfigEntry(PathAllowList.EntryType.PREFIX, prefix);
        }
    }

    @FunctionalInterface
    public interface EntryType {
        PathAllowList.EntryType FILESYSTEM = FileSystem::getPathMatcher;
        PathAllowList.EntryType PREFIX = (p_289949_, p_289938_) -> p_289955_ -> p_289955_.toString().startsWith(p_289938_);

        PathMatcher compile(FileSystem fileSystem, String pattern);
    }
}
