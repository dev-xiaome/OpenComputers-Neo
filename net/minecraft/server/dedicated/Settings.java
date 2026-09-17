package net.minecraft.server.dedicated;

import com.google.common.base.MoreObjects;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.RegistryAccess;
import org.slf4j.Logger;

public abstract class Settings<T extends Settings<T>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final Properties properties;

    public Settings(Properties properties) {
        this.properties = properties;
    }

    public static Properties loadFromFile(Path path) {
        try {
            try {
                Properties properties3;
                try (InputStream inputstream = Files.newInputStream(path)) {
                    CharsetDecoder charsetdecoder = StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                    Properties properties2 = new Properties();
                    properties2.load(new InputStreamReader(inputstream, charsetdecoder));
                    properties3 = properties2;
                }

                return properties3;
            } catch (CharacterCodingException charactercodingexception) {
                LOGGER.info("Failed to load properties as UTF-8 from file {}, trying ISO_8859_1", path);

                Properties properties1;
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
                    Properties properties = new Properties();
                    properties.load(reader);
                    properties1 = properties;
                }

                return properties1;
            }
        } catch (IOException ioexception) {
            LOGGER.error("Failed to load properties from file: {}", path, ioexception);
            return new Properties();
        }
    }

    public void store(Path path) {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            net.neoforged.neoforge.common.util.SortedProperties.store(this.properties, writer, "Minecraft server properties");
        } catch (IOException ioexception) {
            LOGGER.error("Failed to store properties to file: {}", path);
        }
    }

    private static <V extends Number> Function<String, V> wrapNumberDeserializer(Function<String, V> parseFunc) {
        return p_139845_ -> {
            try {
                return parseFunc.apply(p_139845_);
            } catch (NumberFormatException numberformatexception) {
                return null;
            }
        };
    }

    protected static <V> Function<String, V> dispatchNumberOrString(IntFunction<V> byId, Function<String, V> byName) {
        return p_139856_ -> {
            try {
                return byId.apply(Integer.parseInt(p_139856_));
            } catch (NumberFormatException numberformatexception) {
                return byName.apply(p_139856_);
            }
        };
    }

    @Nullable
    private String getStringRaw(String key) {
        return (String)this.properties.get(key);
    }

    @Nullable
    protected <V> V getLegacy(String key, Function<String, V> serializer) {
        String s = this.getStringRaw(key);
        if (s == null) {
            return null;
        } else {
            this.properties.remove(key);
            return serializer.apply(s);
        }
    }

    protected <V> V get(String key, Function<String, V> serializer, Function<V, String> deserializer, V defaultValue) {
        String s = this.getStringRaw(key);
        V v = MoreObjects.firstNonNull(s != null ? serializer.apply(s) : null, defaultValue);
        this.properties.put(key, deserializer.apply(v));
        return v;
    }

    protected <V> Settings<T>.MutableValue<V> getMutable(String key, Function<String, V> serializer, Function<V, String> deserializer, V defaultValue) {
        String s = this.getStringRaw(key);
        V v = MoreObjects.firstNonNull(s != null ? serializer.apply(s) : null, defaultValue);
        this.properties.put(key, deserializer.apply(v));
        return this.new MutableValue<V>(key, v, deserializer);
    }

    protected <V> V get(String key, Function<String, V> serializer, UnaryOperator<V> modifier, Function<V, String> deserializer, V defaultValue) {
        return this.get(key, p_139849_ -> {
            V v = serializer.apply(p_139849_);
            return v != null ? modifier.apply(v) : null;
        }, deserializer, defaultValue);
    }

    protected <V> V get(String key, Function<String, V> mapper, V value) {
        return this.get(key, mapper, Objects::toString, value);
    }

    protected <V> Settings<T>.MutableValue<V> getMutable(String key, Function<String, V> serializer, V defaultValue) {
        return this.getMutable(key, serializer, Objects::toString, defaultValue);
    }

    protected String get(String key, String defaultValue) {
        return this.get(key, Function.identity(), Function.identity(), defaultValue);
    }

    @Nullable
    protected String getLegacyString(String key) {
        return this.getLegacy(key, Function.identity());
    }

    protected int get(String key, int defaultValue) {
        return this.get(key, wrapNumberDeserializer(Integer::parseInt), Integer.valueOf(defaultValue));
    }

    protected Settings<T>.MutableValue<Integer> getMutable(String key, int defaultValue) {
        return this.getMutable(key, wrapNumberDeserializer(Integer::parseInt), defaultValue);
    }

    protected int get(String key, UnaryOperator<Integer> modifier, int defaultValue) {
        return this.get(key, wrapNumberDeserializer(Integer::parseInt), modifier, Objects::toString, defaultValue);
    }

    protected long get(String key, long defaultValue) {
        return this.get(key, wrapNumberDeserializer(Long::parseLong), defaultValue);
    }

    protected boolean get(String key, boolean defaultValue) {
        return this.get(key, Boolean::valueOf, defaultValue);
    }

    protected Settings<T>.MutableValue<Boolean> getMutable(String key, boolean defaultValue) {
        return this.getMutable(key, Boolean::valueOf, defaultValue);
    }

    @Nullable
    protected Boolean getLegacyBoolean(String key) {
        return this.getLegacy(key, Boolean::valueOf);
    }

    protected Properties cloneProperties() {
        Properties properties = new Properties();
        properties.putAll(this.properties);
        return properties;
    }

    protected abstract T reload(RegistryAccess registryAccess, Properties properties);

    public class MutableValue<V> implements Supplier<V> {
        private final String key;
        private final V value;
        private final Function<V, String> serializer;

        MutableValue(String key, V value, Function<V, String> serializer) {
            this.key = key;
            this.value = value;
            this.serializer = serializer;
        }

        @Override
        public V get() {
            return this.value;
        }

        public T update(RegistryAccess registryAccess, V newValue) {
            Properties properties = Settings.this.cloneProperties();
            properties.put(this.key, this.serializer.apply(newValue));
            return Settings.this.reload(registryAccess, properties);
        }
    }
}
