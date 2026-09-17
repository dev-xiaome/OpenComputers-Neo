package net.minecraft.client.searchtree;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IdSearchTree<T> implements SearchTree<T> {
    protected final Comparator<T> additionOrder;
    protected final ResourceLocationSearchTree<T> resourceLocationSearchTree;

    public IdSearchTree(Function<T, Stream<ResourceLocation>> idGetter, List<T> contents) {
        ToIntFunction<T> tointfunction = Util.createIndexLookup(contents);
        this.additionOrder = Comparator.comparingInt(tointfunction);
        this.resourceLocationSearchTree = ResourceLocationSearchTree.create(contents, idGetter);
    }

    /**
     * Searches this search tree for the given text.
     * <p>
     * If the query does not contain a {@code :}, then only {@link #byName} is searched. If it does contain a colon, both {@link #byName} and {@link #byId} are searched and the results are merged using a {@link MergingIterator}.
     * @return A list of all matching items in this search tree.
     */
    @Override
    public List<T> search(String query) {
        int i = query.indexOf(58);
        return i == -1 ? this.searchPlainText(query) : this.searchResourceLocation(query.substring(0, i).trim(), query.substring(i + 1).trim());
    }

    protected List<T> searchPlainText(String query) {
        return this.resourceLocationSearchTree.searchPath(query);
    }

    protected List<T> searchResourceLocation(String namespace, String path) {
        List<T> list = this.resourceLocationSearchTree.searchNamespace(namespace);
        List<T> list1 = this.resourceLocationSearchTree.searchPath(path);
        return ImmutableList.copyOf(new IntersectionIterator<>(list.iterator(), list1.iterator(), this.additionOrder));
    }
}
