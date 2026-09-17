package net.minecraft.util.eventlog;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public class EventLogDirectory {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int COMPRESS_BUFFER_SIZE = 4096;
    private static final String COMPRESSED_EXTENSION = ".gz";
    private final Path root;
    private final String extension;

    private EventLogDirectory(Path root, String extension) {
        this.root = root;
        this.extension = extension;
    }

    public static EventLogDirectory open(Path root, String extension) throws IOException {
        Files.createDirectories(root);
        return new EventLogDirectory(root, extension);
    }

    public EventLogDirectory.FileList listFiles() throws IOException {
        EventLogDirectory.FileList eventlogdirectory$filelist;
        try (Stream<Path> stream = Files.list(this.root)) {
            eventlogdirectory$filelist = new EventLogDirectory.FileList(
                stream.filter(p_262170_ -> Files.isRegularFile(p_262170_)).map(this::parseFile).filter(Objects::nonNull).toList()
            );
        }

        return eventlogdirectory$filelist;
    }

    @Nullable
    private EventLogDirectory.File parseFile(Path path) {
        String s = path.getFileName().toString();
        int i = s.indexOf(46);
        if (i == -1) {
            return null;
        } else {
            EventLogDirectory.FileId eventlogdirectory$fileid = EventLogDirectory.FileId.parse(s.substring(0, i));
            if (eventlogdirectory$fileid != null) {
                String s1 = s.substring(i);
                if (s1.equals(this.extension)) {
                    return new EventLogDirectory.RawFile(path, eventlogdirectory$fileid);
                }

                if (s1.equals(this.extension + ".gz")) {
                    return new EventLogDirectory.CompressedFile(path, eventlogdirectory$fileid);
                }
            }

            return null;
        }
    }

    static void tryCompress(Path path, Path outputPath) throws IOException {
        if (Files.exists(outputPath)) {
            throw new IOException("Compressed target file already exists: " + outputPath);
        } else {
            try (FileChannel filechannel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                FileLock filelock = filechannel.tryLock();
                if (filelock == null) {
                    throw new IOException("Raw log file is already locked, cannot compress: " + path);
                }

                writeCompressed(filechannel, outputPath);
                filechannel.truncate(0L);
            }

            Files.delete(path);
        }
    }

    private static void writeCompressed(ReadableByteChannel channel, Path outputPath) throws IOException {
        try (OutputStream outputstream = new GZIPOutputStream(Files.newOutputStream(outputPath))) {
            byte[] abyte = new byte[4096];
            ByteBuffer bytebuffer = ByteBuffer.wrap(abyte);

            while (channel.read(bytebuffer) >= 0) {
                bytebuffer.flip();
                outputstream.write(abyte, 0, bytebuffer.limit());
                bytebuffer.clear();
            }
        }
    }

    public EventLogDirectory.RawFile createNewFile(LocalDate date) throws IOException {
        int i = 1;
        Set<EventLogDirectory.FileId> set = this.listFiles().ids();

        EventLogDirectory.FileId eventlogdirectory$fileid;
        do {
            eventlogdirectory$fileid = new EventLogDirectory.FileId(date, i++);
        } while (set.contains(eventlogdirectory$fileid));

        EventLogDirectory.RawFile eventlogdirectory$rawfile = new EventLogDirectory.RawFile(
            this.root.resolve(eventlogdirectory$fileid.toFileName(this.extension)), eventlogdirectory$fileid
        );
        Files.createFile(eventlogdirectory$rawfile.path());
        return eventlogdirectory$rawfile;
    }

    public static record CompressedFile(Path path, EventLogDirectory.FileId id) implements EventLogDirectory.File {
        @Nullable
        @Override
        public Reader openReader() throws IOException {
            return !Files.exists(this.path) ? null : new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(this.path))));
        }

        @Override
        public EventLogDirectory.CompressedFile compress() {
            return this;
        }
    }

    public interface File {
        Path path();

        EventLogDirectory.FileId id();

        @Nullable
        Reader openReader() throws IOException;

        EventLogDirectory.CompressedFile compress() throws IOException;
    }

    public static record FileId(LocalDate date, int index) {
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

        @Nullable
        public static EventLogDirectory.FileId parse(String fileName) {
            int i = fileName.indexOf("-");
            if (i == -1) {
                return null;
            } else {
                String s = fileName.substring(0, i);
                String s1 = fileName.substring(i + 1);

                try {
                    return new EventLogDirectory.FileId(LocalDate.parse(s, DATE_FORMATTER), Integer.parseInt(s1));
                } catch (DateTimeParseException | NumberFormatException numberformatexception) {
                    return null;
                }
            }
        }

        @Override
        public String toString() {
            return DATE_FORMATTER.format(this.date) + "-" + this.index;
        }

        public String toFileName(String extension) {
            return this + extension;
        }
    }

    public static class FileList implements Iterable<EventLogDirectory.File> {
        private final List<EventLogDirectory.File> files;

        FileList(List<EventLogDirectory.File> files) {
            this.files = new ArrayList<>(files);
        }

        public EventLogDirectory.FileList prune(LocalDate date, int daysToKeep) {
            this.files.removeIf(p_261494_ -> {
                EventLogDirectory.FileId eventlogdirectory$fileid = p_261494_.id();
                LocalDate localdate = eventlogdirectory$fileid.date().plusDays((long)daysToKeep);
                if (!date.isBefore(localdate)) {
                    try {
                        Files.delete(p_261494_.path());
                        return true;
                    } catch (IOException ioexception) {
                        EventLogDirectory.LOGGER.warn("Failed to delete expired event log file: {}", p_261494_.path(), ioexception);
                    }
                }

                return false;
            });
            return this;
        }

        public EventLogDirectory.FileList compressAll() {
            ListIterator<EventLogDirectory.File> listiterator = this.files.listIterator();

            while (listiterator.hasNext()) {
                EventLogDirectory.File eventlogdirectory$file = listiterator.next();

                try {
                    listiterator.set(eventlogdirectory$file.compress());
                } catch (IOException ioexception) {
                    EventLogDirectory.LOGGER.warn("Failed to compress event log file: {}", eventlogdirectory$file.path(), ioexception);
                }
            }

            return this;
        }

        @Override
        public Iterator<EventLogDirectory.File> iterator() {
            return this.files.iterator();
        }

        public Stream<EventLogDirectory.File> stream() {
            return this.files.stream();
        }

        public Set<EventLogDirectory.FileId> ids() {
            return this.files.stream().map(EventLogDirectory.File::id).collect(Collectors.toSet());
        }
    }

    public static record RawFile(Path path, EventLogDirectory.FileId id) implements EventLogDirectory.File {
        public FileChannel openChannel() throws IOException {
            return FileChannel.open(this.path, StandardOpenOption.WRITE, StandardOpenOption.READ);
        }

        @Nullable
        @Override
        public Reader openReader() throws IOException {
            return Files.exists(this.path) ? Files.newBufferedReader(this.path) : null;
        }

        @Override
        public EventLogDirectory.CompressedFile compress() throws IOException {
            Path path = this.path.resolveSibling(this.path.getFileName().toString() + ".gz");
            EventLogDirectory.tryCompress(this.path, path);
            return new EventLogDirectory.CompressedFile(path, this.id);
        }
    }
}
