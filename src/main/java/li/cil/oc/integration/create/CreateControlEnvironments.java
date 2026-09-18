package li.cil.oc.integration.create;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.content.contraptions.chassis.StickerBlockEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.observer.TrackObserverBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBlock;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CreateControlEnvironments {
    private CreateControlEnvironments() {
    }

    public static final class Sticker extends CreateEnvironment<StickerBlockEntity> {
        Sticker(final StickerBlockEntity blockEntity) {
            super(blockEntity, "Create_Sticker");
        }

        @Callback(direct = true)
        public Object[] isExtended(final Context context, final Arguments args) {
            return result(blockEntity.isBlockStateExtended());
        }

        @Callback(direct = true)
        public Object[] isAttachedToBlock(final Context context, final Arguments args) {
            return result(blockEntity.isBlockStateExtended() && blockEntity.isAttachedToBlock());
        }

        @Callback
        public Object[] extend(final Context context, final Arguments args) {
            return result(setExtended(true));
        }

        @Callback
        public Object[] retract(final Context context, final Arguments args) {
            return result(setExtended(false));
        }

        @Callback
        public Object[] toggle(final Context context, final Arguments args) {
            final BlockState state = blockEntity.getBlockState();
            if (!(state.getBlock() instanceof StickerBlock))
                return result(false);
            return result(setExtended(!state.getValue(StickerBlock.EXTENDED)));
        }

        private boolean setExtended(final boolean extended) {
            final BlockState state = blockEntity.getBlockState();
            if (!(state.getBlock() instanceof StickerBlock) || state.getValue(StickerBlock.EXTENDED) == extended)
                return false;
            blockEntity.getLevel().setBlock(blockEntity.getBlockPos(),
                    state.setValue(StickerBlock.EXTENDED, extended), Block.UPDATE_CLIENTS);
            return true;
        }
    }

    public static final class TrackObserver extends CreateEnvironment<TrackObserverBlockEntity> {
        TrackObserver(final TrackObserverBlockEntity blockEntity) {
            super(blockEntity, "Create_TrainObserver");
        }

        @Callback(direct = true)
        public Object[] isTrainPassing(final Context context, final Arguments args) {
            return result(Create.RAILWAYS.trains.containsKey(blockEntity.passingTrainUUID));
        }

        @Callback(direct = true)
        public Object[] getPassingTrainName(final Context context, final Arguments args) {
            final Train train = Create.RAILWAYS.trains.get(blockEntity.passingTrainUUID);
            return result(train == null ? null : train.name.getString());
        }
    }

    public static final class Signal extends CreateEnvironment<SignalBlockEntity> {
        Signal(final SignalBlockEntity blockEntity) {
            super(blockEntity, "Create_Signal");
        }

        @Callback(direct = true)
        public Object[] getState(final Context context, final Arguments args) {
            return result(blockEntity.getState().toString());
        }

        @Callback(direct = true)
        public Object[] isForcedRed(final Context context, final Arguments args) {
            return result(blockEntity.getBlockState().getValue(SignalBlock.POWERED));
        }

        @Callback
        public Object[] setForcedRed(final Context context, final Arguments args) {
            if (blockEntity.getLevel() != null)
                blockEntity.getLevel().setBlock(blockEntity.getBlockPos(),
                        blockEntity.getBlockState().setValue(SignalBlock.POWERED, args.checkBoolean(0)), 2);
            return result();
        }

        @Callback(direct = true)
        public Object[] listBlockingTrainNames(final Context context, final Arguments args) {
            final SignalBoundary signal = requireSignal();
            final Map<Integer, String> trains = new LinkedHashMap<>();
            int index = 1;
            for (final boolean current : Iterate.trueAndFalse) {
                final Map<BlockPos, Boolean> set = signal.blockEntities.get(current);
                if (!set.containsKey(blockEntity.getBlockPos()))
                    continue;
                final UUID group = signal.groups.get(current);
                final SignalEdgeGroup edgeGroup = Create.RAILWAYS.signalEdgeGroups.get(group);
                if (edgeGroup != null)
                    for (final Train train : edgeGroup.trains)
                        trains.put(index++, train.name.getString());
            }
            return result(trains);
        }

        @Callback(direct = true)
        public Object[] getSignalType(final Context context, final Arguments args) {
            return result(requireSignal().getTypeFor(blockEntity.getBlockPos()).toString());
        }

        @Callback
        public Object[] cycleSignalType(final Context context, final Arguments args) {
            requireSignal().cycleSignalType(blockEntity.getBlockPos());
            return result();
        }

        private SignalBoundary requireSignal() {
            final SignalBoundary signal = blockEntity.getSignal();
            if (signal == null)
                throw new IllegalStateException("no signal");
            return signal;
        }
    }
}
