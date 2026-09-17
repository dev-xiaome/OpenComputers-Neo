package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetBookCoverFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetBookCoverFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_338146_ -> commonFields(p_338146_)
                .and(
                    p_338146_.group(
                        Filterable.codec(Codec.string(0, 32)).optionalFieldOf("title").forGetter(p_333759_ -> p_333759_.title),
                        Codec.STRING.optionalFieldOf("author").forGetter(p_333817_ -> p_333817_.author),
                        ExtraCodecs.intRange(0, 3).optionalFieldOf("generation").forGetter(p_333964_ -> p_333964_.generation)
                    )
                )
                .apply(p_338146_, SetBookCoverFunction::new)
    );
    private final Optional<String> author;
    private final Optional<Filterable<String>> title;
    private final Optional<Integer> generation;

    public SetBookCoverFunction(
        List<LootItemCondition> conditons, Optional<Filterable<String>> title, Optional<String> author, Optional<Integer> generation
    ) {
        super(conditons);
        this.author = author;
        this.title = title;
        this.generation = generation;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY, this::apply);
        return stack;
    }

    private WrittenBookContent apply(WrittenBookContent writtenBookContent) {
        return new WrittenBookContent(
            this.title.orElseGet(writtenBookContent::title),
            this.author.orElseGet(writtenBookContent::author),
            this.generation.orElseGet(writtenBookContent::generation),
            writtenBookContent.pages(),
            writtenBookContent.resolved()
        );
    }

    @Override
    public LootItemFunctionType<SetBookCoverFunction> getType() {
        return LootItemFunctions.SET_BOOK_COVER;
    }
}
