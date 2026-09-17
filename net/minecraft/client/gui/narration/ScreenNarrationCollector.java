package net.minecraft.client.gui.narration;

import com.google.common.collect.Maps;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ScreenNarrationCollector {
    int generation;
    final Map<ScreenNarrationCollector.EntryKey, ScreenNarrationCollector.NarrationEntry> entries = Maps.newTreeMap(
        Comparator.<ScreenNarrationCollector.EntryKey, NarratedElementType>comparing(p_169196_ -> p_169196_.type).thenComparing(p_169185_ -> p_169185_.depth)
    );

    public void update(Consumer<NarrationElementOutput> updater) {
        this.generation++;
        updater.accept(new ScreenNarrationCollector.Output(0));
    }

    public String collectNarrationText(boolean collectAll) {
        final StringBuilder stringbuilder = new StringBuilder();
        Consumer<String> consumer = new Consumer<String>() {
            private boolean firstEntry = true;

            public void accept(String str) {
                if (!this.firstEntry) {
                    stringbuilder.append(". ");
                }

                this.firstEntry = false;
                stringbuilder.append(str);
            }
        };
        this.entries.forEach((p_169193_, p_169194_) -> {
            if (p_169194_.generation == this.generation && (collectAll || !p_169194_.alreadyNarrated)) {
                p_169194_.contents.getText(consumer);
                p_169194_.alreadyNarrated = true;
            }
        });
        return stringbuilder.toString();
    }

    @OnlyIn(Dist.CLIENT)
    static class EntryKey {
        final NarratedElementType type;
        final int depth;

        EntryKey(NarratedElementType type, int depth) {
            this.type = type;
            this.depth = depth;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class NarrationEntry {
        NarrationThunk<?> contents = NarrationThunk.EMPTY;
        int generation = -1;
        boolean alreadyNarrated;

        public ScreenNarrationCollector.NarrationEntry update(int generation, NarrationThunk<?> contents) {
            if (!this.contents.equals(contents)) {
                this.contents = contents;
                this.alreadyNarrated = false;
            } else if (this.generation + 1 != generation) {
                this.alreadyNarrated = false;
            }

            this.generation = generation;
            return this;
        }
    }

    @OnlyIn(Dist.CLIENT)
    class Output implements NarrationElementOutput {
        private final int depth;

        Output(int depth) {
            this.depth = depth;
        }

        @Override
        public void add(NarratedElementType type, NarrationThunk<?> contents) {
            ScreenNarrationCollector.this.entries
                .computeIfAbsent(new ScreenNarrationCollector.EntryKey(type, this.depth), p_169229_ -> new ScreenNarrationCollector.NarrationEntry())
                .update(ScreenNarrationCollector.this.generation, contents);
        }

        @Override
        public NarrationElementOutput nest() {
            return ScreenNarrationCollector.this.new Output(this.depth + 1);
        }
    }
}
