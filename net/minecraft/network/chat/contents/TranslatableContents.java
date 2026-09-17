package net.minecraft.network.chat.contents;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

public class TranslatableContents implements ComponentContents {
    public static final Object[] NO_ARGS = new Object[0];
    private static final Codec<Object> PRIMITIVE_ARG_CODEC = ExtraCodecs.JAVA.validate(TranslatableContents::filterAllowedArguments);
    private static final Codec<Object> ARG_CODEC = Codec.either(PRIMITIVE_ARG_CODEC, ComponentSerialization.CODEC)
        .xmap(
            p_304564_ -> p_304564_.map(p_304446_ -> p_304446_, p_304596_ -> Objects.requireNonNullElse(p_304596_.tryCollapseToString(), p_304596_)),
            p_304615_ -> p_304615_ instanceof Component component ? Either.right(component) : Either.left(p_304615_)
        );
    public static final MapCodec<TranslatableContents> CODEC = RecordCodecBuilder.mapCodec(
        p_337512_ -> p_337512_.group(
                    Codec.STRING.fieldOf("translate").forGetter(p_304759_ -> p_304759_.key),
                    Codec.STRING.lenientOptionalFieldOf("fallback").forGetter(p_304865_ -> Optional.ofNullable(p_304865_.fallback)),
                    ARG_CODEC.listOf().optionalFieldOf("with").forGetter(p_304814_ -> adjustArgs(p_304814_.args))
                )
                .apply(p_337512_, TranslatableContents::create)
    );
    public static final ComponentContents.Type<TranslatableContents> TYPE = new ComponentContents.Type<>(CODEC, "translatable");
    private static final FormattedText TEXT_PERCENT = FormattedText.of("%");
    private static final FormattedText TEXT_NULL = FormattedText.of("null");
    private final String key;
    @Nullable
    private final String fallback;
    private final Object[] args;
    @Nullable
    private Language decomposedWith;
    /**
     * The discrete elements that make up this component. For example, this would be ["Prefix, ", "FirstArg", "SecondArg", " again ", "SecondArg", " and ", "FirstArg", " lastly ", "ThirdArg", " and also ", "FirstArg", " again!"] for "translation.test.complex" (see en_us.json)
     */
    private List<FormattedText> decomposedParts = ImmutableList.of();
    private static final Pattern FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private static DataResult<Object> filterAllowedArguments(@Nullable Object input) {
        return !isAllowedPrimitiveArgument(input) ? DataResult.error(() -> "This value needs to be parsed as component") : DataResult.success(input);
    }

    public static boolean isAllowedPrimitiveArgument(@Nullable Object input) {
        return input instanceof Number || input instanceof Boolean || input instanceof String;
    }

    private static Optional<List<Object>> adjustArgs(Object[] args) {
        return args.length == 0 ? Optional.empty() : Optional.of(Arrays.asList(args));
    }

    private static Object[] adjustArgs(Optional<List<Object>> args) {
        return args.<Object[]>map(p_304855_ -> p_304855_.isEmpty() ? NO_ARGS : p_304855_.toArray()).orElse(NO_ARGS);
    }

    private static TranslatableContents create(String key, Optional<String> fallback, Optional<List<Object>> args) {
        return new TranslatableContents(key, fallback.orElse(null), adjustArgs(args));
    }

    public TranslatableContents(String key, @Nullable String fallback, Object[] args) {
        this.key = key;
        this.fallback = fallback;
        this.args = args;
        if (!net.neoforged.fml.loading.FMLEnvironment.production) {
            for (Object arg : this.args) {
                if (!(arg instanceof Component) && !isAllowedPrimitiveArgument(arg)) {
                    throw new IllegalArgumentException("TranslatableContents' arguments must be either a Component, Number, Boolean, or a String. Was given " + arg + " for " + this.key);
                }
            }
        }
    }

    @Override
    public ComponentContents.Type<?> type() {
        return TYPE;
    }

    private void decompose() {
        Language language = Language.getInstance();
        if (language != this.decomposedWith) {
            this.decomposedWith = language;

            Component langComponent = language.getComponent(this.key);
            if (langComponent != null) {
                this.decomposedParts = ImmutableList.of(langComponent);
                return;
            }

            String s = this.fallback != null ? language.getOrDefault(this.key, this.fallback) : language.getOrDefault(this.key);

            try {
                Builder<FormattedText> builder = ImmutableList.builder();
                this.decomposeTemplate(s, builder::add);
                this.decomposedParts = builder.build();
            } catch (TranslatableFormatException translatableformatexception) {
                this.decomposedParts = ImmutableList.of(FormattedText.of(s));
            }
        }
    }

