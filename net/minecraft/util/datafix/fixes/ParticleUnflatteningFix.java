package net.minecraft.util.datafix.fixes;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import org.slf4j.Logger;

public class ParticleUnflatteningFix extends DataFix {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ParticleUnflatteningFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.PARTICLE);
        Type<?> type1 = this.getOutputSchema().getType(References.PARTICLE);
        return this.writeFixAndRead("ParticleUnflatteningFix", type, type1, this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> tag) {
        Optional<String> optional = tag.asString().result();
        if (optional.isEmpty()) {
            return tag;
        } else {
            String s = optional.get();
            String[] astring = s.split(" ", 2);
            String s1 = NamespacedSchema.ensureNamespaced(astring[0]);
            Dynamic<T> dynamic = tag.createMap(Map.of(tag.createString("type"), tag.createString(s1)));

            return switch (s1) {
                case "minecraft:item" -> astring.length > 1 ? this.updateItem(dynamic, astring[1]) : dynamic;
                case "minecraft:block", "minecraft:block_marker", "minecraft:falling_dust", "minecraft:dust_pillar" -> astring.length > 1
                ? this.updateBlock(dynamic, astring[1])
                : dynamic;
                case "minecraft:dust" -> astring.length > 1 ? this.updateDust(dynamic, astring[1]) : dynamic;
                case "minecraft:dust_color_transition" -> astring.length > 1 ? this.updateDustTransition(dynamic, astring[1]) : dynamic;
                case "minecraft:sculk_charge" -> astring.length > 1 ? this.updateSculkCharge(dynamic, astring[1]) : dynamic;
                case "minecraft:vibration" -> astring.length > 1 ? this.updateVibration(dynamic, astring[1]) : dynamic;
                case "minecraft:shriek" -> astring.length > 1 ? this.updateShriek(dynamic, astring[1]) : dynamic;
                default -> dynamic;
            };
        }
    }

    private <T> Dynamic<T> updateItem(Dynamic<T> tag, String item) {
        int i = item.indexOf("{");
        Dynamic<T> dynamic = tag.createMap(Map.of(tag.createString("Count"), tag.createInt(1)));
        if (i == -1) {
            dynamic = dynamic.set("id", tag.createString(item));
        } else {
            dynamic = dynamic.set("id", tag.createString(item.substring(0, i)));
            CompoundTag compoundtag = parseTag(item.substring(i));
            if (compoundtag != null) {
                dynamic = dynamic.set("tag", new Dynamic<>(NbtOps.INSTANCE, compoundtag).convert(tag.getOps()));
            }
        }

        return tag.set("item", dynamic);
    }

    @Nullable
    private static CompoundTag parseTag(String tag) {
        try {
            return TagParser.parseTag(tag);
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse tag: {}", tag, exception);
            return null;
        }
    }

    private <T> Dynamic<T> updateBlock(Dynamic<T> tag, String block) {
        int i = block.indexOf("[");
        Dynamic<T> dynamic = tag.emptyMap();
        if (i == -1) {
            dynamic = dynamic.set("Name", tag.createString(NamespacedSchema.ensureNamespaced(block)));
        } else {
            dynamic = dynamic.set("Name", tag.createString(NamespacedSchema.ensureNamespaced(block.substring(0, i))));
            Map<Dynamic<T>, Dynamic<T>> map = parseBlockProperties(tag, block.substring(i));
            if (!map.isEmpty()) {
                dynamic = dynamic.set("Properties", tag.createMap(map));
            }
        }

        return tag.set("block_state", dynamic);
    }

    private static <T> Map<Dynamic<T>, Dynamic<T>> parseBlockProperties(Dynamic<T> tag, String properties) {
        try {
            Map<Dynamic<T>, Dynamic<T>> map = new HashMap<>();
            StringReader stringreader = new StringReader(properties);
            stringreader.expect('[');
            stringreader.skipWhitespace();

            while (stringreader.canRead() && stringreader.peek() != ']') {
                stringreader.skipWhitespace();
                String s = stringreader.readString();
                stringreader.skipWhitespace();
                stringreader.expect('=');
                stringreader.skipWhitespace();
                String s1 = stringreader.readString();
                stringreader.skipWhitespace();
                map.put(tag.createString(s), tag.createString(s1));
                if (stringreader.canRead()) {
                    if (stringreader.peek() != ',') {
                        break;
                    }

                    stringreader.skip();
                }
            }

            stringreader.expect(']');
            return map;
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse block properties: {}", properties, exception);
            return Map.of();
        }
    }

    private static <T> Dynamic<T> readVector(Dynamic<T> tag, StringReader reader) throws CommandSyntaxException {
        float f = reader.readFloat();
        reader.expect(' ');
        float f1 = reader.readFloat();
        reader.expect(' ');
        float f2 = reader.readFloat();
        return tag.createList(Stream.of(f, f1, f2).map(tag::createFloat));
    }

    private <T> Dynamic<T> updateDust(Dynamic<T> tag, String options) {
        try {
            StringReader stringreader = new StringReader(options);
            Dynamic<T> dynamic = readVector(tag, stringreader);
            stringreader.expect(' ');
            float f = stringreader.readFloat();
            return tag.set("color", dynamic).set("scale", tag.createFloat(f));
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse particle options: {}", options, exception);
            return tag;
        }
    }

    private <T> Dynamic<T> updateDustTransition(Dynamic<T> tag, String options) {
        try {
            StringReader stringreader = new StringReader(options);
            Dynamic<T> dynamic = readVector(tag, stringreader);
            stringreader.expect(' ');
            float f = stringreader.readFloat();
            stringreader.expect(' ');
            Dynamic<T> dynamic1 = readVector(tag, stringreader);
            return tag.set("from_color", dynamic).set("to_color", dynamic1).set("scale", tag.createFloat(f));
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse particle options: {}", options, exception);
            return tag;
        }
    }

    private <T> Dynamic<T> updateSculkCharge(Dynamic<T> tag, String options) {
        try {
            StringReader stringreader = new StringReader(options);
            float f = stringreader.readFloat();
            return tag.set("roll", tag.createFloat(f));
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse particle options: {}", options, exception);
            return tag;
        }
    }

    private <T> Dynamic<T> updateVibration(Dynamic<T> tag, String options) {
        try {
            StringReader stringreader = new StringReader(options);
            float f = (float)stringreader.readDouble();
            stringreader.expect(' ');
            float f1 = (float)stringreader.readDouble();
            stringreader.expect(' ');
            float f2 = (float)stringreader.readDouble();
            stringreader.expect(' ');
            int i = stringreader.readInt();
            Dynamic<T> dynamic = (Dynamic<T>)tag.createIntList(IntStream.of(Mth.floor(f), Mth.floor(f1), Mth.floor(f2)));
            Dynamic<T> dynamic1 = tag.createMap(
                Map.of(tag.createString("type"), tag.createString("minecraft:block"), tag.createString("pos"), dynamic)
            );
            return tag.set("destination", dynamic1).set("arrival_in_ticks", tag.createInt(i));
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse particle options: {}", options, exception);
            return tag;
        }
    }

    private <T> Dynamic<T> updateShriek(Dynamic<T> tag, String options) {
        try {
            StringReader stringreader = new StringReader(options);
            int i = stringreader.readInt();
            return tag.set("delay", tag.createInt(i));
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse particle options: {}", options, exception);
            return tag;
        }
    }
}
