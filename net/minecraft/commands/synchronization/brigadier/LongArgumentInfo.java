package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class LongArgumentInfo implements ArgumentTypeInfo<LongArgumentType, LongArgumentInfo.Template> {
    public void serializeToNetwork(LongArgumentInfo.Template template, FriendlyByteBuf buffer) {
        boolean flag = template.min != Long.MIN_VALUE;
        boolean flag1 = template.max != Long.MAX_VALUE;
        buffer.writeByte(ArgumentUtils.createNumberFlags(flag, flag1));
        if (flag) {
            buffer.writeLong(template.min);
        }

        if (flag1) {
            buffer.writeLong(template.max);
        }
    }

    public LongArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        byte b0 = buffer.readByte();
        long i = ArgumentUtils.numberHasMin(b0) ? buffer.readLong() : Long.MIN_VALUE;
        long j = ArgumentUtils.numberHasMax(b0) ? buffer.readLong() : Long.MAX_VALUE;
        return new LongArgumentInfo.Template(i, j);
    }

    public void serializeToJson(LongArgumentInfo.Template template, JsonObject json) {
        if (template.min != Long.MIN_VALUE) {
            json.addProperty("min", template.min);
        }

        if (template.max != Long.MAX_VALUE) {
            json.addProperty("max", template.max);
        }
    }

    public LongArgumentInfo.Template unpack(LongArgumentType argument) {
        return new LongArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<LongArgumentType> {
        final long min;
        final long max;

        Template(long min, long max) {
            this.min = min;
            this.max = max;
        }

        public LongArgumentType instantiate(CommandBuildContext context) {
            return LongArgumentType.longArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<LongArgumentType, ?> type() {
            return LongArgumentInfo.this;
        }
    }
}
