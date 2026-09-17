package net.minecraft.world.entity.ai.attributes;

import net.minecraft.util.Mth;

/**
 * Defines an entity {@linkplain net.minecraft.world.entity.ai.attributes.Attribute attribute} that is limited to a range of values.
 */
public class RangedAttribute extends Attribute {
    /**
     * The lowest possible value for the attribute.
     */
    private final double minValue;
    /**
     * The highest possible value for the attribute.
     */
    private final double maxValue;

    public RangedAttribute(String descriptionId, double defaultValue, double min, double max) {
        super(descriptionId, defaultValue);
        this.minValue = min;
        this.maxValue = max;
        if (min > max) {
            throw new IllegalArgumentException("Minimum value cannot be bigger than maximum value!");
        } else if (defaultValue < min) {
            throw new IllegalArgumentException("Default value cannot be lower than minimum value!");
        } else if (defaultValue > max) {
            throw new IllegalArgumentException("Default value cannot be bigger than maximum value!");
        }
    }

    public double getMinValue() {
        return this.minValue;
    }

    public double getMaxValue() {
        return this.maxValue;
    }

    /**
     * Sanitizes the value of the attribute to fit within the expected parameter range of the attribute. In this case it will clamp the value between {@link minValue} and {@link maxValue}.
     * @return The clamped attribute value.
     *
     * @param value The value of the attribute to sanitize.
     */
    @Override
    public double sanitizeValue(double value) {
        return Double.isNaN(value) ? this.minValue : Mth.clamp(value, this.minValue, this.maxValue);
    }
}
