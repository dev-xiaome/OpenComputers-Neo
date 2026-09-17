package net.minecraft.commands.arguments.item;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.Term;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.util.parsing.packrat.commands.ResourceLocationParseRule;
import net.minecraft.util.parsing.packrat.commands.ResourceLookupRule;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;
import net.minecraft.util.parsing.packrat.commands.TagParseRule;

public class ComponentPredicateParser {
    public static <T, C, P> Grammar<List<T>> createGrammar(ComponentPredicateParser.Context<T, C, P> context) {
        Atom<List<T>> atom = Atom.of("top");
        Atom<Optional<T>> atom1 = Atom.of("type");
        Atom<Unit> atom2 = Atom.of("any_type");
        Atom<T> atom3 = Atom.of("element_type");
        Atom<T> atom4 = Atom.of("tag_type");
        Atom<List<T>> atom5 = Atom.of("conditions");
        Atom<List<T>> atom6 = Atom.of("alternatives");
        Atom<T> atom7 = Atom.of("term");
        Atom<T> atom8 = Atom.of("negation");
        Atom<T> atom9 = Atom.of("test");
        Atom<C> atom10 = Atom.of("component_type");
        Atom<P> atom11 = Atom.of("predicate_type");
        Atom<ResourceLocation> atom12 = Atom.of("id");
        Atom<Tag> atom13 = Atom.of("tag");
        Dictionary<StringReader> dictionary = new Dictionary<>();
        dictionary.put(
            atom,
            Term.alternative(
                Term.sequence(
                    Term.named(atom1), StringReaderTerms.character('['), Term.cut(), Term.optional(Term.named(atom5)), StringReaderTerms.character(']')
                ),
                Term.named(atom1)
            ),
            p_336103_ -> {
                Builder<T> builder = ImmutableList.builder();
                p_336103_.getOrThrow(atom1).ifPresent(builder::add);
                List<T> list = p_336103_.get(atom5);
                if (list != null) {
                    builder.addAll(list);
                }

                return builder.build();
            }
        );
        dictionary.put(
            atom1,
            Term.alternative(Term.named(atom3), Term.sequence(StringReaderTerms.character('#'), Term.cut(), Term.named(atom4)), Term.named(atom2)),
            p_335800_ -> Optional.ofNullable(p_335800_.getAny(atom3, atom4))
        );
        dictionary.put(atom2, StringReaderTerms.character('*'), p_335962_ -> Unit.INSTANCE);
        dictionary.put(atom3, new ComponentPredicateParser.ElementLookupRule<>(atom12, context));
        dictionary.put(atom4, new ComponentPredicateParser.TagLookupRule<>(atom12, context));
        dictionary.put(
            atom5, Term.sequence(Term.named(atom6), Term.optional(Term.sequence(StringReaderTerms.character(','), Term.named(atom5)))), p_336093_ -> {
                T t = context.anyOf(p_336093_.getOrThrow(atom6));
                return Optional.ofNullable(p_336093_.get(atom5)).map(p_335806_ -> Util.copyAndAdd(t, (List<T>)p_335806_)).orElse(List.of(t));
            }
        );
        dictionary.put(
            atom6, Term.sequence(Term.named(atom7), Term.optional(Term.sequence(StringReaderTerms.character('|'), Term.named(atom6)))), p_335542_ -> {
                T t = p_335542_.getOrThrow(atom7);
                return Optional.ofNullable(p_335542_.get(atom6)).map(p_335939_ -> Util.copyAndAdd(t, (List<T>)p_335939_)).orElse(List.of(t));
            }
        );
        dictionary.put(
            atom7,
            Term.alternative(Term.named(atom9), Term.sequence(StringReaderTerms.character('!'), Term.named(atom8))),
            p_335647_ -> p_335647_.getAnyOrThrow(atom9, atom8)
        );
        dictionary.put(atom8, Term.named(atom9), p_335793_ -> context.negate(p_335793_.getOrThrow(atom9)));
        dictionary.put(
            atom9,
            Term.alternative(
                Term.sequence(Term.named(atom10), StringReaderTerms.character('='), Term.cut(), Term.named(atom13)),
                Term.sequence(Term.named(atom11), StringReaderTerms.character('~'), Term.cut(), Term.named(atom13)),
                Term.named(atom10)
            ),
            (p_335570_, p_336062_) -> {
                P p = p_336062_.get(atom11);

                try {
                    if (p != null) {
                        Tag tag1 = p_336062_.getOrThrow(atom13);
                        return Optional.of(context.createPredicateTest(p_335570_.input(), p, tag1));
                    } else {
                        C c = p_336062_.getOrThrow(atom10);
                        Tag tag = p_336062_.get(atom13);
                        return Optional.of(
                            tag != null ? context.createComponentTest(p_335570_.input(), c, tag) : context.createComponentTest(p_335570_.input(), c)
                        );
                    }
                } catch (CommandSyntaxException commandsyntaxexception) {
                    p_335570_.errorCollector().store(p_335570_.mark(), commandsyntaxexception);
                    return Optional.empty();
                }
            }
        );
        dictionary.put(atom10, new ComponentPredicateParser.ComponentLookupRule<>(atom12, context));
        dictionary.put(atom11, new ComponentPredicateParser.PredicateLookupRule<>(atom12, context));
        dictionary.put(atom13, TagParseRule.INSTANCE);
        dictionary.put(atom12, ResourceLocationParseRule.INSTANCE);
        return new Grammar<>(dictionary, atom);
    }