    private void decomposeTemplate(String formatTemplate, Consumer<FormattedText> consumer) {
        Matcher matcher = FORMAT_PATTERN.matcher(formatTemplate);

        try {
            int i = 0;
            int j = 0;

            while (matcher.find(j)) {
                int k = matcher.start();
                int l = matcher.end();
                if (k > j) {
                    String s = formatTemplate.substring(j, k);
                    if (s.indexOf(37) != -1) {
                        throw new IllegalArgumentException();
                    }

                    consumer.accept(FormattedText.of(s));
                }

                String s4 = matcher.group(2);
                String s1 = formatTemplate.substring(k, l);
                if ("%".equals(s4) && "%%".equals(s1)) {
                    consumer.accept(TEXT_PERCENT);
                } else {
                    if (!"s".equals(s4)) {
                        throw new TranslatableFormatException(this, "Unsupported format: '" + s1 + "'");
                    }

                    String s2 = matcher.group(1);
                    int i1 = s2 != null ? Integer.parseInt(s2) - 1 : i++;
                    consumer.accept(this.getArgument(i1));
                }

                j = l;
            }

            if (j < formatTemplate.length()) {
                String s3 = formatTemplate.substring(j);
                if (s3.indexOf(37) != -1) {
                    throw new IllegalArgumentException();
                }

                consumer.accept(FormattedText.of(s3));
            }
        } catch (IllegalArgumentException illegalargumentexception) {
            throw new TranslatableFormatException(this, illegalargumentexception);
        }
    }

    private FormattedText getArgument(int index) {
        if (index >= 0 && index < this.args.length) {
            Object object = this.args[index];
            if (object instanceof Component) {
                return (Component)object;
            } else {
                return object == null ? TEXT_NULL : FormattedText.of(object.toString());
            }
        } else {
            throw new TranslatableFormatException(this, index);
        }
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
        this.decompose();

        if (!net.neoforged.neoforge.common.util.InsertingContents.pushTranslation(this)) {
            // Reference cycle.
            return Optional.empty();
        }

        try {
        for (FormattedText formattedtext : this.decomposedParts) {
            Optional<T> optional = formattedtext.visit(styledContentConsumer, style);
            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
        } finally {
            net.neoforged.neoforge.common.util.InsertingContents.popTranslation();
        }
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
        this.decompose();

        if (!net.neoforged.neoforge.common.util.InsertingContents.pushTranslation(this)) {
            // Reference cycle.
            return Optional.empty();
        }

        try {
        for (FormattedText formattedtext : this.decomposedParts) {
            Optional<T> optional = formattedtext.visit(contentConsumer);
            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
        } finally {
            net.neoforged.neoforge.common.util.InsertingContents.popTranslation();
        }
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack nbtPathPattern, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        Object[] aobject = new Object[this.args.length];

        for (int i = 0; i < aobject.length; i++) {
            Object object = this.args[i];
            if (object instanceof Component component) {
                aobject[i] = ComponentUtils.updateForEntity(nbtPathPattern, component, entity, recursionDepth);
            } else {
                aobject[i] = object;
            }
        }

        return MutableComponent.create(new TranslatableContents(this.key, this.fallback, aobject));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            if (other instanceof TranslatableContents translatablecontents
                && Objects.equals(this.key, translatablecontents.key)
                && Objects.equals(this.fallback, translatablecontents.fallback)
                && Arrays.equals(this.args, translatablecontents.args)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = Objects.hashCode(this.key);
        i = 31 * i + Objects.hashCode(this.fallback);
        return 31 * i + Arrays.hashCode(this.args);
    }

    @Override
    public String toString() {
        return "translation{key='"
            + this.key
            + "'"
            + (this.fallback != null ? ", fallback='" + this.fallback + "'" : "")
            + ", args="
            + Arrays.toString(this.args)
            + "}";
    }

    public String getKey() {
        return this.key;
    }

    @Nullable
    public String getFallback() {
        return this.fallback;
    }

    public Object[] getArgs() {
        return this.args;
    }
}
