package net.minecraft.data.models.blockstates;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public class MultiVariantGenerator implements BlockStateGenerator {
    private final Block block;
    private final List<Variant> baseVariants;
    private final Set<Property<?>> seenProperties = Sets.newHashSet();
    private final List<PropertyDispatch> declaredPropertySets = Lists.newArrayList();

    private MultiVariantGenerator(Block block, List<Variant> baseVariants) {
        this.block = block;
        this.baseVariants = baseVariants;
    }

    public MultiVariantGenerator with(PropertyDispatch propertyDispatch) {
        propertyDispatch.getDefinedProperties().forEach(p_339378_ -> {
            if (this.block.getStateDefinition().getProperty(p_339378_.getName()) != p_339378_) {
                throw new IllegalStateException("Property " + p_339378_ + " is not defined for block " + this.block);
            } else if (!this.seenProperties.add((Property<?>)p_339378_)) {
                throw new IllegalStateException("Values of property " + p_339378_ + " already defined for block " + this.block);
            }
        });
        this.declaredPropertySets.add(propertyDispatch);
        return this;
    }

    public JsonElement get() {
        Stream<Pair<Selector, List<Variant>>> stream = Stream.of(Pair.of(Selector.empty(), this.baseVariants));

        for (PropertyDispatch propertydispatch : this.declaredPropertySets) {
            Map<Selector, List<Variant>> map = propertydispatch.getEntries();
            stream = stream.flatMap(p_125289_ -> map.entrySet().stream().map(p_176309_ -> {
                    Selector selector = ((Selector)p_125289_.getFirst()).extend(p_176309_.getKey());
                    List<Variant> list = mergeVariants((List<Variant>)p_125289_.getSecond(), p_176309_.getValue());
                    return Pair.of(selector, list);
                }));
        }

        Map<String, JsonElement> map1 = new TreeMap<>();
        stream.forEach(p_125285_ -> map1.put(p_125285_.getFirst().getKey(), Variant.convertList(p_125285_.getSecond())));
        JsonObject jsonobject = new JsonObject();
        jsonobject.add("variants", Util.make(new JsonObject(), p_125282_ -> map1.forEach(p_125282_::add)));
        return jsonobject;
    }

    private static List<Variant> mergeVariants(List<Variant> variants1, List<Variant> variants2) {
        Builder<Variant> builder = ImmutableList.builder();
        variants1.forEach(p_125276_ -> variants2.forEach(p_176306_ -> builder.add(Variant.merge(p_125276_, p_176306_))));
        return builder.build();
    }

    @Override
    public Block getBlock() {
        return this.block;
    }

    public static MultiVariantGenerator multiVariant(Block block) {
        return new MultiVariantGenerator(block, ImmutableList.of(Variant.variant()));
    }

    public static MultiVariantGenerator multiVariant(Block block, Variant variant) {
        return new MultiVariantGenerator(block, ImmutableList.of(variant));
    }

    public static MultiVariantGenerator multiVariant(Block block, Variant... variants) {
        return new MultiVariantGenerator(block, ImmutableList.copyOf(variants));
    }
}
