package net.minecraft.nbt;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

public class TextComponentTagVisitor implements TagVisitor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INLINE_LIST_THRESHOLD = 8;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_LENGTH = 128;
    private static final ByteCollection INLINE_ELEMENT_TYPES = new ByteOpenHashSet(Arrays.asList((byte)1, (byte)2, (byte)3, (byte)4, (byte)5, (byte)6));
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_KEY = ChatFormatting.AQUA;
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_STRING = ChatFormatting.GREEN;
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_NUMBER = ChatFormatting.GOLD;
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_NUMBER_TYPE = ChatFormatting.RED;
    private static final Pattern SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final String LIST_OPEN = "[";
    private static final String LIST_CLOSE = "]";
    private static final String LIST_TYPE_SEPARATOR = ";";
    private static final String ELEMENT_SPACING = " ";
    private static final String STRUCT_OPEN = "{";
    private static final String STRUCT_CLOSE = "}";
    private static final String NEWLINE = "\n";
    private static final String NAME_VALUE_SEPARATOR = ": ";
    private static final String ELEMENT_SEPARATOR = String.valueOf(',');
    private static final String WRAPPED_ELEMENT_SEPARATOR = ELEMENT_SEPARATOR + "\n";
    private static final String SPACED_ELEMENT_SEPARATOR = ELEMENT_SEPARATOR + " ";
    private static final Component FOLDED = Component.literal("<...>").withStyle(ChatFormatting.GRAY);
    private static final Component BYTE_TYPE = Component.literal("b").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component SHORT_TYPE = Component.literal("s").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component INT_TYPE = Component.literal("I").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component LONG_TYPE = Component.literal("L").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component FLOAT_TYPE = Component.literal("f").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component DOUBLE_TYPE = Component.literal("d").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component BYTE_ARRAY_TYPE = Component.literal("B").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private final String indentation;
    private int indentDepth;
    private int depth;
    private final MutableComponent result = Component.empty();

    public TextComponentTagVisitor(String indentation) {
        this.indentation = indentation;
    }

    public Component visit(Tag tag) {
        tag.accept(this);
        return this.result;
    }

    @Override
    public void visitString(StringTag tag) {
        String s = StringTag.quoteAndEscape(tag.getAsString());
        String s1 = s.substring(0, 1);
        Component component = Component.literal(s.substring(1, s.length() - 1)).withStyle(SYNTAX_HIGHLIGHTING_STRING);
        this.result.append(s1).append(component).append(s1);
    }

    @Override
    public void visitByte(ByteTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.getAsNumber())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(BYTE_TYPE);
    }

    @Override
    public void visitShort(ShortTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.getAsNumber())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(SHORT_TYPE);
    }

    @Override
    public void visitInt(IntTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.getAsNumber())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER));
    }

    @Override
    public void visitLong(LongTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.getAsNumber())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(LONG_TYPE);
    }

    @Override
    public void visitFloat(FloatTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.getAsFloat())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(FLOAT_TYPE);
    }

    @Override
    public void visitDouble(DoubleTag tag) {
        this.result.append(Component.literal(String.valueOf(tag.getAsDouble())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(DOUBLE_TYPE);
    }

    @Override
    public void visitByteArray(ByteArrayTag tag) {
        this.result.append("[").append(BYTE_ARRAY_TYPE).append(";");
        byte[] abyte = tag.getAsByteArray();

        for (int i = 0; i < abyte.length && i < 128; i++) {
            MutableComponent mutablecomponent = Component.literal(String.valueOf(abyte[i])).withStyle(SYNTAX_HIGHLIGHTING_NUMBER);
            this.result.append(" ").append(mutablecomponent).append(BYTE_ARRAY_TYPE);
            if (i != abyte.length - 1) {
                this.result.append(ELEMENT_SEPARATOR);
            }
        }

        if (abyte.length > 128) {
            this.result.append(FOLDED);
        }

        this.result.append("]");
    }

    @Override
    public void visitIntArray(IntArrayTag tag) {
        this.result.append("[").append(INT_TYPE).append(";");
        int[] aint = tag.getAsIntArray();

        for (int i = 0; i < aint.length && i < 128; i++) {
            this.result.append(" ").append(Component.literal(String.valueOf(aint[i])).withStyle(SYNTAX_HIGHLIGHTING_NUMBER));
            if (i != aint.length - 1) {
                this.result.append(ELEMENT_SEPARATOR);
            }
        }

        if (aint.length > 128) {
            this.result.append(FOLDED);
        }

        this.result.append("]");
    }

    @Override
    public void visitLongArray(LongArrayTag tag) {
        this.result.append("[").append(LONG_TYPE).append(";");
        long[] along = tag.getAsLongArray();

        for (int i = 0; i < along.length && i < 128; i++) {
            Component component = Component.literal(String.valueOf(along[i])).withStyle(SYNTAX_HIGHLIGHTING_NUMBER);
            this.result.append(" ").append(component).append(LONG_TYPE);
            if (i != along.length - 1) {
                this.result.append(ELEMENT_SEPARATOR);
            }
        }

        if (along.length > 128) {
            this.result.append(FOLDED);
        }

        this.result.append("]");
    }

    @Override
    public void visitList(ListTag tag) {
        if (tag.isEmpty()) {
            this.result.append("[]");
        } else if (this.depth >= 64) {
            this.result.append("[").append(FOLDED).append("]");
        } else if (INLINE_ELEMENT_TYPES.contains(tag.getElementType()) && tag.size() <= 8) {
            this.result.append("[");

            for (int j = 0; j < tag.size(); j++) {
                if (j != 0) {
                    this.result.append(SPACED_ELEMENT_SEPARATOR);
                }

                this.appendSubTag(tag.get(j), false);
            }

            this.result.append("]");
        } else {
            this.result.append("[");
            if (!this.indentation.isEmpty()) {
                this.result.append("\n");
            }

            String s = Strings.repeat(this.indentation, this.indentDepth + 1);

            for (int i = 0; i < tag.size() && i < 128; i++) {
                this.result.append(s);
                this.appendSubTag(tag.get(i), true);
                if (i != tag.size() - 1) {
                    this.result.append(this.indentation.isEmpty() ? SPACED_ELEMENT_SEPARATOR : WRAPPED_ELEMENT_SEPARATOR);
                }
            }

            if (tag.size() > 128) {
                this.result.append(s).append(FOLDED);
            }

            if (!this.indentation.isEmpty()) {
                this.result.append("\n" + Strings.repeat(this.indentation, this.indentDepth));
            }

            this.result.append("]");
        }
    }

    @Override
    public void visitCompound(CompoundTag tag) {
        if (tag.isEmpty()) {
            this.result.append("{}");
        } else if (this.depth >= 64) {
            this.result.append("{").append(FOLDED).append("}");
        } else {
            this.result.append("{");
            Collection<String> collection = tag.getAllKeys();
            if (LOGGER.isDebugEnabled()) {
                List<String> list = Lists.newArrayList(tag.getAllKeys());
                Collections.sort(list);
                collection = list;
            }

            if (!this.indentation.isEmpty()) {
                this.result.append("\n");
            }

            String s1 = Strings.repeat(this.indentation, this.indentDepth + 1);
            Iterator<String> iterator = collection.iterator();

            while (iterator.hasNext()) {
                String s = iterator.next();
                this.result.append(s1).append(handleEscapePretty(s)).append(": ");
                this.appendSubTag(tag.get(s), true);
                if (iterator.hasNext()) {
                    this.result.append(this.indentation.isEmpty() ? SPACED_ELEMENT_SEPARATOR : WRAPPED_ELEMENT_SEPARATOR);
                }
            }

            if (!this.indentation.isEmpty()) {
                this.result.append("\n" + Strings.repeat(this.indentation, this.indentDepth));
            }

            this.result.append("}");
        }
    }

    private void appendSubTag(Tag tag, boolean indent) {
        if (indent) {
            this.indentDepth++;
        }

        this.depth++;

        try {
            tag.accept(this);
        } finally {
            if (indent) {
                this.indentDepth--;
            }

            this.depth--;
        }
    }

    protected static Component handleEscapePretty(String text) {
        if (SIMPLE_VALUE.matcher(text).matches()) {
            return Component.literal(text).withStyle(SYNTAX_HIGHLIGHTING_KEY);
        } else {
            String s = StringTag.quoteAndEscape(text);
            String s1 = s.substring(0, 1);
            Component component = Component.literal(s.substring(1, s.length() - 1)).withStyle(SYNTAX_HIGHLIGHTING_KEY);
            return Component.literal(s1).append(component).append(s1);
        }
    }

    @Override
    public void visitEnd(EndTag tag) {
    }
}
