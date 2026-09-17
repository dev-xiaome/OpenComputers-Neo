package net.minecraft.client.searchtree;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FullTextSearchTree<T> extends IdSearchTree<T> {
    private final SearchTree<T> plainTextSearchTree;

    public FullTextSearchTree(Function<T, Stream<String>> filter, Function<T, Stream<ResourceLocation>> idGetter, List<T> contents) {
        super(idGetter, contents);
        this.plainTextSearchTree = SearchTree.plainText(contents, filter);
    }

    @Override
    protected List<T> searchPlainText(String query) {
        return this.plainTextSearchTree.search(query);
    }

    @Override
    protected List<T> searchResourceLocation(String namespace, String path) {
        List<T> list = this.resourceLocationSearchTree.searchNamespace(namespace);
        List<T> list1 = this.resourceLocationSearchTree.searchPath(path);
        List<T> list2 = this.plainTextSearchTree.search(path);
        Iterator<T> iterator = new MergingUniqueIterator<>(list1.iterator(), list2.iterator(), this.additionOrder);
        return ImmutableList.copyOf(new IntersectionIterator<>(list.iterator(), iterator, this.additionOrder));
    }
}
