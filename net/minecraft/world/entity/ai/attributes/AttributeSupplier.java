package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

public class AttributeSupplier {
    private final Map<Holder<Attribute>, AttributeInstance> instances;

    AttributeSupplier(Map<Holder<Attribute>, AttributeInstance> instances) {
        this.instances = instances;
    }

    private AttributeInstance getAttributeInstance(Holder<Attribute> attribute) {
        AttributeInstance attributeinstance = this.instances.get(attribute);
        if (attributeinstance == null) {
            throw new IllegalArgumentException("Can't find attribute " + attribute.getRegisteredName());
        } else {
            return attributeinstance;
        }
    }

    public double getValue(Holder<Attribute> attribute) {
        return this.getAttributeInstance(attribute).getValue();
    }

    public double getBaseValue(Holder<Attribute> attribute) {
        return this.getAttributeInstance(attribute).getBaseValue();
    }

    public double getModifierValue(Holder<Attribute> attribute, ResourceLocation id) {
        AttributeModifier attributemodifier = this.getAttributeInstance(attribute).getModifier(id);
        if (attributemodifier == null) {
            throw new IllegalArgumentException("Can't find modifier " + id + " on attribute " + attribute.getRegisteredName());
        } else {
            return attributemodifier.amount();
        }
    }

    @Nullable
    public AttributeInstance createInstance(Consumer<AttributeInstance> onDirty, Holder<Attribute> attribute) {
        AttributeInstance attributeinstance = this.instances.get(attribute);
        if (attributeinstance == null) {
            return null;
        } else {
            AttributeInstance attributeinstance1 = new AttributeInstance(attribute, onDirty);
            attributeinstance1.replaceFrom(attributeinstance);
            return attributeinstance1;
        }
    }

    public static AttributeSupplier.Builder builder() {
        return new AttributeSupplier.Builder();
    }

    public boolean hasAttribute(Holder<Attribute> attribute) {
        return this.instances.containsKey(attribute);
    }

    public boolean hasModifier(Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance attributeinstance = this.instances.get(attribute);
        return attributeinstance != null && attributeinstance.getModifier(id) != null;
    }

    public static class Builder {
        private final Map<Holder<Attribute>, AttributeInstance> builder = new java.util.HashMap<>();
        private boolean instanceFrozen;
        private final java.util.List<AttributeSupplier.Builder> others = new java.util.ArrayList<>();

        public Builder() { }

        public Builder(AttributeSupplier attributeMap) {
            this.builder.putAll(attributeMap.instances);
        }

        public void combine(Builder other) {
            this.builder.putAll(other.builder);
            others.add(other);
        }

        public boolean hasAttribute(Holder<Attribute> attribute) {
            return this.builder.containsKey(attribute);
        }

        private AttributeInstance create(Holder<Attribute> attribute) {
            AttributeInstance attributeinstance = new AttributeInstance(attribute, p_315942_ -> {
                if (this.instanceFrozen) {
                    throw new UnsupportedOperationException("Tried to change value for default attribute instance: " + attribute.getRegisteredName());
                }
            });
            this.builder.put(attribute, attributeinstance);
            return attributeinstance;
        }

        public AttributeSupplier.Builder add(Holder<Attribute> attribute) {
            this.create(attribute);
            return this;
        }

        public AttributeSupplier.Builder add(Holder<Attribute> attribute, double baseValue) {
            AttributeInstance attributeinstance = this.create(attribute);
            attributeinstance.setBaseValue(baseValue);
            return this;
        }

        public AttributeSupplier build() {
            this.instanceFrozen = true;
            others.forEach(b -> b.instanceFrozen = true);
            return new AttributeSupplier(ImmutableMap.copyOf(this.builder));
        }
    }
}