    static class ComponentLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, C> {
        ComponentLookupRule(Atom<ResourceLocation> idParser, ComponentPredicateParser.Context<T, C, P> context) {
            super(idParser, context);
        }

        @Override
        protected C validateElement(ImmutableStringReader reader, ResourceLocation elementType) throws Exception {
            return this.context.lookupComponentType(reader, elementType);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listComponentTypes();
        }
    }

    public interface Context<T, C, P> {
        T forElementType(ImmutableStringReader reader, ResourceLocation elementType) throws CommandSyntaxException;

        Stream<ResourceLocation> listElementTypes();

        T forTagType(ImmutableStringReader reader, ResourceLocation tagType) throws CommandSyntaxException;

        Stream<ResourceLocation> listTagTypes();

        C lookupComponentType(ImmutableStringReader reader, ResourceLocation componentType) throws CommandSyntaxException;

        Stream<ResourceLocation> listComponentTypes();

        T createComponentTest(ImmutableStringReader reader, C context, Tag value) throws CommandSyntaxException;

        T createComponentTest(ImmutableStringReader reader, C context);

        P lookupPredicateType(ImmutableStringReader reader, ResourceLocation predicateType) throws CommandSyntaxException;

        Stream<ResourceLocation> listPredicateTypes();

        T createPredicateTest(ImmutableStringReader reader, P predicate, Tag value) throws CommandSyntaxException;

        T negate(T value);

        T anyOf(List<T> values);
    }

    static class ElementLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, T> {
        ElementLookupRule(Atom<ResourceLocation> idParser, ComponentPredicateParser.Context<T, C, P> context) {
            super(idParser, context);
        }

        @Override
        protected T validateElement(ImmutableStringReader reader, ResourceLocation elementType) throws Exception {
            return this.context.forElementType(reader, elementType);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listElementTypes();
        }
    }

    static class PredicateLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, P> {
        PredicateLookupRule(Atom<ResourceLocation> idParser, ComponentPredicateParser.Context<T, C, P> context) {
            super(idParser, context);
        }

        @Override
        protected P validateElement(ImmutableStringReader reader, ResourceLocation elementType) throws Exception {
            return this.context.lookupPredicateType(reader, elementType);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listPredicateTypes();
        }
    }

    static class TagLookupRule<T, C, P> extends ResourceLookupRule<ComponentPredicateParser.Context<T, C, P>, T> {
        TagLookupRule(Atom<ResourceLocation> idParser, ComponentPredicateParser.Context<T, C, P> context) {
            super(idParser, context);
        }

        @Override
        protected T validateElement(ImmutableStringReader reader, ResourceLocation elementType) throws Exception {
            return this.context.forTagType(reader, elementType);
        }

        @Override
        public Stream<ResourceLocation> possibleResources() {
            return this.context.listTagTypes();
        }
    }
}
