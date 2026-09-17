package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetWritableBookPagesFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetWritableBookPagesFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_335359_ -> commonFields(p_335359_)
                .and(
                    p_335359_.group(
                        WritableBookContent.PAGES_CODEC.fieldOf("pages").forGetter(p_333827_ -> p_333827_.pages),
                        ListOperation.codec(100).forGetter(p_334060_ -> p_334060_.pageOperation)
                    )
                )
                .apply(p_335359_, SetWritableBookPagesFunction::new)
    );
    private final List<Filterable<String>> pages;
    private final ListOperation pageOperation;

    public SetWritableBookPagesFunction(List<LootItemCondition> conditions, List<Filterable<String>> pages, ListOperation pageOperation) {
        super(conditions);
        this.pages = pages;
        this.pageOperation = pageOperation;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY, this::apply);
        return stack;
    }

    public WritableBookContent apply(WritableBookContent writableBookContent) {
        List<Filterable<String>> list = this.pageOperation.apply(writableBookContent.pages(), this.pages, 100);
        return writableBookContent.withReplacedPages(list);
    }

    @Override
    public LootItemFunctionType<SetWritableBookPagesFunction> getType() {
        return LootItemFunctions.SET_WRITABLE_BOOK_PAGES;
    }
}
