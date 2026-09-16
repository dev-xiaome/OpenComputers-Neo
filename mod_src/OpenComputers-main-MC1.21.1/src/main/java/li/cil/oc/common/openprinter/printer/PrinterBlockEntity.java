package li.cil.oc.common.openprinter.printer;

import li.cil.oc.common.openprinter.OpenPrinter;
import li.cil.oc.common.openprinter.block.DeviceBlock;
import li.cil.oc.common.openprinter.blockentity.InventoryDevice;
import li.cil.oc.common.openprinter.menu.PortableInventory;

import li.cil.oc.api.Network;
import li.cil.oc.api.UnrecoverablePersistanceException;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.BlockEntityEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PrinterBlockEntity extends BlockEntityEnvironment implements InventoryDevice {
    private static final int BLACK_INK_SLOT = 0;
    private static final int COLOR_INK_SLOT = 1;
    private static final int PAPER_SLOT = 2;
    private static final int SCANNER_SLOT = 3;
    private static final int OUTPUT_START = 4;
    private static final int OUTPUT_END = 13;
    private static final int MAX_QUEUE = 32;
    private static final int MAX_COPIES = 64;
    private static final int MAX_JOB_PAGES = 256;
    static final int PAGE_PRINT_TICKS = 40;
    private static final int HISTORY_LIMIT = 16;
    private static final double ENERGY_PER_PAGE = 1.0;
    private static final int MAP_COLOR_INK_COST = 20;
    private static final double FORMAT_VERSION = 2.0;
    private static final String OC_NODE_TAG = "oc:node";

    private final ItemStackHandler inventory = new ItemStackHandler(13) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case BLACK_INK_SLOT -> stack.is(OpenPrinter.BLACK_INK.get());
                case COLOR_INK_SLOT -> stack.is(OpenPrinter.COLOR_INK.get());
                case PAPER_SLOT -> stack.is(Items.PAPER) || stack.is(Items.NAME_TAG) || stack.is(Items.MAP)
                        || stack.is(Items.WRITABLE_BOOK);
                case SCANNER_SLOT -> stack.is(OpenPrinter.PRINTED_PAGE.get())
                        || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK);
                default -> stack.is(OpenPrinter.PRINTED_PAGE.get()) || stack.is(OpenPrinter.FOLDER.get())
                        || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK)
                        || stack.is(Items.FILLED_MAP);
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final Deque<PrintJob> jobs = new ArrayDeque<>();
    private final Deque<PrintJob> history = new ArrayDeque<>();
    private final int[] syncedMenuData = new int[6];
    private String lastDisplayedReason = "";
    private UUID printerId = UUID.randomUUID();

    // Compatibility buffer for the classic OpenPrinter API. New code should
    // prefer print(document, options), but old programs can continue to use
    // setTitle/writeln/clear/print(copies).
    private final List<PrintDocument.Line> legacyLines = new ArrayList<>();
    private String legacyTitle = "";

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(OpenPrinter.PRINTER_BE.get(), pos, state);
        node = Network.newNode(this, Visibility.Network)
                .withComponent("openprinter", Visibility.Network)
                .withConnector(32).create();
    }

    @Override
    public ItemStackHandler inventory() {
        return inventory;
    }

    @Override
    public IItemHandler itemHandler(@Nullable Direction side) {
        return inventory;
    }

    @Override
    public BlockPos devicePosition() {
        return worldPosition;
    }

    @Override
    public DeviceBlock.Kind deviceKind() {
        return DeviceBlock.Kind.PRINTER;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.openprinter.printer");
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (level == null || level.isClientSide) return syncedMenuData[index];
                PrintJob job = jobs.peekFirst();
                return switch (index) {
                    case 0 -> jobs.size();
                    case 1 -> stateCode(job);
                    case 2 -> job == null ? 0 : Math.min(job.totalPages(), job.completedPages + 1);
                    case 3 -> job == null ? 0 : job.totalPages();
                    case 4 -> job == null ? 0 : Math.min(100, job.printTicks * 100 / PAGE_PRINT_TICKS);
                    case 5 -> reasonCode(job == null ? "" : job.reason);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                syncedMenuData[index] = value;
            }

            @Override
            public int getCount() {
                return syncedMenuData.length;
            }
        };
    }

    private static int stateCode(PrintJob job) {
        if (job == null) return 0;
        return switch (job.state) {
            case QUEUED -> 1;
            case PRINTING -> 2;
            case BLOCKED -> 3;
            default -> 0;
        };
    }

    private static int reasonCode(String reason) {
        return switch (reason) {
            case "out of paper" -> 1;
            case "out of name tags" -> 2;
            case "out of black ink" -> 3;
            case "out of color ink" -> 4;
            case "output tray full" -> 5;
            case "no power" -> 6;
            case "out of empty maps" -> 7;
            case "out of writable books" -> 8;
            default -> 0;
        };
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("inventory", inventory.serializeNBT(provider));
        tag.putUUID("printerUUID", printerId);
        ListTag savedJobs = new ListTag();
        for (PrintJob job : jobs) savedJobs.add(job.save());
        tag.put("printJobs", savedJobs);
        ListTag savedHistory = new ListTag();
        for (PrintJob job : history) savedHistory.add(job.save());
        tag.put("printHistory", savedHistory);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (node != null && node.host() == this && tag.contains(OC_NODE_TAG, Tag.TAG_COMPOUND)) {
            try {
                node.loadData(tag.getCompound(OC_NODE_TAG), provider);
            } catch (UnrecoverablePersistanceException exception) {
                OpenPrinter.LOGGER.warn("Could not restore the printer's OpenComputers component address", exception);
            }
        }
        if (tag.contains("inventory")) inventory.deserializeNBT(provider, tag.getCompound("inventory"));
        if (tag.hasUUID("printerUUID")) printerId = tag.getUUID("printerUUID");
        jobs.clear();
        ListTag savedJobs = tag.getList("printJobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < savedJobs.size(); i++) {
            PrintJob job = PrintJob.load(savedJobs.getCompound(i));
            if (job.state == PrintJob.State.PRINTING || job.state == PrintJob.State.BLOCKED) job.state = PrintJob.State.QUEUED;
            jobs.addLast(job);
        }
        history.clear();
        ListTag savedHistory = tag.getList("printHistory", Tag.TAG_COMPOUND);
        for (int i = 0; i < savedHistory.size(); i++) history.addLast(PrintJob.load(savedHistory.getCompound(i)));
    }

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        PrintJob job = jobs.peekFirst();
        if (job == null) return;

        String blocker = blocker(job);
        if (!blocker.isEmpty()) {
            transition(job, PrintJob.State.BLOCKED, blocker);
            return;
        }
        transition(job, PrintJob.State.PRINTING, "");
        job.printTicks++;
        if (job.printTicks % 20 == 0) setChanged();
        if (job.printTicks < PAGE_PRINT_TICKS) return;

        if (!(node instanceof Connector connector) || !connector.tryChangeBuffer(-ENERGY_PER_PAGE)) {
            transition(job, PrintJob.State.BLOCKED, "no power");
            return;
        }
        printPhysicalPage(job);
        job.completedPages++;
        job.printTicks = 0;
        setChanged();
        if (job.completedPages >= job.totalPages()) {
            jobs.removeFirst();
            transition(job, PrintJob.State.COMPLETE, "");
            remember(job);
        }
    }

    private String blocker(PrintJob job) {
        if (job.kind == PrintJob.Kind.DOCUMENT) {
            if (!hasPrintedPageOutputSpace()) return "output tray full";
        } else if (job.kind != PrintJob.Kind.BOOK && emptyOutputSlot() < 0) {
            return "output tray full";
        }
        if (job.kind == PrintJob.Kind.LABEL) {
            if (!inventory.getStackInSlot(PAPER_SLOT).is(Items.NAME_TAG)) return "out of name tags";
            if (inkLevel(BLACK_INK_SLOT) < 1) return "out of black ink";
        } else if (job.kind == PrintJob.Kind.BOOK) {
            if (!inventory.getStackInSlot(PAPER_SLOT).is(Items.WRITABLE_BOOK)) return "out of writable books";
            int[] cost = inkCost(job.document.page(job.pageIndex()));
            if (inkLevel(BLACK_INK_SLOT) < cost[0]) return "out of black ink";
            if (job.completedPages + 1 >= job.totalPages() && emptyOutputSlot() < 0) return "output tray full";
        } else if (job.kind == PrintJob.Kind.MAP) {
            if (!inventory.getStackInSlot(PAPER_SLOT).is(Items.MAP)) return "out of empty maps";
            if (inkLevel(COLOR_INK_SLOT) < MAP_COLOR_INK_COST) return "out of color ink";
        } else {
            if (!inventory.getStackInSlot(PAPER_SLOT).is(Items.PAPER)) return "out of paper";
            int[] cost = inkCost(job.document.page(job.pageIndex()));
            if (inkLevel(BLACK_INK_SLOT) < cost[0]) return "out of black ink";
            if (inkLevel(COLOR_INK_SLOT) < cost[1]) return "out of color ink";
        }
        if (!(node instanceof Connector connector) || connector.globalBuffer() < ENERGY_PER_PAGE) return "no power";
        return "";
    }

    private void printPhysicalPage(PrintJob job) {
        int outputSlot = emptyOutputSlot();
        if (job.kind == PrintJob.Kind.LABEL) {
            ItemStack output = new ItemStack(Items.NAME_TAG);
            output.set(DataComponents.CUSTOM_NAME, Component.literal(job.label));
            consumeInk(BLACK_INK_SLOT, 1);
            consumePaper();
            inventory.setStackInSlot(outputSlot, output);
            return;
        }
        if (job.kind == PrintJob.Kind.MAP) {
            ItemStack output = MapItem.create(level, worldPosition.getX(), worldPosition.getZ(), (byte) 0, false, false);
            MapId mapId = output.get(DataComponents.MAP_ID);
            MapItemSavedData mapData = MapItem.getSavedData(output, level);
            if (mapId == null || mapData == null) throw new IllegalStateException("could not allocate printed map data");
            System.arraycopy(job.mapImage.colors(), 0, mapData.colors, 0, mapData.colors.length);
            level.setMapData(mapId, mapData.locked());
            if (!job.mapImage.title().isEmpty()) output.set(DataComponents.CUSTOM_NAME, Component.literal(job.mapImage.title()));
            consumeInk(COLOR_INK_SLOT, MAP_COLOR_INK_COST);
            consumePaper();
            inventory.setStackInSlot(outputSlot, output);
            return;
        }

        if (job.kind == PrintJob.Kind.BOOK) {
            ItemStack input = inventory.getStackInSlot(PAPER_SLOT);
            if (!input.is(Items.WRITABLE_BOOK)) throw new IllegalStateException("writable book disappeared");
            boolean finalPage = job.completedPages + 1 >= job.totalPages();
            int bookOutputSlot = finalPage ? emptyOutputSlot() : -1;
            if (finalPage && bookOutputSlot < 0) throw new IllegalStateException("output tray full");
            WritableBookContent content = input.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
            List<Filterable<String>> pages = new ArrayList<>(content.pages());
            if (pages.size() >= WritableBookContent.MAX_PAGES) throw new IllegalStateException("book is full");
            pages.add(Filterable.passThrough(bookPage(job.document.page(job.pageIndex()))));
            input.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));
            int[] cost = inkCost(job.document.page(job.pageIndex()));
            consumeInk(BLACK_INK_SLOT, cost[0]);
            if (finalPage) {
                ItemStack output = input.copy();
                inventory.setStackInSlot(PAPER_SLOT, ItemStack.EMPTY);
                inventory.setStackInSlot(bookOutputSlot, job.sign
                        ? signedBook(pages, job.document.title(), job.author)
                        : output);
            }
            return;
        }

        List<PrintDocument.Line> lines = job.document.page(job.pageIndex());
        ItemStack output = new ItemStack(OpenPrinter.PRINTED_PAGE.get());
        CompoundTag data = new CompoundTag();
        data.putDouble("version", FORMAT_VERSION);
        if (!job.document.title().isEmpty()) {
            data.putString("pageTitle", job.document.title());
            output.set(DataComponents.CUSTOM_NAME, Component.literal(job.document.title()));
        }
        for (int i = 0; i < lines.size(); i++) {
            PrintDocument.Line line = lines.get(i);
            data.putString("line" + i, line.text() + "\u221e" + line.color() + "\u221e" + line.alignment());
        }
        data.putUUID("printerUUID", printerId);
        data.putUUID("pageUUID", UUID.randomUUID());
        data.putInt("documentPage", job.pageIndex() + 1);
        data.putInt("documentPages", job.pagesPerCopy());
        data.putInt("copy", job.copyIndex() + 1);
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        int[] cost = inkCost(lines);
        consumeInk(BLACK_INK_SLOT, cost[0]);
        consumeInk(COLOR_INK_SLOT, cost[1]);
        consumePaper();
        storePrintedPage(output);
    }

    private static int[] inkCost(List<PrintDocument.Line> lines) {
        int black = 0;
        int color = 0;
        for (PrintDocument.Line line : lines) {
            if (line.text().isEmpty()) continue;
            if (line.color() == 0) black++;
            else color++;
            if (line.text().matches(".*\u00a7[0-9a-fA-F].*")) color++;
        }
        return new int[]{black, color};
    }

    private boolean hasWritableBook() {
        return inventory.getStackInSlot(PAPER_SLOT).is(Items.WRITABLE_BOOK);
    }

    private static void validateBook(PrintDocument document) {
        for (int page = 0; page < document.pageCount(); page++) {
            for (PrintDocument.Line line : document.page(page)) {
                if (line.color() != 0 || line.text().indexOf('\u00a7') >= 0) {
                    throw new IllegalArgumentException("books only support plain black text");
                }
            }
        }
    }

    private static int writableBookPages(ItemStack book) {
        WritableBookContent content = book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
        return content.pages().size();
    }

    private PrintJob documentJob(PrintDocument document, int copies) {
        if (!hasWritableBook()) return PrintJob.document(document, copies);
        validateBook(document);
        if (writableBookPages(inventory.getStackInSlot(PAPER_SLOT)) + document.pageCount() * copies > WritableBookContent.MAX_PAGES) {
            throw new IllegalArgumentException("book is full");
        }
        return PrintJob.book(document, copies, false, "OpenPrinter");
    }

    private static String bookPage(List<PrintDocument.Line> lines) {
        StringBuilder page = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) page.append('\n');
            page.append(lines.get(i).text());
        }
        return page.toString();
    }

    private static ItemStack signedBook(List<Filterable<String>> pages, String title, String author) {
        List<Filterable<Component>> signedPages = new ArrayList<>(pages.size());
        for (Filterable<String> page : pages) signedPages.add(Filterable.passThrough(Component.literal(page.get(false))));
        ItemStack output = new ItemStack(Items.WRITTEN_BOOK);
        output.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), author, 0, signedPages, true));
        return output;
    }

    private void transition(PrintJob job, PrintJob.State state, String reason) {
        if (job.state == state && job.reason.equals(reason)) return;
        job.state = state;
        job.reason = reason;
        lastDisplayedReason = reason;
        setChanged();
        if (node != null) {
            node.sendToReachable("computer.signal", "openprinter_job", node.address(), job.id.toString(),
                    state.name().toLowerCase(), reason);
        }
    }

    private void remember(PrintJob job) {
        history.addFirst(job);
        while (history.size() > HISTORY_LIMIT) history.removeLast();
    }

    private PrintDocument legacyDocument() {
        return legacyDocument(legacyTitle);
    }

    private PrintDocument legacyDocument(String title) {
        Map<Integer, Object> lineTable = new LinkedHashMap<>();
        for (int i = 0; i < legacyLines.size(); i++) {
            PrintDocument.Line line = legacyLines.get(i);
            Map<String, Object> encoded = new LinkedHashMap<>();
            encoded.put("text", line.text());
            encoded.put("color", line.color());
            encoded.put("alignment", line.alignment());
            lineTable.put(i + 1, encoded);
        }
        Map<String, Object> documentValue = new LinkedHashMap<>();
        documentValue.put("title", title);
        documentValue.put("lines", lineTable);
        return PrintDocument.parse(documentValue, Map.of());
    }

    @Callback(direct = true, doc = "function():string -- Verifies that the printer component is available.")
    public Object[] greet(Context context, Arguments args) {
        return new Object[]{"Lasciate ogne speranza, voi ch'intrate"};
    }

    @Callback(doc = "function(document:string|table[, options:table]):string, string -- Queues a document. Classic print([copies]) is also supported for the writeln buffer.")
    public Object[] print(Context context, Arguments args) {
        // Classic OpenPrinter: print([copyCount]) prints the buffered page and
        // returns a boolean. This signature does not collide with modern calls,
        // whose first argument must be a string or table.
        if (args.count() == 0 || args.isInteger(0)) {
            if (jobs.size() >= MAX_QUEUE) return new Object[]{false, "print queue full"};
            int copies = args.count() == 0 ? 1 : Math.max(1, Math.min(MAX_COPIES, args.checkInteger(0)));
            PrintDocument document = legacyDocument();
            if ((long) document.pageCount() * copies > MAX_JOB_PAGES) {
                return new Object[]{false, "job exceeds 256 physical pages"};
            }
            PrintJob job = documentJob(document, copies);
            jobs.addLast(job);
            legacyLines.clear();
            legacyTitle = "";
            setChanged();
            signalQueued(job);
            return new Object[]{true};
        }

        if (jobs.size() >= MAX_QUEUE) return new Object[]{null, "print queue full"};
        Object documentValue = args.isString(0) ? args.checkString(0) : args.checkTable(0);
        Map<?, ?> options = args.count() > 1 ? args.checkTable(1) : Map.of();
        int copies = copies(options);
        PrintDocument document = PrintDocument.parse(documentValue, options);
        if ((long) document.pageCount() * copies > MAX_JOB_PAGES) return new Object[]{null, "job exceeds 256 physical pages"};
        PrintJob job = documentJob(document, copies);
        jobs.addLast(job);
        setChanged();
        signalQueued(job);
        return new Object[]{job.id.toString()};
    }

    @Callback(doc = "function(title:string[, author:string][, copies:number]):string, string -- Queues the classic buffer into a writable book and seals it.")
    public Object[] printAndSign(Context context, Arguments args) {
        if (jobs.size() >= MAX_QUEUE) return new Object[]{null, "print queue full"};
        if (!hasWritableBook()) return new Object[]{null, "a writable book is required"};
        String title = args.checkString(0);
        String author = "OpenPrinter";
        int copies = 1;
        if (args.count() > 1) {
            if (args.isString(1)) author = args.checkString(1);
            else copies = args.checkInteger(1);
        }
        if (args.count() > 2) copies = args.checkInteger(2);
        if (title.isEmpty()) return new Object[]{null, "book title cannot be empty"};
        if (title.length() > WrittenBookContent.TITLE_MAX_LENGTH) {
            return new Object[]{null, "book title is too long"};
        }
        if (author.isEmpty()) return new Object[]{null, "book author cannot be empty"};
        if (copies < 1 || copies > MAX_COPIES) return new Object[]{null, "invalid copy count"};

        PrintDocument document = legacyDocument(title);
        validateBook(document);
        if (writableBookPages(inventory.getStackInSlot(PAPER_SLOT)) + document.pageCount() * copies > WritableBookContent.MAX_PAGES) {
            return new Object[]{null, "book is full"};
        }
        PrintJob job = PrintJob.book(document, copies, true, limit(author, 64));
        jobs.addLast(job);
        legacyLines.clear();
        legacyTitle = "";
        setChanged();
        signalQueued(job);
        return new Object[]{job.id.toString()};
    }

    @Callback(doc = "function(text:string[, color:number|string[, alignment:string]]):boolean -- Appends a line to the classic page buffer.")
    public Object[] writeln(Context context, Arguments args) {
        if (legacyLines.size() >= PrintDocument.LINES_PER_PAGE) {
            return new Object[]{false, "too many lines"};
        }
        String text = args.checkString(0);
        int color = 0;
        String alignment = "left";
        if (args.count() > 1) {
            if (args.isInteger(1)) color = args.checkInteger(1) & 0xFFFFFF;
            else if (args.isString(1)) alignment = args.checkString(1);
        }
        if (args.count() > 2 && args.isString(2)) alignment = args.checkString(2);
        alignment = PrintDocument.normalizeAlignment(alignment);
        if (CharacterWidth.calculate(text) > PrintDocument.MAX_WIDTH) {
            return new Object[]{false, "line exceeds printable width"};
        }
        legacyLines.add(new PrintDocument.Line(text, color, alignment));
        return new Object[]{true};
    }

    @Callback(direct = true, doc = "function(title:string):boolean -- Sets the title used by the classic writeln buffer.")
    public Object[] setTitle(Context context, Arguments args) {
        legacyTitle = limit(args.count() > 0 ? args.checkString(0) : "", 64);
        return new Object[]{true};
    }

    @Callback(direct = true, doc = "function():boolean -- Clears the classic writeln buffer.")
    public Object[] clear(Context context, Arguments args) {
        legacyLines.clear();
        legacyTitle = "";
        return new Object[]{true};
    }

    @Callback(direct = true, doc = "function():number -- Returns loaded paper count.")
    public Object[] getPaperLevel(Context context, Arguments args) {
        ItemStack paper = inventory.getStackInSlot(PAPER_SLOT);
        if (paper.is(Items.WRITABLE_BOOK)) {
            return new Object[]{WritableBookContent.MAX_PAGES - writableBookPages(paper)};
        }
        return new Object[]{paper.is(Items.PAPER) ? paper.getCount() : 0};
    }

    @Callback(direct = true, doc = "function():number -- Returns remaining black ink durability.")
    public Object[] getBlackInkLevel(Context context, Arguments args) {
        return new Object[]{inkLevel(BLACK_INK_SLOT)};
    }

    @Callback(direct = true, doc = "function():number -- Returns remaining color ink durability.")
    public Object[] getColorInkLevel(Context context, Arguments args) {
        return new Object[]{inkLevel(COLOR_INK_SLOT)};
    }

    @Callback(direct = true, doc = "function(text:string):number -- Returns character count excluding Minecraft formatting codes.")
    public Object[] charCount(Context context, Arguments args) {
        return new Object[]{args.checkString(0).replaceAll("(?i)§[0-9A-FK-OR]", "").length()};
    }

    @Callback(direct = true, doc = "function(text:string):number -- Returns the printable pixel width of a string.")
    public Object[] width(Context context, Arguments args) {
        return new Object[]{CharacterWidth.calculate(args.checkString(0))};
    }

    @Callback(direct = true, doc = "function():number -- Returns the maximum printable line width.")
    public Object[] maxWidth(Context context, Arguments args) {
        return new Object[]{PrintDocument.MAX_WIDTH};
    }

    @Callback(doc = "function(text:string[, options:table]):string, string -- Queues one or more printed name tags.")
    public Object[] printLabel(Context context, Arguments args) {
        if (!PrinterConfig.ENABLE_NAME_TAGS.get()) return new Object[]{null, "name tag printing is disabled"};
        if (jobs.size() >= MAX_QUEUE) return new Object[]{null, "print queue full"};
        Map<?, ?> options = args.count() > 1 ? args.checkTable(1) : Map.of();
        PrintJob job = PrintJob.label(limit(args.checkString(0), 64), copies(options));
        jobs.addLast(job);
        setChanged();
        signalQueued(job);
        return new Object[]{job.id.toString()};
    }

    @Callback(doc = "function(image:table[, options:table]):string, string -- Queues an RGB image for an empty vanilla map.")
    public Object[] printMap(Context context, Arguments args) {
        if (jobs.size() >= MAX_QUEUE) return new Object[]{null, "print queue full"};
        Map<?, ?> imageValue = args.checkTable(0);
        Map<?, ?> options = args.count() > 1 ? args.checkTable(1) : Map.of();
        PrintJob job = PrintJob.map(MapPrintImage.parse(imageValue, options), copies(options));
        jobs.addLast(job);
        setChanged();
        signalQueued(job);
        return new Object[]{job.id.toString()};
    }

    @Callback(doc = "function(data:string[, options:table]):string, string -- Decodes and queues a PNG, JPEG, GIF, or BMP image.")
    public Object[] printImage(Context context, Arguments args) {
        if (jobs.size() >= MAX_QUEUE) return new Object[]{null, "print queue full"};
        byte[] encoded = args.checkByteArray(0);
        Map<?, ?> options = args.count() > 1 ? args.checkTable(1) : Map.of();
        PrintJob job = PrintJob.map(MapPrintImage.decode(encoded, options), copies(options));
        jobs.addLast(job);
        setChanged();
        signalQueued(job);
        return new Object[]{job.id.toString()};
    }

    private void signalQueued(PrintJob job) {
        if (node != null) node.sendToReachable("computer.signal", "openprinter_job", node.address(),
                job.id.toString(), "queued", "");
    }

    @Callback(direct = true, doc = "function([jobId:string]):table, string -- Returns printer or job status.")
    public Object[] status(Context context, Arguments args) {
        if (args.count() > 0) {
            PrintJob job = findJob(args.checkString(0));
            return job == null ? new Object[]{null, "unknown job"} : new Object[]{job.toLua()};
        }
        PrintJob current = jobs.peekFirst();
        if (current != null) return new Object[]{current.toLua()};
        Map<String, Object> idle = new LinkedHashMap<>();
        idle.put("state", "idle");
        idle.put("queueLength", 0);
        idle.put("reason", lastDisplayedReason);
        return new Object[]{idle};
    }

    @Callback(direct = true, doc = "function():table -- Lists active print jobs in FIFO order.")
    public Object[] queue(Context context, Arguments args) {
        Map<Integer, Object> result = new LinkedHashMap<>();
        int index = 1;
        for (PrintJob job : jobs) result.put(index++, job.toLua());
        return new Object[]{result};
    }

    @Callback(doc = "function(jobId:string):boolean, string -- Cancels an active print job.")
    public Object[] cancel(Context context, Arguments args) {
        String id = args.checkString(0);
        PrintJob found = null;
        for (PrintJob job : jobs) if (job.id.toString().equals(id)) { found = job; break; }
        if (found == null) return new Object[]{false, "unknown or finished job"};
        jobs.remove(found);
        found.printTicks = 0;
        transition(found, PrintJob.State.CANCELLED, "cancelled by user");
        remember(found);
        setChanged();
        return new Object[]{true};
    }

    @Callback(direct = true, doc = "function(line:number):string, string -- Classic helper that scans one line from a printed page.")
    public Object[] scanLine(Context context, Arguments args) {
        ItemStack input = inventory.getStackInSlot(SCANNER_SLOT);
        if (!input.is(OpenPrinter.PRINTED_PAGE.get())) return new Object[]{null, "scanner does not contain a printed page"};
        int line = args.checkInteger(0);
        CompoundTag data = tag(input);
        String key = "line" + line;
        if (!data.contains(key) && line > 0) key = "line" + (line - 1);
        if (!data.contains(key)) return new Object[]{null, "line is empty"};
        String[] encoded = data.getString(key).split("∞", 3);
        return new Object[]{encoded.length == 0 ? "" : encoded[0]};
    }

    @Callback(doc = "function():table, string -- Classic helper that scans a vanilla book.")
    public Object[] scanBook(Context context, Arguments args) {
        ItemStack input = inventory.getStackInSlot(SCANNER_SLOT);
        if (!input.is(Items.WRITABLE_BOOK) && !input.is(Items.WRITTEN_BOOK)) {
            return new Object[]{null, "scanner does not contain a vanilla book"};
        }
        return scan(context, args);
    }

    @Callback(doc = "function():table, string -- Scans a printed page or vanilla book into a directly printable document.")
    public Object[] scan(Context context, Arguments args) {
        ItemStack input = inventory.getStackInSlot(SCANNER_SLOT);
        if (input.is(OpenPrinter.PRINTED_PAGE.get())) return new Object[]{scanPrintedPage(input)};
        if (input.is(Items.WRITABLE_BOOK)) {
            WritableBookContent content = input.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
            Map<Integer, Object> pages = new LinkedHashMap<>();
            for (int i = 0; i < content.pages().size(); i++) pages.put(i + 1, Map.of("text", content.pages().get(i).raw()));
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("type", "document");
            document.put("title", "");
            document.put("pages", pages);
            document.put("metadata", Map.of("source", "writable_book", "signed", false));
            return new Object[]{document};
        }
        if (input.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = input.getOrDefault(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY);
            Map<Integer, Object> pages = new LinkedHashMap<>();
            for (int i = 0; i < content.pages().size(); i++) pages.put(i + 1, Map.of("text", content.pages().get(i).raw().getString()));
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("type", "document");
            document.put("title", content.title().raw());
            document.put("pages", pages);
            document.put("metadata", Map.of("source", "written_book", "signed", true, "author", content.author()));
            return new Object[]{document};
        }
        return new Object[]{null, "scanner empty or unsupported item"};
    }

    private static Map<String, Object> scanPrintedPage(ItemStack page) {
        CompoundTag data = tag(page);
        List<PrintDocument.Line> lines = new ArrayList<>();
        for (int i = 0; i < PrintDocument.LINES_PER_PAGE && data.contains("line" + i); i++) {
            String[] encoded = data.getString("line" + i).split("\u221e", 3);
            String text = encoded[0];
            int color;
            try { color = encoded.length > 1 ? Integer.parseInt(encoded[1]) & 0xFFFFFF : 0; }
            catch (NumberFormatException ignored) { color = 0; }
            String alignment = encoded.length > 2 ? encoded[2] : "left";
            lines.add(new PrintDocument.Line(text, color, PrintDocument.normalizeAlignment(alignment)));
        }
        Map<String, Object> document = PrintDocument.scanned(data.getString("pageTitle"), lines).toLua();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "printed_page");
        if (data.hasUUID("printerUUID")) metadata.put("printerUUID", data.getUUID("printerUUID").toString());
        if (data.hasUUID("pageUUID")) metadata.put("pageUUID", data.getUUID("pageUUID").toString());
        document.put("metadata", metadata);
        return document;
    }

    @Callback(direct = true, doc = "function():table -- Returns loaded consumables and output capacity.")
    public Object[] supplies(Context context, Arguments args) {
        Map<String, Object> result = new LinkedHashMap<>();
        ItemStack paper = inventory.getStackInSlot(PAPER_SLOT);
        result.put("paper", paper.is(Items.PAPER) ? paper.getCount() : 0);
        result.put("bookPages", paper.is(Items.WRITABLE_BOOK)
                ? WritableBookContent.MAX_PAGES - writableBookPages(paper) : 0);
        result.put("nameTags", paper.is(Items.NAME_TAG) ? paper.getCount() : 0);
        result.put("emptyMaps", paper.is(Items.MAP) ? paper.getCount() : 0);
        result.put("blackInk", inkLevel(BLACK_INK_SLOT));
        result.put("colorInk", inkLevel(COLOR_INK_SLOT));
        result.put("outputFree", emptyOutputSlots());
        result.put("outputSlots", OUTPUT_END - OUTPUT_START);
        return new Object[]{result};
    }

    @Callback(direct = true, doc = "function():table -- Returns printer limits and supported features.")
    public Object[] capabilities(Context context, Arguments args) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiVersion", 4);
        result.put("maxWidth", PrintDocument.MAX_WIDTH);
        result.put("linesPerPage", PrintDocument.LINES_PER_PAGE);
        result.put("maxDocumentPages", PrintDocument.MAX_DOCUMENT_PAGES);
        result.put("maxJobPages", MAX_JOB_PAGES);
        result.put("maxCopies", MAX_COPIES);
        result.put("maxQueue", MAX_QUEUE);
        result.put("color", true);
        result.put("automaticWrap", true);
        result.put("nameTags", PrinterConfig.ENABLE_NAME_TAGS.get());
        result.put("mapImages", true);
        result.put("mapWidth", MapPrintImage.SIZE);
        result.put("mapHeight", MapPrintImage.SIZE);
        result.put("mapColorInkCost", MAP_COLOR_INK_COST);
        result.put("encodedMapImages", true);
        result.put("mapImageFormats", List.of("png", "jpeg", "gif", "bmp"));
        result.put("mapImageMaxBytes", MapPrintImage.MAX_ENCODED_BYTES);
        result.put("mapImageMaxDimension", MapPrintImage.MAX_SOURCE_DIMENSION);
        return new Object[]{result};
    }

    private PrintJob findJob(String id) {
        for (PrintJob job : jobs) if (job.id.toString().equals(id)) return job;
        for (PrintJob job : history) if (job.id.toString().equals(id)) return job;
        return null;
    }

    private static int copies(Map<?, ?> options) {
        return Math.max(1, Math.min(MAX_COPIES, PrintDocument.intValue(options.get("copies"), 1)));
    }

    private boolean hasPrintedPageOutputSpace() {
        return folderOutputSlotWithSpace() >= 0 || emptyOutputSlot() >= 0;
    }

    private int folderOutputSlotWithSpace() {
        if (level == null) return -1;
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) {
            ItemStack folder = inventory.getStackInSlot(slot);
            if (!folder.is(OpenPrinter.FOLDER.get())) continue;
            PortableInventory contents = new PortableInventory(folder, 9, level.registryAccess(), true);
            for (int contentSlot = 0; contentSlot < contents.getSlots(); contentSlot++) {
                if (contents.getStackInSlot(contentSlot).isEmpty()) return slot;
            }
        }
        return -1;
    }

    private int emptyFolderSlot(ItemStack folder) {
        PortableInventory contents = new PortableInventory(folder, 9, level.registryAccess(), true);
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            if (contents.getStackInSlot(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private void storePrintedPage(ItemStack page) {
        int folderSlot = folderOutputSlotWithSpace();
        if (folderSlot >= 0) {
            ItemStack folder = inventory.getStackInSlot(folderSlot);
            PortableInventory contents = new PortableInventory(folder, 9, level.registryAccess(), true);
            int contentSlot = emptyFolderSlot(folder);
            if (contentSlot < 0) throw new IllegalStateException("folder is full");
            contents.setStackInSlot(contentSlot, page);
            contents.saveToContainer();
            return;
        }
        int outputSlot = emptyOutputSlot();
        if (outputSlot < 0) throw new IllegalStateException("output tray full");
        inventory.setStackInSlot(outputSlot, page);
    }

    private int emptyOutputSlot() {
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) if (inventory.getStackInSlot(slot).isEmpty()) return slot;
        return -1;
    }

    private int emptyOutputSlots() {
        int free = 0;
        for (int slot = OUTPUT_START; slot < OUTPUT_END; slot++) if (inventory.getStackInSlot(slot).isEmpty()) free++;
        return free;
    }

    private int inkLevel(int slot) {
        ItemStack ink = inventory.getStackInSlot(slot);
        return ink.isEmpty() ? 0 : Math.max(0, ink.getMaxDamage() - ink.getDamageValue());
    }

    private void consumeInk(int slot, int amount) {
        if (amount <= 0) return;
        ItemStack ink = inventory.getStackInSlot(slot);
        if (ink.isEmpty()) return;
        ink.setDamageValue(ink.getDamageValue() + amount);
        if (ink.getDamageValue() >= ink.getMaxDamage()) inventory.setStackInSlot(slot, ItemStack.EMPTY);
        else inventory.setStackInSlot(slot, ink);
    }

    private void consumePaper() {
        inventory.extractItem(PAPER_SLOT, 1, false);
    }

    private static String limit(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    public static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
