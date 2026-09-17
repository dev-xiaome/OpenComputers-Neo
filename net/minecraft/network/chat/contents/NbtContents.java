package net.minecraft.network.chat.contents;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

public class NbtContents implements ComponentContents {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<NbtContents> CODEC = RecordCodecBuilder.mapCodec(
        p_337510_ -> p_337510_.group(
                    Codec.STRING.fieldOf("nbt").forGetter(NbtContents::getNbtPath),
                    Codec.BOOL.lenientOptionalFieldOf("interpret", Boolean.valueOf(false)).forGetter(NbtContents::isInterpreting),
                    ComponentSerialization.CODEC.lenientOptionalFieldOf("separator").forGetter(NbtContents::getSeparator),
                    DataSource.CODEC.forGetter(NbtContents::getDataSource)
                )
                .apply(p_337510_, NbtContents::new)
    );
    public static final ComponentContents.Type<NbtContents> TYPE = new ComponentContents.Type<>(CODEC, "nbt");
    private final boolean interpreting;
    private final Optional<Component> separator;
    private final String nbtPathPattern;
    private final DataSource dataSource;
    @Nullable
    protected final NbtPathArgument.NbtPath compiledNbtPath;

    public NbtContents(String nbtPathPattern, boolean interpreting, Optional<Component> separator, DataSource dataSource) {
        this(nbtPathPattern, compileNbtPath(nbtPathPattern), interpreting, separator, dataSource);
    }

    private NbtContents(String nbtPathPattern, @Nullable NbtPathArgument.NbtPath compiledNbtPath, boolean interpreting, Optional<Component> separator, DataSource dataSource) {
        this.nbtPathPattern = nbtPathPattern;
        this.compiledNbtPath = compiledNbtPath;
        this.interpreting = interpreting;
        this.separator = separator;
        this.dataSource = dataSource;
    }

    @Nullable
    private static NbtPathArgument.NbtPath compileNbtPath(String nbtPathPattern) {
        try {
            return new NbtPathArgument().parse(new StringReader(nbtPathPattern));
        } catch (CommandSyntaxException commandsyntaxexception) {
            return null;
        }
    }

    public String getNbtPath() {
        return this.nbtPathPattern;
    }

    public boolean isInterpreting() {
        return this.interpreting;
    }

    public Optional<Component> getSeparator() {
        return this.separator;
    }

    public DataSource getDataSource() {
        return this.dataSource;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            if (other instanceof NbtContents nbtcontents
                && this.dataSource.equals(nbtcontents.dataSource)
                && this.separator.equals(nbtcontents.separator)
                && this.interpreting == nbtcontents.interpreting
                && this.nbtPathPattern.equals(nbtcontents.nbtPathPattern)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = this.interpreting ? 1 : 0;
        i = 31 * i + this.separator.hashCode();
        i = 31 * i + this.nbtPathPattern.hashCode();
        return 31 * i + this.dataSource.hashCode();
    }

    @Override
    public String toString() {
        return "nbt{" + this.dataSource + ", interpreting=" + this.interpreting + ", separator=" + this.separator + "}";
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack nbtPathPattern, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        if (nbtPathPattern != null && this.compiledNbtPath != null) {
            Stream<String> stream = this.dataSource.getData(nbtPathPattern).flatMap(p_237417_ -> {
                try {
                    return this.compiledNbtPath.get(p_237417_).stream();
                } catch (CommandSyntaxException commandsyntaxexception) {
                    return Stream.empty();
                }
            }).map(Tag::getAsString);
            if (this.interpreting) {
                Component component = DataFixUtils.orElse(
                    ComponentUtils.updateForEntity(nbtPathPattern, this.separator, entity, recursionDepth), ComponentUtils.DEFAULT_NO_STYLE_SEPARATOR
                );
                return stream.flatMap(p_329869_ -> {
                    try {
                        MutableComponent mutablecomponent = Component.Serializer.fromJson(p_329869_, nbtPathPattern.registryAccess());
                        return Stream.of(ComponentUtils.updateForEntity(nbtPathPattern, mutablecomponent, entity, recursionDepth));
                    } catch (Exception exception) {
                        LOGGER.warn("Failed to parse component: {}", p_329869_, exception);
                        return Stream.of();
                    }
                }).reduce((p_237420_, p_237421_) -> p_237420_.append(component).append(p_237421_)).orElseGet(Component::empty);
            } else {
                return ComponentUtils.updateForEntity(nbtPathPattern, this.separator, entity, recursionDepth)
                    .map(
                        p_237415_ -> stream.map(Component::literal)
                                .reduce((p_237424_, p_237425_) -> p_237424_.append(p_237415_).append(p_237425_))
                                .orElseGet(Component::empty)
                    )
                    .orElseGet(() -> Component.literal(stream.collect(Collectors.joining(", "))));
            }
        } else {
            return Component.empty();
        }
    }

    @Override
    public ComponentContents.Type<?> type() {
        return TYPE;
    }
}
