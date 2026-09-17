package net.minecraft.util;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import org.slf4j.Logger;

public class FileZipper implements Closeable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path outputFile;
    private final Path tempFile;
    private final FileSystem fs;

    public FileZipper(Path outputFile) {
        this.outputFile = outputFile;
        this.tempFile = outputFile.resolveSibling(outputFile.getFileName().toString() + "_tmp");

        try {
            this.fs = Util.ZIP_FILE_SYSTEM_PROVIDER.newFileSystem(this.tempFile, ImmutableMap.of("create", "true"));
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }
    }

    public void add(Path p_path, String filename) {
        try {
            Path path = this.fs.getPath(File.separator);
            Path path1 = path.resolve(p_path.toString());
            Files.createDirectories(path1.getParent());
            Files.write(path1, filename.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }
    }

    public void add(Path p_path, File filename) {
        try {
            Path path = this.fs.getPath(File.separator);
            Path path1 = path.resolve(p_path.toString());
            Files.createDirectories(path1.getParent());
            Files.copy(filename.toPath(), path1);
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }
    }

    public void add(Path p_path) {
        try {
            Path path = this.fs.getPath(File.separator);
            if (Files.isRegularFile(p_path)) {
                Path path3 = path.resolve(p_path.getParent().relativize(p_path).toString());
                Files.copy(path3, p_path);
            } else {
                try (Stream<Path> stream = Files.find(p_path, Integer.MAX_VALUE, (p_144707_, p_144708_) -> p_144708_.isRegularFile())) {
                    for (Path path1 : stream.collect(Collectors.toList())) {
                        Path path2 = path.resolve(p_path.relativize(path1).toString());
                        Files.createDirectories(path2.getParent());
                        Files.copy(path1, path2);
                    }
                }
            }
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }
    }

    @Override
    public void close() {
        try {
            this.fs.close();
            Files.move(this.tempFile, this.outputFile);
            LOGGER.info("Compressed to {}", this.outputFile);
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }
    }
}
