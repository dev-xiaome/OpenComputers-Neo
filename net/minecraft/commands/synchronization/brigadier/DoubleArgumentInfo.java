package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class DoubleArgumentInfo implements ArgumentTypeInfo<DoubleArgumentType, DoubleArgumentInfo.Template> {
    public void serializeToNetwork(DoubleArgumentInfo.Template template, FriendlyByteBuf buffer) {
        boolean flag = template.min != -Double.MAX_VALUE;
        boolean flag1 = template.max != Double.MAX_VALUE;
        buffer.writeByte(ArgumentUtils.createNumberFlags(flag, flag1));
        if (flag) {
            buffer.writeDouble(template.min);
        }

        if (flag1) {
            buffer.writeDouble(template.max);
        }
    }

    public DoubleArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        byte b0 = buffer.readByte();
        double d0 = ArgumentUtils.numberHasMin(b0) ? buffer.readDouble() : -Double.MAX_VALUE;
        double d1 = ArgumentUtils.numberHasMax(b0) ? buffer.readDouble() : Double.MAX_VALUE;
        return new DoubleArgumentInfo.Template(d0, d1);
    }

    public void serializeToJson(DoubleArgumentInfo.Template template, JsonObject json) {
        if (template.min != -Double.MAX_VALUE) {
            json.addProperty("min", template.min);
        }

        if (template.max != Double.MAX_VALUE) {
            json.addProperty("max", template.max);
        }
    }

    public DoubleArgumentInfo.Template unpack(DoubleArgumentType argument) {
        return new DoubleArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<DoubleArgumentType> {
        final double min;
        final double max;

        Template(double min, double max) {
            this.min = min;
            this.max = max;
        }

        public DoubleArgumentType instantiate(CommandBuildContext context) {
            return DoubleArgumentType.doubleArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<DoubleArgumentType, ?> type() {
            return DoubleArgumentInfo.this;
        }
    }
}
