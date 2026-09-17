package net.minecraft.data.structures;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.FastBufferedInputStream;
import org.slf4j.Logger;

public class NbtToSnbt implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Iterable<Path> inputFolders;
    private final PackOutput output;

    public NbtToSnbt(PackOutput output, Collection<Path> inputFolders) {
        this.inputFolders = inputFolders;
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path path = this.output.getOutputFolder();
        List<CompletableFuture<?>> list = new ArrayList<>();

        for (Path path1 : this.inputFolders) {
            list.add(
                CompletableFuture.<CompletableFuture>supplyAsync(
                        () -> {
                            try {
                                CompletableFuture completablefuture;
                                try (Stream<Path> stream = Files.walk(path1)) {
                                    completablefuture = CompletableFuture.allOf(
                                        stream.filter(p_126430_ -> p_126430_.toString().endsWith(".nbt"))
                                            .map(
                                                p_253418_ -> CompletableFuture.runAsync(
                                                        () -> convertStructure(output, p_253418_, getName(path1, p_253418_), path), Util.ioPool()
                                                    )
                                            )
                                            .toArray(CompletableFuture[]::new)
                                    );
                                }

                                return completablefuture;
                            } catch (IOException ioexception) {
                                LOGGER.error("Failed to read structure input directory", (Throwable)ioexception);
                                return CompletableFuture.completedFuture(null);
                            }
                        },
                        Util.backgroundExecutor()
                    )
                    .thenCompose(p_253420_ -> p_253420_)
            );
        }

        return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
    }

    @Override
    public final String getName() {
        return "NBT -> SNBT";
    }

    /**
     * Gets the name of the given NBT file, based on its path and the input directory. The result does not have the ".nbt" extension.
     */
    private static String getName(Path inputFolder, Path nbtPath) {
        String s = inputFolder.relativize(nbtPath).toString().replaceAll("\\\\", "/");
        return s.substring(0, s.length() - ".nbt".length());
    }

    @Nullable
    public static Path convertStructure(CachedOutput output, Path nbtPath, String name, Path directoryPath) {
        try {
            Path path1;
            try (
                InputStream inputstream = Files.newInputStream(nbtPath);
                InputStream inputstream1 = new FastBufferedInputStream(inputstream);
            ) {
                Path path = directoryPath.resolve(name + ".snbt");
                writeSnbt(output, path, NbtUtils.structureToSnbt(NbtIo.readCompressed(inputstream1, NbtAccounter.unlimitedHeap())));
                LOGGER.info("Converted {} from NBT to SNBT", name);
                path1 = path;
            }

            return path1;
        } catch (IOException ioexception) {
            LOGGER.error("Couldn't convert {} from NBT to SNBT at {}", name, nbtPath, ioexception);
            return null;
        }
    }

    public static void writeSnbt(CachedOutput output, Path path, String contents) throws IOException {
        ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
        HashingOutputStream hashingoutputstream = new HashingOutputStream(Hashing.sha1(), bytearrayoutputstream);
        hashingoutputstream.write(contents.getBytes(StandardCharsets.UTF_8));
        hashingoutputstream.write(10);
        output.writeIfNeeded(path, bytearrayoutputstream.toByteArray(), hashingoutputstream.hash());
    }
}
