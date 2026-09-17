package net.minecraft.nbt;

import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.Util;
import net.minecraft.util.DelegateDataOutput;
import net.minecraft.util.FastBufferedInputStream;

public class NbtIo {
    private static final OpenOption[] SYNC_OUTPUT_OPTIONS = new OpenOption[]{
        StandardOpenOption.SYNC, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
    };

    public static CompoundTag readCompressed(Path path, NbtAccounter accounter) throws IOException {
        CompoundTag compoundtag;
        try (
            InputStream inputstream = Files.newInputStream(path);
            InputStream inputstream1 = new FastBufferedInputStream(inputstream);
        ) {
            compoundtag = readCompressed(inputstream1, accounter);
        }

        return compoundtag;
    }

    private static DataInputStream createDecompressorStream(InputStream zippedStream) throws IOException {
        return new DataInputStream(new FastBufferedInputStream(new GZIPInputStream(zippedStream)));
    }

    private static DataOutputStream createCompressorStream(OutputStream outputSteam) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(outputSteam)));
    }

    public static CompoundTag readCompressed(InputStream zippedStream, NbtAccounter accounter) throws IOException {
        CompoundTag compoundtag;
        try (DataInputStream datainputstream = createDecompressorStream(zippedStream)) {
            compoundtag = read(datainputstream, accounter);
        }

        return compoundtag;
    }

    public static void parseCompressed(Path path, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
        try (
            InputStream inputstream = Files.newInputStream(path);
            InputStream inputstream1 = new FastBufferedInputStream(inputstream);
        ) {
            parseCompressed(inputstream1, visitor, accounter);
        }
    }

    public static void parseCompressed(InputStream zippedStream, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
        try (DataInputStream datainputstream = createDecompressorStream(zippedStream)) {
            parse(datainputstream, visitor, accounter);
        }
    }

    public static void writeCompressed(CompoundTag compoundTag, Path path) throws IOException {
        try (
            OutputStream outputstream = Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS);
            OutputStream outputstream1 = new BufferedOutputStream(outputstream);
        ) {
            writeCompressed(compoundTag, outputstream1);
        }
    }

    /**
     * Writes and compresses a compound tag to a GNU zipped file.
     * @see #writeCompressed(CompoundTag, File)
     */
    public static void writeCompressed(CompoundTag compoundTag, OutputStream outputStream) throws IOException {
        try (DataOutputStream dataoutputstream = createCompressorStream(outputStream)) {
            write(compoundTag, dataoutputstream);
        }
    }

    public static void write(CompoundTag compoundTag, Path path) throws IOException {
        try (
            OutputStream outputstream = Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS);
            OutputStream outputstream1 = new BufferedOutputStream(outputstream);
            DataOutputStream dataoutputstream = new DataOutputStream(outputstream1);
        ) {
            write(compoundTag, dataoutputstream);
        }
    }

    @Nullable
    public static CompoundTag read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return null;
        } else {
            CompoundTag compoundtag;
            try (
                InputStream inputstream = Files.newInputStream(path);
                DataInputStream datainputstream = new DataInputStream(inputstream);
            ) {
                compoundtag = read(datainputstream, NbtAccounter.unlimitedHeap());
            }

            return compoundtag;
        }
    }

    /**
     * Reads a compound tag from a file. The size of the file can be infinite.
     */
    public static CompoundTag read(DataInput input) throws IOException {
        return read(input, NbtAccounter.unlimitedHeap());
    }

    /**
     * Reads a compound tag from a file. The size of the file is limited by the {@code accounter}.
     * @throws RuntimeException if the size of the file is larger than the maximum amount of bytes specified by the {@code accounter}
     */
    public static CompoundTag read(DataInput input, NbtAccounter accounter) throws IOException {
        Tag tag = readUnnamedTag(input, accounter);
        if (tag instanceof CompoundTag) {
            return (CompoundTag)tag;
        } else {
            throw new IOException("Root tag must be a named compound tag");
        }
    }

    public static void write(CompoundTag compoundTag, DataOutput output) throws IOException {
        writeUnnamedTagWithFallback(compoundTag, output);
    }

    public static void parse(DataInput input, StreamTagVisitor visitor, NbtAccounter accounter) throws IOException {
        TagType<?> tagtype = TagTypes.getType(input.readByte());
        if (tagtype == EndTag.TYPE) {
            if (visitor.visitRootEntry(EndTag.TYPE) == StreamTagVisitor.ValueResult.CONTINUE) {
                visitor.visitEnd();
            }
        } else {
            switch (visitor.visitRootEntry(tagtype)) {
                case HALT:
                default:
                    break;
                case BREAK:
                    StringTag.skipString(input);
                    tagtype.skip(input, accounter);
                    break;
                case CONTINUE:
                    StringTag.skipString(input);
                    tagtype.parse(input, visitor, accounter);
            }
        }
    }

    public static Tag readAnyTag(DataInput input, NbtAccounter accounter) throws IOException {
        byte b0 = input.readByte();
        return (Tag)(b0 == 0 ? EndTag.INSTANCE : readTagSafe(input, accounter, b0));
    }

    public static void writeAnyTag(Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            tag.write(output);
        }
    }

    public static void writeUnnamedTag(Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            output.writeUTF("");
            tag.write(output);
        }
    }

    public static void writeUnnamedTagWithFallback(Tag tag, DataOutput output) throws IOException {
        writeUnnamedTag(tag, new NbtIo.StringFallbackDataOutput(output));
    }

    private static Tag readUnnamedTag(DataInput input, NbtAccounter accounter) throws IOException {
        byte b0 = input.readByte();
        accounter.accountBytes(1); // Forge: Count everything!
        if (b0 == 0) {
            return EndTag.INSTANCE;
        } else {
            accounter.readUTF(input.readUTF()); //Forge: Count this string.
            accounter.accountBytes(4); //Forge: 4 extra bytes for the object allocation.
            return readTagSafe(input, accounter, b0);
        }
    }

    private static Tag readTagSafe(DataInput input, NbtAccounter accounter, byte type) {
        try {
            return TagTypes.getType(type).load(input, accounter);
        } catch (IOException ioexception) {
            CrashReport crashreport = CrashReport.forThrowable(ioexception, "Loading NBT data");
            CrashReportCategory crashreportcategory = crashreport.addCategory("NBT Tag");
            crashreportcategory.setDetail("Tag type", type);
            throw new ReportedNbtException(crashreport);
        }
    }

    public static class StringFallbackDataOutput extends DelegateDataOutput {
        public StringFallbackDataOutput(DataOutput p_312308_) {
            super(p_312308_);
        }

        @Override
        public void writeUTF(String p_312136_) throws IOException {
            try {
                super.writeUTF(p_312136_);
            } catch (UTFDataFormatException utfdataformatexception) {
                Util.logAndPauseIfInIde("Failed to write NBT String", utfdataformatexception);
                super.writeUTF("");
            }
        }
    }
}
