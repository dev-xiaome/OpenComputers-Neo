package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.ScoreHolder;

public class ScoreHolderArgument implements ArgumentType<ScoreHolderArgument.Result> {
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_SCORE_HOLDERS = (p_353117_, p_353118_) -> {
        StringReader stringreader = new StringReader(p_353118_.getInput());
        stringreader.setCursor(p_353118_.getStart());
        EntitySelectorParser entityselectorparser = new EntitySelectorParser(stringreader, EntitySelectorParser.allowSelectors(p_353117_.getSource()));

        try {
            entityselectorparser.parse();
        } catch (CommandSyntaxException commandsyntaxexception) {
        }

        return entityselectorparser.fillSuggestions(
            p_353118_, p_171606_ -> SharedSuggestionProvider.suggest(p_353117_.getSource().getOnlinePlayerNames(), p_171606_)
        );
    };
    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "*", "@e");
    private static final SimpleCommandExceptionType ERROR_NO_RESULTS = new SimpleCommandExceptionType(Component.translatable("argument.scoreHolder.empty"));
    final boolean multiple;

    public ScoreHolderArgument(boolean multiple) {
        this.multiple = multiple;
    }

    public static ScoreHolder getName(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getNames(context, name).iterator().next();
    }

    /**
     * Gets one or more score holders, with no objectives list.
     */
    public static Collection<ScoreHolder> getNames(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getNames(context, name, Collections::emptyList);
    }

    /**
     * Gets one or more score holders, using the server's complete list of objectives.
     */
    public static Collection<ScoreHolder> getNamesWithDefaultWildcard(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getNames(context, name, context.getSource().getServer().getScoreboard()::getTrackedPlayers);
    }

    /**
     * Gets one or more score holders.
     */
    public static Collection<ScoreHolder> getNames(CommandContext<CommandSourceStack> context, String name, Supplier<Collection<ScoreHolder>> objectives) throws CommandSyntaxException {
        Collection<ScoreHolder> collection = context.getArgument(name, ScoreHolderArgument.Result.class).getNames(context.getSource(), objectives);
        if (collection.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else {
            return collection;
        }
    }

    public static ScoreHolderArgument scoreHolder() {
        return new ScoreHolderArgument(false);
    }

    public static ScoreHolderArgument scoreHolders() {
        return new ScoreHolderArgument(true);
    }

    public ScoreHolderArgument.Result parse(StringReader reader) throws CommandSyntaxException {
        return this.parse(reader, true);
    }

    public <S> ScoreHolderArgument.Result parse(StringReader reader, S p_353126_) throws CommandSyntaxException {
        return this.parse(reader, EntitySelectorParser.allowSelectors(p_353126_));
    }

    private ScoreHolderArgument.Result parse(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            EntitySelectorParser entityselectorparser = new EntitySelectorParser(reader, allowSelectors);
            EntitySelector entityselector = entityselectorparser.parse();
            if (!this.multiple && entityselector.getMaxResults() > 1) {
                throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.createWithContext(reader);
            } else {
                return new ScoreHolderArgument.SelectorResult(entityselector);
            }
        } else {
            int i = reader.getCursor();

            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }

            String s = reader.getString().substring(i, reader.getCursor());
            if (s.equals("*")) {
                return (p_108231_, p_108232_) -> {
                    Collection<ScoreHolder> collection = p_108232_.get();
                    if (collection.isEmpty()) {
                        throw ERROR_NO_RESULTS.create();
                    } else {
                        return collection;
                    }
                };
            } else {
                List<ScoreHolder> list = List.of(ScoreHolder.forNameOnly(s));
                if (s.startsWith("#")) {
                    return (p_108237_, p_108238_) -> list;
                } else {
                    try {
                        UUID uuid = UUID.fromString(s);
                        return (p_314703_, p_314704_) -> {
                            MinecraftServer minecraftserver = p_314703_.getServer();
                            ScoreHolder scoreholder = null;
                            List<ScoreHolder> list1 = null;

                            for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
                                Entity entity = serverlevel.getEntity(uuid);
                                if (entity != null) {
                                    if (scoreholder == null) {
                                        scoreholder = entity;
                                    } else {
                                        if (list1 == null) {
                                            list1 = new ArrayList<>();
                                            list1.add(scoreholder);
                                        }

                                        list1.add(entity);
                                    }
                                }
                            }

                            if (list1 != null) {
                                return list1;
                            } else {
                                return scoreholder != null ? List.of(scoreholder) : list;
                            }
                        };
                    } catch (IllegalArgumentException illegalargumentexception) {
                        return (p_314699_, p_314700_) -> {
                            MinecraftServer minecraftserver = p_314699_.getServer();
                            ServerPlayer serverplayer = minecraftserver.getPlayerList().getPlayerByName(s);
                            return serverplayer != null ? List.of(serverplayer) : list;
                        };
                    }
                }
            }
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Info implements ArgumentTypeInfo<ScoreHolderArgument, ScoreHolderArgument.Info.Template> {
        private static final byte FLAG_MULTIPLE = 1;

        public void serializeToNetwork(ScoreHolderArgument.Info.Template template, FriendlyByteBuf buffer) {
            int i = 0;
            if (template.multiple) {
                i |= 1;
            }

            buffer.writeByte(i);
        }

        public ScoreHolderArgument.Info.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            byte b0 = buffer.readByte();
            boolean flag = (b0 & 1) != 0;
            return new ScoreHolderArgument.Info.Template(flag);
        }

        public void serializeToJson(ScoreHolderArgument.Info.Template template, JsonObject json) {
            json.addProperty("amount", template.multiple ? "multiple" : "single");
        }

        public ScoreHolderArgument.Info.Template unpack(ScoreHolderArgument argument) {
            return new ScoreHolderArgument.Info.Template(argument.multiple);
        }

        public final class Template implements ArgumentTypeInfo.Template<ScoreHolderArgument> {
            final boolean multiple;

            Template(boolean multiple) {
                this.multiple = multiple;
            }

            public ScoreHolderArgument instantiate(CommandBuildContext context) {
                return new ScoreHolderArgument(this.multiple);
            }

            @Override
            public ArgumentTypeInfo<ScoreHolderArgument, ?> type() {
                return Info.this;
            }
        }
    }

    @FunctionalInterface
    public interface Result {
        Collection<ScoreHolder> getNames(CommandSourceStack source, Supplier<Collection<ScoreHolder>> objectives) throws CommandSyntaxException;
    }

    public static class SelectorResult implements ScoreHolderArgument.Result {
        private final EntitySelector selector;

        public SelectorResult(EntitySelector selector) {
            this.selector = selector;
        }

        @Override
        public Collection<ScoreHolder> getNames(CommandSourceStack source, Supplier<Collection<ScoreHolder>> objectives) throws CommandSyntaxException {
            List<? extends Entity> list = this.selector.findEntities(source);
            if (list.isEmpty()) {
                throw EntityArgument.NO_ENTITIES_FOUND.create();
            } else {
                return List.copyOf(list);
            }
        }
    }
}
