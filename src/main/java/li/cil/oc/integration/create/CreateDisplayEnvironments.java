package li.cil.oc.integration.create;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class CreateDisplayEnvironments {
    private CreateDisplayEnvironments() {
    }

    public static final class DisplayLink extends CreateEnvironment<DisplayLinkBlockEntity> {
        private static final String SOURCE_LIST_TAG = "ComputerSourceList";
        private final AtomicInteger cursorX = new AtomicInteger();
        private final AtomicInteger cursorY = new AtomicInteger();

        DisplayLink(final DisplayLinkBlockEntity blockEntity) {
            super(blockEntity, "Create_DisplayLink");
        }

        @Callback(direct = true)
        public Object[] setCursorPos(final Context context, final Arguments args) {
            final int x = args.checkInteger(0);
            final int y = args.checkInteger(1);
            if (x < 1 || y < 1)
                throw new IllegalArgumentException("cursor position must be larger than 0");
            cursorX.set(x - 1);
            cursorY.set(y - 1);
            return result();
        }

        @Callback(direct = true)
        public Object[] getCursorPos(final Context context, final Arguments args) {
            return result(cursorX.get() + 1, cursorY.get() + 1);
        }

        @Callback
        public Object[] getSize(final Context context, final Arguments args) {
            blockEntity.updateGatheredData();
            final DisplayTargetStats stats = blockEntity.activeTarget.provideStats(
                    new DisplayLinkContext(blockEntity.getLevel(), blockEntity));
            return result(stats.maxRows(), stats.maxColumns());
        }

        @Callback(direct = true)
        public Object[] isColor(final Context context, final Arguments args) {
            return result(false);
        }

        @Callback(direct = true)
        public Object[] isColour(final Context context, final Arguments args) {
            return result(false);
        }

        @Callback(direct = true)
        public Object[] write(final Context context, final Arguments args) {
            writeImpl(args.checkString(0));
            return result();
        }

        @Callback(direct = true)
        public Object[] writeBytes(final Context context, final Arguments args) {
            final Object value = args.checkAny(0);
            final byte[] bytes;
            if (value instanceof byte[] raw) {
                bytes = raw;
            } else if (value instanceof Map<?, ?> table) {
                bytes = new byte[table.size()];
                for (int index = 0; index < bytes.length; index++) {
                    final Object entry = table.get(index + 1);
                    if (!(entry instanceof Number number))
                        throw new IllegalArgumentException("byte table entries must be numbers");
                    bytes[index] = (byte) (number.intValue() & 0xff);
                }
            } else {
                throw new IllegalArgumentException("bad argument #1 (string or table expected)");
            }
            writeImpl(new String(bytes, StandardCharsets.UTF_8));
            return result();
        }

        private void writeImpl(final String text) {
            final ListTag tag = blockEntity.getSourceConfig().getList(SOURCE_LIST_TAG, Tag.TAG_STRING);
            final int x = cursorX.get();
            final int y = cursorY.get();
            for (int index = tag.size(); index <= y; index++)
                tag.add(StringTag.valueOf(""));
            final StringBuilder builder = new StringBuilder(tag.getString(y));
            builder.append(" ".repeat(Math.max(0, x - builder.length())));
            builder.replace(x, x + text.length(), text);
            tag.set(y, StringTag.valueOf(builder.toString()));
            synchronized (blockEntity) {
                blockEntity.getSourceConfig().put(SOURCE_LIST_TAG, tag);
            }
            cursorX.set(x + text.length());
        }

        @Callback(direct = true)
        public Object[] clearLine(final Context context, final Arguments args) {
            final ListTag tag = blockEntity.getSourceConfig().getList(SOURCE_LIST_TAG, Tag.TAG_STRING);
            if (tag.size() > cursorY.get())
                tag.set(cursorY.get(), StringTag.valueOf(""));
            synchronized (blockEntity) {
                blockEntity.getSourceConfig().put(SOURCE_LIST_TAG, tag);
            }
            return result();
        }

        @Callback(direct = true)
        public Object[] clear(final Context context, final Arguments args) {
            synchronized (blockEntity) {
                blockEntity.getSourceConfig().put(SOURCE_LIST_TAG, new ListTag());
            }
            return result();
        }

        @Callback
        public Object[] update(final Context context, final Arguments args) {
            blockEntity.tickSource();
            return result();
        }
    }

    public static final class NixieTube extends CreateEnvironment<NixieTubeBlockEntity> {
        NixieTube(final NixieTubeBlockEntity blockEntity) {
            super(blockEntity, "Create_NixieTube");
        }

        @Override
        protected void onFirstComputerAttach() {
            final Level level = blockEntity.getLevel();
            if (level == null)
                return;
            NixieTubeBlock.walkNixies(level, blockEntity.getBlockPos(), true, (pos, row) -> {
                if (level.getBlockEntity(pos) instanceof NixieTubeBlockEntity nixie)
                    nixie.displayEmptyText(row);
            });
        }

        @Override
        protected void onLastComputerDetach() {
            final Level level = blockEntity.getLevel();
            if (level == null || !(level.getBlockState(blockEntity.getBlockPos()).getBlock() instanceof NixieTubeBlock))
                return;
            final BlockState state = level.getBlockState(blockEntity.getBlockPos());
            NixieTubeBlock.walkNixies(level, blockEntity.getBlockPos(), false, (pos, row) -> {
                if (level.getBlockEntity(pos) instanceof NixieTubeBlockEntity nixie)
                    NixieTubeBlock.updateDisplayedRedstoneValue(nixie, state, true);
            });
        }

        @Callback
        public Object[] setText(final Context context, final Arguments args) {
            final Level level = blockEntity.getLevel();
            if (level == null)
                return result();
            blockEntity.computerSignal = null;
            final String json = Component.Serializer.toJson(Component.literal(args.checkString(0)), level.registryAccess());
            final String color = args.optString(1, null);
            changeTextNixie(json, color == null ? null : parseColor(color));
            return result();
        }

        @Callback
        public Object[] setTextColour(final Context context, final Arguments args) {
            changeTextNixie(null, parseColor(args.checkString(0)));
            return result();
        }

        @Callback
        public Object[] setTextColor(final Context context, final Arguments args) {
            return setTextColour(context, args);
        }

        private void changeTextNixie(final String json, final DyeColor color) {
            final Level level = blockEntity.getLevel();
            if (level == null)
                return;
            final BlockState state = level.getBlockState(blockEntity.getBlockPos());
            NixieTubeBlock.walkNixies(level, blockEntity.getBlockPos(), true, (pos, row) -> {
                if (json != null)
                    ((NixieTubeBlock) blockEntity.getBlockState().getBlock()).withBlockEntityDo(
                            level, pos, nixie -> nixie.displayCustomText(json, row));
                if (color != null)
                    level.setBlockAndUpdate(pos, NixieTubeBlock.withColor(state, color));
            });
        }

        @Callback
        public Object[] setSignal(final Context context, final Arguments args) {
            if (args.isTable(0))
                setSignal(signal().first, args.checkTable(0));
            if (args.isTable(1))
                setSignal(signal().second, args.checkTable(1));
            return result();
        }

        private void setSignal(final NixieTubeBlockEntity.ComputerSignal.TubeDisplay display, final Map<?, ?> attrs) {
            if (attrs.containsKey("r")) display.r = constrainByte("r", 0, 255, attrs.get("r"));
            if (attrs.containsKey("g")) display.g = constrainByte("g", 0, 255, attrs.get("g"));
            if (attrs.containsKey("b")) display.b = constrainByte("b", 0, 255, attrs.get("b"));
            if (attrs.containsKey("glowWidth")) display.glowWidth = constrainByte("glowWidth", 1, 4, attrs.get("glowWidth"));
            if (attrs.containsKey("glowHeight")) display.glowHeight = constrainByte("glowHeight", 1, 4, attrs.get("glowHeight"));
            if (attrs.containsKey("blinkPeriod")) display.blinkPeriod = constrainByte("blinkPeriod", 0, 255, attrs.get("blinkPeriod"));
            if (attrs.containsKey("blinkOffTime")) display.blinkOffTime = constrainByte("blinkOffTime", 0, 255, attrs.get("blinkOffTime"));
            if (display.r == 0 && display.g == 0 && display.b == 0) {
                display.blinkPeriod = 0;
                display.blinkOffTime = 0;
            } else if (display.blinkPeriod == 0) {
                display.blinkPeriod = 1;
                display.blinkOffTime = 0;
            }
            blockEntity.notifyUpdate();
        }

        private byte constrainByte(final String name, final int min, final int max, final Object raw) {
            if (!(raw instanceof Number number))
                throw new IllegalArgumentException("field " + name + " must be a number");
            final int value = number.intValue();
            if (value < min || value > max)
                throw new IllegalArgumentException("field " + name + " must be in range " + min + "-" + max);
            return (byte) value;
        }

        private NixieTubeBlockEntity.ComputerSignal signal() {
            if (blockEntity.computerSignal == null)
                blockEntity.computerSignal = new NixieTubeBlockEntity.ComputerSignal();
            return blockEntity.computerSignal;
        }

        private DyeColor parseColor(final String raw) {
            final String name = "grey".equalsIgnoreCase(raw) ? "gray" : raw;
            for (final DyeColor value : DyeColor.values())
                if (value.getName().equalsIgnoreCase(name))
                    return value;
            throw new IllegalArgumentException("unknown dye color: " + raw);
        }
    }
}
