package li.cil.oc.common.openprinter.printer;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class PrintJob {
    enum Kind { DOCUMENT, BOOK, LABEL, MAP }
    enum State { QUEUED, PRINTING, BLOCKED, COMPLETE, CANCELLED }

    final UUID id;
    final Kind kind;
    final PrintDocument document;
    final MapPrintImage mapImage;
    final String label;
    final String author;
    final boolean sign;
    final int copies;
    State state = State.QUEUED;
    String reason = "";
    int completedPages;
    int printTicks;

    private PrintJob(UUID id, Kind kind, PrintDocument document, MapPrintImage mapImage, String label,
                     String author, boolean sign, int copies) {
        this.id = id;
        this.kind = kind;
        this.document = document;
        this.mapImage = mapImage;
        this.label = label;
        this.author = author;
        this.sign = sign;
        this.copies = copies;
    }

    static PrintJob document(PrintDocument document, int copies) {
        return new PrintJob(UUID.randomUUID(), Kind.DOCUMENT, document, null, "", "", false, copies);
    }

    static PrintJob book(PrintDocument document, int copies, boolean sign, String author) {
        return new PrintJob(UUID.randomUUID(), Kind.BOOK, document, null, "", author, sign, copies);
    }

    static PrintJob label(String label, int copies) {
        return new PrintJob(UUID.randomUUID(), Kind.LABEL, null, null, label, "", false, copies);
    }

    static PrintJob map(MapPrintImage image, int copies) {
        return new PrintJob(UUID.randomUUID(), Kind.MAP, null, image, "", "", false, copies);
    }

    int pagesPerCopy() {
        return kind == Kind.DOCUMENT || kind == Kind.BOOK ? document.pageCount() : 1;
    }

    int totalPages() {
        return pagesPerCopy() * copies;
    }

    int pageIndex() {
        return completedPages % pagesPerCopy();
    }

    int copyIndex() {
        return completedPages / pagesPerCopy();
    }

    Map<String, Object> toLua() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id.toString());
        result.put("type", kind.name().toLowerCase());
        result.put("state", state.name().toLowerCase());
        result.put("reason", reason);
        result.put("pagesComplete", completedPages);
        result.put("pagesTotal", totalPages());
        result.put("copies", copies);
        result.put("copy", Math.min(copies, copyIndex() + 1));
        result.put("page", Math.min(pagesPerCopy(), pageIndex() + 1));
        double partialPage = Math.min(1.0, printTicks / (double) PrinterBlockEntity.PAGE_PRINT_TICKS);
        result.put("progress", totalPages() == 0 ? 100 : ((completedPages + partialPage) * 100.0) / totalPages());
        if (kind == Kind.DOCUMENT || kind == Kind.BOOK) {
            result.put("title", document.title());
            if (kind == Kind.BOOK) {
                result.put("signed", sign);
                result.put("author", author);
            }
        }
        else if (kind == Kind.LABEL) result.put("label", label);
        else {
            result.put("title", mapImage.title());
            result.put("width", mapImage.sourceWidth());
            result.put("height", mapImage.sourceHeight());
        }
        return result;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("kind", kind.name());
        if (document != null) tag.put("document", document.save());
        if (mapImage != null) tag.put("mapImage", mapImage.save());
        tag.putString("label", label);
        tag.putString("author", author);
        tag.putBoolean("sign", sign);
        tag.putInt("copies", copies);
        tag.putString("state", state.name());
        tag.putString("reason", reason);
        tag.putInt("completedPages", completedPages);
        tag.putInt("printTicks", printTicks);
        return tag;
    }

    static PrintJob load(CompoundTag tag) {
        Kind kind = enumValue(Kind.class, tag.getString("kind"), Kind.DOCUMENT);
        PrintDocument document = kind == Kind.DOCUMENT || kind == Kind.BOOK ? PrintDocument.load(tag.getCompound("document")) : null;
        MapPrintImage mapImage = kind == Kind.MAP ? MapPrintImage.load(tag.getCompound("mapImage")) : null;
        PrintJob job = new PrintJob(tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(), kind,
                document, mapImage, tag.getString("label"), tag.getString("author"), tag.getBoolean("sign"),
                Math.max(1, tag.getInt("copies")));
        job.state = enumValue(State.class, tag.getString("state"), State.QUEUED);
        job.reason = tag.getString("reason");
        job.completedPages = Math.max(0, tag.getInt("completedPages"));
        job.printTicks = Math.max(0, tag.getInt("printTicks"));
        return job;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }
}
