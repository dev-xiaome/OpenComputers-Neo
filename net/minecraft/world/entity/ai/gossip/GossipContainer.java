package net.minecraft.world.entity.ai.gossip;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import org.slf4j.Logger;

public class GossipContainer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int DISCARD_THRESHOLD = 2;
    private final Map<UUID, GossipContainer.EntityGossips> gossips = Maps.newHashMap();

    @VisibleForDebug
    public Map<UUID, Object2IntMap<GossipType>> getGossipEntries() {
        Map<UUID, Object2IntMap<GossipType>> map = Maps.newHashMap();
        this.gossips.keySet().forEach(p_148167_ -> {
            GossipContainer.EntityGossips gossipcontainer$entitygossips = this.gossips.get(p_148167_);
            map.put(p_148167_, gossipcontainer$entitygossips.entries);
        });
        return map;
    }

    public void decay() {
        Iterator<GossipContainer.EntityGossips> iterator = this.gossips.values().iterator();

        while (iterator.hasNext()) {
            GossipContainer.EntityGossips gossipcontainer$entitygossips = iterator.next();
            gossipcontainer$entitygossips.decay();
            if (gossipcontainer$entitygossips.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private Stream<GossipContainer.GossipEntry> unpack() {
        return this.gossips.entrySet().stream().flatMap(p_26185_ -> p_26185_.getValue().unpack(p_26185_.getKey()));
    }

    private Collection<GossipContainer.GossipEntry> selectGossipsForTransfer(RandomSource random, int amount) {
        List<GossipContainer.GossipEntry> list = this.unpack().toList();
        if (list.isEmpty()) {
            return Collections.emptyList();
        } else {
            int[] aint = new int[list.size()];
            int i = 0;

            for (int j = 0; j < list.size(); j++) {
                GossipContainer.GossipEntry gossipcontainer$gossipentry = list.get(j);
                i += Math.abs(gossipcontainer$gossipentry.weightedValue());
                aint[j] = i - 1;
            }

            Set<GossipContainer.GossipEntry> set = Sets.newIdentityHashSet();

            for (int i1 = 0; i1 < amount; i1++) {
                int k = random.nextInt(i);
                int l = Arrays.binarySearch(aint, k);
                set.add(list.get(l < 0 ? -l - 1 : l));
            }

            return set;
        }
    }

    private GossipContainer.EntityGossips getOrCreate(UUID identifier) {
        return this.gossips.computeIfAbsent(identifier, p_26202_ -> new GossipContainer.EntityGossips());
    }

    public void transferFrom(GossipContainer container, RandomSource randomSource, int amount) {
        Collection<GossipContainer.GossipEntry> collection = container.selectGossipsForTransfer(randomSource, amount);
        collection.forEach(p_26200_ -> {
            int i = p_26200_.value - p_26200_.type.decayPerTransfer;
            if (i >= 2) {
                this.getOrCreate(p_26200_.target).entries.mergeInt(p_26200_.type, i, GossipContainer::mergeValuesForTransfer);
            }
        });
    }

    public int getReputation(UUID identifier, Predicate<GossipType> gossip) {
        GossipContainer.EntityGossips gossipcontainer$entitygossips = this.gossips.get(identifier);
        return gossipcontainer$entitygossips != null ? gossipcontainer$entitygossips.weightedValue(gossip) : 0;
    }

    public long getCountForType(GossipType gossipType, DoublePredicate gossipPredicate) {
        return this.gossips
            .values()
            .stream()
            .filter(p_148174_ -> gossipPredicate.test((double)(p_148174_.entries.getOrDefault(gossipType, 0) * gossipType.weight)))
            .count();
    }

    public void add(UUID identifier, GossipType gossipType, int gossipValue) {
        GossipContainer.EntityGossips gossipcontainer$entitygossips = this.getOrCreate(identifier);
        gossipcontainer$entitygossips.entries
            .mergeInt(gossipType, gossipValue, (p_186096_, p_186097_) -> this.mergeValuesForAddition(gossipType, p_186096_, p_186097_));
        gossipcontainer$entitygossips.makeSureValueIsntTooLowOrTooHigh(gossipType);
        if (gossipcontainer$entitygossips.isEmpty()) {
            this.gossips.remove(identifier);
        }
    }

    public void remove(UUID identifier, GossipType gossipType, int gossipValue) {
        this.add(identifier, gossipType, -gossipValue);
    }

    public void remove(UUID identifier, GossipType gossipType) {
        GossipContainer.EntityGossips gossipcontainer$entitygossips = this.gossips.get(identifier);
        if (gossipcontainer$entitygossips != null) {
            gossipcontainer$entitygossips.remove(gossipType);
            if (gossipcontainer$entitygossips.isEmpty()) {
                this.gossips.remove(identifier);
            }
        }
    }

    public void remove(GossipType gossipType) {
        Iterator<GossipContainer.EntityGossips> iterator = this.gossips.values().iterator();

        while (iterator.hasNext()) {
            GossipContainer.EntityGossips gossipcontainer$entitygossips = iterator.next();
            gossipcontainer$entitygossips.remove(gossipType);
            if (gossipcontainer$entitygossips.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public <T> T store(DynamicOps<T> ops) {
        return GossipContainer.GossipEntry.LIST_CODEC
            .encodeStart(ops, this.unpack().toList())
            .resultOrPartial(p_262900_ -> LOGGER.warn("Failed to serialize gossips: {}", p_262900_))
            .orElseGet(ops::emptyList);
    }

    public void update(Dynamic<?> dynamic) {
        GossipContainer.GossipEntry.LIST_CODEC
            .decode(dynamic)
            .resultOrPartial(p_262901_ -> LOGGER.warn("Failed to deserialize gossips: {}", p_262901_))
            .stream()
            .flatMap(p_262899_ -> p_262899_.getFirst().stream())
            .forEach(p_26162_ -> this.getOrCreate(p_26162_.target).entries.put(p_26162_.type, p_26162_.value));
    }

    /**
     * Returns the greater of two int values
     */
    private static int mergeValuesForTransfer(int value1, int value2) {
        return Math.max(value1, value2);
    }

    private int mergeValuesForAddition(GossipType gossipType, int existing, int additive) {
        int i = existing + additive;
        return i > gossipType.max ? Math.max(gossipType.max, existing) : i;
    }

    static class EntityGossips {
        final Object2IntMap<GossipType> entries = new Object2IntOpenHashMap<>();

        public int weightedValue(Predicate<GossipType> gossipType) {
            return this.entries
                .object2IntEntrySet()
                .stream()
                .filter(p_26224_ -> gossipType.test(p_26224_.getKey()))
                .mapToInt(p_26214_ -> p_26214_.getIntValue() * p_26214_.getKey().weight)
                .sum();
        }

        public Stream<GossipContainer.GossipEntry> unpack(UUID identifier) {
            return this.entries
                .object2IntEntrySet()
                .stream()
                .map(p_26219_ -> new GossipContainer.GossipEntry(identifier, p_26219_.getKey(), p_26219_.getIntValue()));
        }

        public void decay() {
            ObjectIterator<Entry<GossipType>> objectiterator = this.entries.object2IntEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Entry<GossipType> entry = objectiterator.next();
                int i = entry.getIntValue() - entry.getKey().decayPerDay;
                if (i < 2) {
                    objectiterator.remove();
                } else {
                    entry.setValue(i);
                }
            }
        }

        public boolean isEmpty() {
            return this.entries.isEmpty();
        }

        public void makeSureValueIsntTooLowOrTooHigh(GossipType gossipType) {
            int i = this.entries.getInt(gossipType);
            if (i > gossipType.max) {
                this.entries.put(gossipType, gossipType.max);
            }

            if (i < 2) {
                this.remove(gossipType);
            }
        }

        public void remove(GossipType gossipType) {
            this.entries.removeInt(gossipType);
        }
    }

    static record GossipEntry(UUID target, GossipType type, int value) {
        public static final Codec<GossipContainer.GossipEntry> CODEC = RecordCodecBuilder.create(
            p_263007_ -> p_263007_.group(
                        UUIDUtil.CODEC.fieldOf("Target").forGetter(GossipContainer.GossipEntry::target),
                        GossipType.CODEC.fieldOf("Type").forGetter(GossipContainer.GossipEntry::type),
                        ExtraCodecs.POSITIVE_INT.fieldOf("Value").forGetter(GossipContainer.GossipEntry::value)
                    )
                    .apply(p_263007_, GossipContainer.GossipEntry::new)
        );
        public static final Codec<List<GossipContainer.GossipEntry>> LIST_CODEC = CODEC.listOf();

        public int weightedValue() {
            return this.value * this.type.weight;
        }
    }
}
