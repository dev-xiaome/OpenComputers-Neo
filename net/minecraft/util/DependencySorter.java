package net.minecraft.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DependencySorter<K, V extends DependencySorter.Entry<K>> {
    private final Map<K, V> contents = new HashMap<>();

    public DependencySorter<K, V> addEntry(K key, V value) {
        this.contents.put(key, value);
        return this;
    }

    private void visitDependenciesAndElement(Multimap<K, K> dependencies, Set<K> visited, K element, BiConsumer<K, V> action) {
        if (visited.add(element)) {
            dependencies.get(element).forEach(p_285443_ -> this.visitDependenciesAndElement(dependencies, visited, (K)p_285443_, action));
            V v = this.contents.get(element);
            if (v != null) {
                action.accept(element, v);
            }
        }
    }

    private static <K> boolean isCyclic(Multimap<K, K> dependencies, K source, K target) {
        Collection<K> collection = dependencies.get(target);
        return collection.contains(source) ? true : collection.stream().anyMatch(p_284974_ -> isCyclic(dependencies, source, (K)p_284974_));
    }

    private static <K> void addDependencyIfNotCyclic(Multimap<K, K> dependencies, K source, K target) {
        if (!isCyclic(dependencies, source, target)) {
            dependencies.put(source, target);
        }
    }

    public void orderByDependencies(BiConsumer<K, V> action) {
        Multimap<K, K> multimap = HashMultimap.create();
        this.contents
            .forEach((p_285415_, p_285018_) -> p_285018_.visitRequiredDependencies(p_285287_ -> addDependencyIfNotCyclic(multimap, (K)p_285415_, p_285287_)));
        this.contents
            .forEach((p_285462_, p_285526_) -> p_285526_.visitOptionalDependencies(p_285513_ -> addDependencyIfNotCyclic(multimap, (K)p_285462_, p_285513_)));
        Set<K> set = new HashSet<>();
        this.contents.keySet().forEach(p_284996_ -> this.visitDependenciesAndElement(multimap, set, (K)p_284996_, action));
    }

    public interface Entry<K> {
        void visitRequiredDependencies(Consumer<K> visitor);

        void visitOptionalDependencies(Consumer<K> visitor);
    }
}
