package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetFireworkExplosionFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetFireworkExplosionFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_338149_ -> commonFields(p_338149_)
                .and(
                    p_338149_.group(
                        FireworkExplosion.Shape.CODEC.optionalFieldOf("shape").forGetter(p_333919_ -> p_333919_.shape),
                        FireworkExplosion.COLOR_LIST_CODEC.optionalFieldOf("colors").forGetter(p_333966_ -> p_333966_.colors),
                        FireworkExplosion.COLOR_LIST_CODEC.optionalFieldOf("fade_colors").forGetter(p_334021_ -> p_334021_.fadeColors),
                        Codec.BOOL.optionalFieldOf("trail").forGetter(p_334013_ -> p_334013_.trail),
                        Codec.BOOL.optionalFieldOf("twinkle").forGetter(p_333713_ -> p_333713_.twinkle)
                    )
                )
                .apply(p_338149_, SetFireworkExplosionFunction::new)
    );
    public static final FireworkExplosion DEFAULT_VALUE = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(), IntList.of(), false, false);
    final Optional<FireworkExplosion.Shape> shape;
    final Optional<IntList> colors;
    final Optional<IntList> fadeColors;
    final Optional<Boolean> trail;
    final Optional<Boolean> twinkle;

    public SetFireworkExplosionFunction(
        List<LootItemCondition> conditions,
        Optional<FireworkExplosion.Shape> shape,
        Optional<IntList> colors,
        Optional<IntList> fadeColors,
        Optional<Boolean> trail,
        Optional<Boolean> twinkle
    ) {
        super(conditions);
        this.shape = shape;
        this.colors = colors;
        this.fadeColors = fadeColors;
        this.trail = trail;
        this.twinkle = twinkle;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.FIREWORK_EXPLOSION, DEFAULT_VALUE, this::apply);
        return stack;
    }

    private FireworkExplosion apply(FireworkExplosion fireworkExplosion) {
        return new FireworkExplosion(
            this.shape.orElseGet(fireworkExplosion::shape),
            this.colors.orElseGet(fireworkExplosion::colors),
            this.fadeColors.orElseGet(fireworkExplosion::fadeColors),
            this.trail.orElseGet(fireworkExplosion::hasTrail),
            this.twinkle.orElseGet(fireworkExplosion::hasTwinkle)
        );
    }

    @Override
    public LootItemFunctionType<SetFireworkExplosionFunction> getType() {
        return LootItemFunctions.SET_FIREWORK_EXPLOSION;
    }
}
