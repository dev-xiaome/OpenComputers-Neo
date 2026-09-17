package net.minecraft.server.commands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.HeightmapTypeArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.SlotsArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.SwizzleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.execution.tasks.IsolatedCall;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

public class ExecuteCommand {
    private static final int MAX_TEST_AREA = 32768;
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType(
        (p_304211_, p_304212_) -> Component.translatableEscape("commands.execute.blocks.toobig", p_304211_, p_304212_)
    );
    private static final SimpleCommandExceptionType ERROR_CONDITIONAL_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.execute.conditional.fail")
    );
    private static final DynamicCommandExceptionType ERROR_CONDITIONAL_FAILED_COUNT = new DynamicCommandExceptionType(
        p_304210_ -> Component.translatableEscape("commands.execute.conditional.fail_count", p_304210_)
    );
    @VisibleForTesting
    public static final Dynamic2CommandExceptionType ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE = new Dynamic2CommandExceptionType(
        (p_305676_, p_305677_) -> Component.translatableEscape("commands.execute.function.instantiationFailure", p_305676_, p_305677_)
    );
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PREDICATE = (p_335218_, p_335219_) -> {
        ReloadableServerRegistries.Holder reloadableserverregistries$holder = p_335218_.getSource().getServer().reloadableRegistries();
        return SharedSuggestionProvider.suggestResource(reloadableserverregistries$holder.getKeys(Registries.PREDICATE), p_335219_);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = dispatcher.register(
            Commands.literal("execute").requires(p_137197_ -> p_137197_.hasPermission(2))
        );
        dispatcher.register(
            Commands.literal("execute")
                .requires(p_137103_ -> p_137103_.hasPermission(2))
                .then(Commands.literal("run").redirect(dispatcher.getRoot()))
                .then(addConditionals(literalcommandnode, Commands.literal("if"), true, context))
                .then(addConditionals(literalcommandnode, Commands.literal("unless"), false, context))
                .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalcommandnode, p_137299_ -> {
                    List<CommandSourceStack> list = Lists.newArrayList();

                    for (Entity entity : EntityArgument.getOptionalEntities(p_137299_, "targets")) {
                        list.add(p_137299_.getSource().withEntity(entity));
                    }

                    return list;
                })))
                .then(
                    Commands.literal("at")
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .fork(
                                    literalcommandnode,
                                    p_284653_ -> {
                                        List<CommandSourceStack> list = Lists.newArrayList();

                                        for (Entity entity : EntityArgument.getOptionalEntities(p_284653_, "targets")) {
                                            list.add(
                                                p_284653_.getSource()
                                                    .withLevel((ServerLevel)entity.level())
                                                    .withPosition(entity.position())
                                                    .withRotation(entity.getRotationVector())
                                            );
                                        }

                                        return list;
                                    }
                                )
                        )
                )
                .then(
                    Commands.literal("store")
                        .then(wrapStores(literalcommandnode, Commands.literal("result"), true))
                        .then(wrapStores(literalcommandnode, Commands.literal("success"), false))
                )
                .then(
                    Commands.literal("positioned")
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .redirect(
                                    literalcommandnode,
                                    p_137295_ -> p_137295_.getSource()
                                            .withPosition(Vec3Argument.getVec3(p_137295_, "pos"))
                                            .withAnchor(EntityAnchorArgument.Anchor.FEET)
                                )
                        )
                        .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalcommandnode, p_137293_ -> {
                            List<CommandSourceStack> list = Lists.newArrayList();

                            for (Entity entity : EntityArgument.getOptionalEntities(p_137293_, "targets")) {
                                list.add(p_137293_.getSource().withPosition(entity.position()));
                            }

                            return list;
                        })))
                        .then(
                            Commands.literal("over")
                                .then(Commands.argument("heightmap", HeightmapTypeArgument.heightmap()).redirect(literalcommandnode, p_274814_ -> {
                                    Vec3 vec3 = p_274814_.getSource().getPosition();
                                    ServerLevel serverlevel = p_274814_.getSource().getLevel();
                                    double d0 = vec3.x();
                                    double d1 = vec3.z();
                                    if (!serverlevel.hasChunk(SectionPos.blockToSectionCoord(d0), SectionPos.blockToSectionCoord(d1))) {
                                        throw BlockPosArgument.ERROR_NOT_LOADED.create();
                                    } else {
                                        int i = serverlevel.getHeight(HeightmapTypeArgument.getHeightmap(p_274814_, "heightmap"), Mth.floor(d0), Mth.floor(d1));
                                        return p_274814_.getSource().withPosition(new Vec3(d0, (double)i, d1));
                                    }
                                }))
                        )
                )
                .then(
                    Commands.literal("rotated")
                        .then(
                            Commands.argument("rot", RotationArgument.rotation())
                                .redirect(
                                    literalcommandnode,
                                    p_137291_ -> p_137291_.getSource()
                                            .withRotation(RotationArgument.getRotation(p_137291_, "rot").getRotation(p_137291_.getSource()))
                                )
                        )
                        .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalcommandnode, p_137289_ -> {
                            List<CommandSourceStack> list = Lists.newArrayList();

                            for (Entity entity : EntityArgument.getOptionalEntities(p_137289_, "targets")) {
                                list.add(p_137289_.getSource().withRotation(entity.getRotationVector()));
                            }

                            return list;
                        })))
                )
                .then(
                    Commands.literal("facing")
                        .then(
                            Commands.literal("entity")
                                .then(
                                    Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("anchor", EntityAnchorArgument.anchor()).fork(literalcommandnode, p_137287_ -> {
                                            List<CommandSourceStack> list = Lists.newArrayList();
                                            EntityAnchorArgument.Anchor entityanchorargument$anchor = EntityAnchorArgument.getAnchor(p_137287_, "anchor");

                                            for (Entity entity : EntityArgument.getOptionalEntities(p_137287_, "targets")) {
                                                list.add(p_137287_.getSource().facing(entity, entityanchorargument$anchor));
                                            }

                                            return list;
                                        }))
                                )
                        )
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .redirect(literalcommandnode, p_137285_ -> p_137285_.getSource().facing(Vec3Argument.getVec3(p_137285_, "pos")))
                        )
                )
                .then(
                    Commands.literal("align")
                        .then(
                            Commands.argument("axes", SwizzleArgument.swizzle())
                                .redirect(
                                    literalcommandnode,
                                    p_137283_ -> p_137283_.getSource()
                                            .withPosition(p_137283_.getSource().getPosition().align(SwizzleArgument.getSwizzle(p_137283_, "axes")))
                                )
                        )
                )
                .then(
                    Commands.literal("anchored")
                        .then(
                            Commands.argument("anchor", EntityAnchorArgument.anchor())
                                .redirect(
                                    literalcommandnode, p_137281_ -> p_137281_.getSource().withAnchor(EntityAnchorArgument.getAnchor(p_137281_, "anchor"))
                                )
                        )
                )
                .then(
                    Commands.literal("in")
                        .then(
                            Commands.argument("dimension", DimensionArgument.dimension())
                                .redirect(
                                    literalcommandnode, p_137279_ -> p_137279_.getSource().withLevel(DimensionArgument.getDimension(p_137279_, "dimension"))
                                )
                        )
                )
                .then(
                    Commands.literal("summon")
                        .then(
                            Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                                .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                                .redirect(
                                    literalcommandnode,
                                    p_269759_ -> spawnEntityAndRedirect(p_269759_.getSource(), ResourceArgument.getSummonableEntityType(p_269759_, "entity"))
                                )
                        )
                )
                .then(createRelationOperations(literalcommandnode, Commands.literal("on")))
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapStores(
        LiteralCommandNode<CommandSourceStack> parent, LiteralArgumentBuilder<CommandSourceStack> literal, boolean storingResult
    ) {
        literal.then(
            Commands.literal("score")
                .then(
                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                        .then(
                            Commands.argument("objective", ObjectiveArgument.objective())
                                .redirect(
                                    parent,
                                    p_137271_ -> storeValue(
                                            p_137271_.getSource(),
                                            ScoreHolderArgument.getNamesWithDefaultWildcard(p_137271_, "targets"),
                                            ObjectiveArgument.getObjective(p_137271_, "objective"),
                                            storingResult
                                        )
                                )
                        )
                )
        );
        literal.then(
            Commands.literal("bossbar")
                .then(
                    Commands.argument("id", ResourceLocationArgument.id())
                        .suggests(BossBarCommands.SUGGEST_BOSS_BAR)
                        .then(
                            Commands.literal("value")
                                .redirect(parent, p_137259_ -> storeValue(p_137259_.getSource(), BossBarCommands.getBossBar(p_137259_), true, storingResult))
                        )
                        .then(
                            Commands.literal("max")
                                .redirect(parent, p_137247_ -> storeValue(p_137247_.getSource(), BossBarCommands.getBossBar(p_137247_), false, storingResult))
                        )
                )
        );

        for (DataCommands.DataProvider datacommands$dataprovider : DataCommands.TARGET_PROVIDERS) {
            datacommands$dataprovider.wrap(
                literal,
                p_137101_ -> p_137101_.then(
                        Commands.argument("path", NbtPathArgument.nbtPath())
                            .then(
                                Commands.literal("int")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                parent,
                                                p_180216_ -> storeData(
                                                        p_180216_.getSource(),
                                                        datacommands$dataprovider.access(p_180216_),
                                                        NbtPathArgument.getPath(p_180216_, "path"),
                                                        p_180219_ -> IntTag.valueOf((int)((double)p_180219_ * DoubleArgumentType.getDouble(p_180216_, "scale"))),
                                                        storingResult
                                                    )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("float")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                parent,
                                                p_180209_ -> storeData(
                                                        p_180209_.getSource(),
                                                        datacommands$dataprovider.access(p_180209_),
                                                        NbtPathArgument.getPath(p_180209_, "path"),
                                                        p_180212_ -> FloatTag.valueOf(
                                                                (float)((double)p_180212_ * DoubleArgumentType.getDouble(p_180209_, "scale"))
                                                            ),
                                                        storingResult
                                                    )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("short")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                parent,
                                                p_180199_ -> storeData(
                                                        p_180199_.getSource(),
                                                        datacommands$dataprovider.access(p_180199_),
                                                        NbtPathArgument.getPath(p_180199_, "path"),
                                                        p_180202_ -> ShortTag.valueOf(
                                                                (short)((int)((double)p_180202_ * DoubleArgumentType.getDouble(p_180199_, "scale")))
                                                            ),
                                                        storingResult
                                                    )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("long")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                parent,
                                                p_180189_ -> storeData(
                                                        p_180189_.getSource(),
                                                        datacommands$dataprovider.access(p_180189_),
                                                        NbtPathArgument.getPath(p_180189_, "path"),
                                                        p_180192_ -> LongTag.valueOf(
                                                                (long)((double)p_180192_ * DoubleArgumentType.getDouble(p_180189_, "scale"))
                                                            ),
                                                        storingResult
                                                    )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("double")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                parent,
                                                p_180179_ -> storeData(
                                                        p_180179_.getSource(),
                                                        datacommands$dataprovider.access(p_180179_),
                                                        NbtPathArgument.getPath(p_180179_, "path"),
                                                        p_180182_ -> DoubleTag.valueOf((double)p_180182_ * DoubleArgumentType.getDouble(p_180179_, "scale")),
                                                        storingResult
                                                    )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("byte")
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .redirect(
                                                parent,
                                                p_180156_ -> storeData(
                                                        p_180156_.getSource(),
                                                        datacommands$dataprovider.access(p_180156_),
                                                        NbtPathArgument.getPath(p_180156_, "path"),
                                                        p_180165_ -> ByteTag.valueOf(
                                                                (byte)((int)((double)p_180165_ * DoubleArgumentType.getDouble(p_180156_, "scale")))
                                                            ),
                                                        storingResult
                                                    )
                                            )
                                    )
                            )
                    )
            );
        }

        return literal;
    }

    private static CommandSourceStack storeValue(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, boolean storingResult) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        return source.withCallback((p_137137_, p_137138_) -> {
            for (ScoreHolder scoreholder : targets) {
                ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, objective);
                int i = storingResult ? p_137138_ : (p_137137_ ? 1 : 0);
                scoreaccess.set(i);
            }
        }, CommandResultCallback::chain);
    }

    private static CommandSourceStack storeValue(CommandSourceStack source, CustomBossEvent bar, boolean storingValue, boolean storingResult) {
        return source.withCallback((p_137186_, p_137187_) -> {
            int i = storingResult ? p_137187_ : (p_137186_ ? 1 : 0);
            if (storingValue) {
                bar.setValue(i);
            } else {
                bar.setMax(i);
            }
        }, CommandResultCallback::chain);
    }

    private static CommandSourceStack storeData(
        CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path, IntFunction<Tag> tagConverter, boolean storingResult
    ) {
        return source.withCallback((p_137154_, p_137155_) -> {
            try {
                CompoundTag compoundtag = accessor.getData();
                int i = storingResult ? p_137155_ : (p_137154_ ? 1 : 0);
                path.set(compoundtag, tagConverter.apply(i));
                accessor.setData(compoundtag);
            } catch (CommandSyntaxException commandsyntaxexception) {
            }
        }, CommandResultCallback::chain);
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos chunkpos = new ChunkPos(pos);
        LevelChunk levelchunk = level.getChunkSource().getChunkNow(chunkpos.x, chunkpos.z);
        return levelchunk == null ? false : levelchunk.getFullStatus() == FullChunkStatus.ENTITY_TICKING && level.areEntitiesLoaded(chunkpos.toLong());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addConditionals(
        CommandNode<CommandSourceStack> parent, LiteralArgumentBuilder<CommandSourceStack> literal, boolean isIf, CommandBuildContext context
    ) {
        literal.then(
                Commands.literal("block")
                    .then(
                        Commands.argument("pos", BlockPosArgument.blockPos())
                            .then(
                                addConditional(
                                    parent,
                                    Commands.argument("block", BlockPredicateArgument.blockPredicate(context)),
                                    isIf,
                                    p_137277_ -> BlockPredicateArgument.getBlockPredicate(p_137277_, "block")
                                            .test(
                                                new BlockInWorld(p_137277_.getSource().getLevel(), BlockPosArgument.getLoadedBlockPos(p_137277_, "pos"), true)
                                            )
                                )
                            )
                    )
            )
            .then(
                Commands.literal("biome")
                    .then(
                        Commands.argument("pos", BlockPosArgument.blockPos())
                            .then(
                                addConditional(
                                    parent,
                                    Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(context, Registries.BIOME)),
                                    isIf,
                                    p_313477_ -> ResourceOrTagArgument.getResourceOrTag(p_313477_, "biome", Registries.BIOME)
                                            .test(p_313477_.getSource().getLevel().getBiome(BlockPosArgument.getLoadedBlockPos(p_313477_, "pos")))
                                )
                            )
                    )
            )
            .then(
                Commands.literal("loaded")
                    .then(
                        addConditional(
                            parent,
                            Commands.argument("pos", BlockPosArgument.blockPos()),
                            isIf,
                            p_269757_ -> isChunkLoaded(p_269757_.getSource().getLevel(), BlockPosArgument.getBlockPos(p_269757_, "pos"))
                        )
                    )
            )
            .then(
                Commands.literal("dimension")
                    .then(
                        addConditional(
                            parent,
                            Commands.argument("dimension", DimensionArgument.dimension()),
                            isIf,
                            p_264789_ -> DimensionArgument.getDimension(p_264789_, "dimension") == p_264789_.getSource().getLevel()
                        )
                    )
            )
            .then(
                Commands.literal("score")
                    .then(
                        Commands.argument("target", ScoreHolderArgument.scoreHolder())
                            .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                            .then(
                                Commands.argument("targetObjective", ObjectiveArgument.objective())
                                    .then(
                                        Commands.literal("=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            parent,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            isIf,
                                                            p_313481_ -> checkScore(p_313481_, (p_313485_, p_313486_) -> p_313485_ == p_313486_)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("<")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            parent,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            isIf,
                                                            p_313476_ -> checkScore(p_313476_, (p_313478_, p_313479_) -> p_313478_ < p_313479_)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("<=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            parent,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            isIf,
                                                            p_313482_ -> checkScore(p_313482_, (p_313473_, p_313474_) -> p_313473_ <= p_313474_)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal(">")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            parent,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            isIf,
                                                            p_313475_ -> checkScore(p_313475_, (p_313487_, p_313488_) -> p_313487_ > p_313488_)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal(">=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            parent,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            isIf,
                                                            p_313480_ -> checkScore(p_313480_, (p_313483_, p_313484_) -> p_313483_ >= p_313484_)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("matches")
                                            .then(
                                                addConditional(
                                                    parent,
                                                    Commands.argument("range", RangeArgument.intRange()),
                                                    isIf,
                                                    p_137216_ -> checkScore(p_137216_, RangeArgument.Ints.getRange(p_137216_, "range"))
                                                )
                                            )
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("blocks")
                    .then(
                        Commands.argument("start", BlockPosArgument.blockPos())
                            .then(
                                Commands.argument("end", BlockPosArgument.blockPos())
                                    .then(
                                        Commands.argument("destination", BlockPosArgument.blockPos())
                                            .then(addIfBlocksConditional(parent, Commands.literal("all"), isIf, false))
                                            .then(addIfBlocksConditional(parent, Commands.literal("masked"), isIf, true))
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("entity")
                    .then(
                        Commands.argument("entities", EntityArgument.entities())
                            .fork(parent, p_137232_ -> expect(p_137232_, isIf, !EntityArgument.getOptionalEntities(p_137232_, "entities").isEmpty()))
                            .executes(createNumericConditionalHandler(isIf, p_137189_ -> EntityArgument.getOptionalEntities(p_137189_, "entities").size()))
                    )
            )
            .then(
                Commands.literal("predicate")
                    .then(
                        addConditional(
                            parent,
                            Commands.argument("predicate", ResourceOrIdArgument.lootPredicate(context)).suggests(SUGGEST_PREDICATE),
                            isIf,
                            p_335217_ -> checkCustomPredicate(p_335217_.getSource(), ResourceOrIdArgument.getLootPredicate(p_335217_, "predicate"))
                        )
                    )
            )
            .then(
                Commands.literal("function")
                    .then(
                        Commands.argument("name", FunctionArgument.functions())
                            .suggests(FunctionCommand.SUGGEST_FUNCTION)
                            .fork(parent, new ExecuteCommand.ExecuteIfFunctionCustomModifier(isIf))
                    )
            )
            .then(
                Commands.literal("items")
                    .then(
                        Commands.literal("entity")
                            .then(
                                Commands.argument("entities", EntityArgument.entities())
                                    .then(
                                        Commands.argument("slots", SlotsArgument.slots())
                                            .then(
                                                Commands.argument("item_predicate", ItemPredicateArgument.itemPredicate(context))
                                                    .fork(
                                                        parent,
                                                        p_332574_ -> expect(
                                                                p_332574_,
                                                                isIf,
                                                                countItems(
                                                                        EntityArgument.getEntities(p_332574_, "entities"),
                                                                        SlotsArgument.getSlots(p_332574_, "slots"),
                                                                        ItemPredicateArgument.getItemPredicate(p_332574_, "item_predicate")
                                                                    )
                                                                    > 0
                                                            )
                                                    )
                                                    .executes(
                                                        createNumericConditionalHandler(
                                                            isIf,
                                                            p_332569_ -> countItems(
                                                                    EntityArgument.getEntities(p_332569_, "entities"),
                                                                    SlotsArgument.getSlots(p_332569_, "slots"),
                                                                    ItemPredicateArgument.getItemPredicate(p_332569_, "item_predicate")
                                                                )
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("block")
                            .then(
                                Commands.argument("pos", BlockPosArgument.blockPos())
                                    .then(
                                        Commands.argument("slots", SlotsArgument.slots())
                                            .then(
                                                Commands.argument("item_predicate", ItemPredicateArgument.itemPredicate(context))
                                                    .fork(
                                                        parent,
                                                        p_332571_ -> expect(
                                                                p_332571_,
                                                                isIf,
                                                                countItems(
                                                                        p_332571_.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(p_332571_, "pos"),
                                                                        SlotsArgument.getSlots(p_332571_, "slots"),
                                                                        ItemPredicateArgument.getItemPredicate(p_332571_, "item_predicate")
                                                                    )
                                                                    > 0
                                                            )
                                                    )
                                                    .executes(
                                                        createNumericConditionalHandler(
                                                            isIf,
                                                            p_332572_ -> countItems(
                                                                    p_332572_.getSource(),
                                                                    BlockPosArgument.getLoadedBlockPos(p_332572_, "pos"),
                                                                    SlotsArgument.getSlots(p_332572_, "slots"),
                                                                    ItemPredicateArgument.getItemPredicate(p_332572_, "item_predicate")
                                                                )
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
            );

        for (DataCommands.DataProvider datacommands$dataprovider : DataCommands.SOURCE_PROVIDERS) {
            literal.then(
                datacommands$dataprovider.wrap(
                    Commands.literal("data"),
                    p_137092_ -> p_137092_.then(
                            Commands.argument("path", NbtPathArgument.nbtPath())
                                .fork(
                                    parent,
                                    p_180175_ -> expect(
                                            p_180175_,
                                            isIf,
                                            checkMatchingData(datacommands$dataprovider.access(p_180175_), NbtPathArgument.getPath(p_180175_, "path")) > 0
                                        )
                                )
                                .executes(
                                    createNumericConditionalHandler(
                                        isIf,
                                        p_180152_ -> checkMatchingData(datacommands$dataprovider.access(p_180152_), NbtPathArgument.getPath(p_180152_, "path"))
                                    )
                                )
                        )
                )
            );
        }

        return literal;
    }

    private static int countItems(Iterable<? extends Entity> targets, SlotRange slotRange, Predicate<ItemStack> filter) {
        int i = 0;

        for (Entity entity : targets) {
            IntList intlist = slotRange.slots();

            for (int j = 0; j < intlist.size(); j++) {
                int k = intlist.getInt(j);
                SlotAccess slotaccess = entity.getSlot(k);
                ItemStack itemstack = slotaccess.get();
                if (filter.test(itemstack)) {
                    i += itemstack.getCount();
                }
            }
        }

        return i;
    }

    private static int countItems(CommandSourceStack source, BlockPos pos, SlotRange slotRange, Predicate<ItemStack> filter) throws CommandSyntaxException {
        int i = 0;
        Container container = ItemCommands.getContainer(source, pos, ItemCommands.ERROR_SOURCE_NOT_A_CONTAINER);
        int j = container.getContainerSize();
        IntList intlist = slotRange.slots();

        for (int k = 0; k < intlist.size(); k++) {
            int l = intlist.getInt(k);
            if (l >= 0 && l < j) {
                ItemStack itemstack = container.getItem(l);
                if (filter.test(itemstack)) {
                    i += itemstack.getCount();
                }
            }
        }

        return i;
    }

    private static Command<CommandSourceStack> createNumericConditionalHandler(boolean isIf, ExecuteCommand.CommandNumericPredicate predicate) {
        return isIf ? p_288391_ -> {
            int i = predicate.test(p_288391_);
            if (i > 0) {
                p_288391_.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass_count", i), false);
                return i;
            } else {
                throw ERROR_CONDITIONAL_FAILED.create();
            }
        } : p_288393_ -> {
            int i = predicate.test(p_288393_);
            if (i == 0) {
                p_288393_.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
                return 1;
            } else {
                throw ERROR_CONDITIONAL_FAILED_COUNT.create(i);
            }
        };
    }

    private static int checkMatchingData(DataAccessor accessor, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        return path.countMatching(accessor.getData());
    }

    private static boolean checkScore(CommandContext<CommandSourceStack> source, ExecuteCommand.IntBiPredicate predicate) throws CommandSyntaxException {
        ScoreHolder scoreholder = ScoreHolderArgument.getName(source, "target");
        Objective objective = ObjectiveArgument.getObjective(source, "targetObjective");
        ScoreHolder scoreholder1 = ScoreHolderArgument.getName(source, "source");
        Objective objective1 = ObjectiveArgument.getObjective(source, "sourceObjective");
        Scoreboard scoreboard = source.getSource().getServer().getScoreboard();
        ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreholder, objective);
        ReadOnlyScoreInfo readonlyscoreinfo1 = scoreboard.getPlayerScoreInfo(scoreholder1, objective1);
        return readonlyscoreinfo != null && readonlyscoreinfo1 != null ? predicate.test(readonlyscoreinfo.value(), readonlyscoreinfo1.value()) : false;
    }

    private static boolean checkScore(CommandContext<CommandSourceStack> context, MinMaxBounds.Ints bounds) throws CommandSyntaxException {
        ScoreHolder scoreholder = ScoreHolderArgument.getName(context, "target");
        Objective objective = ObjectiveArgument.getObjective(context, "targetObjective");
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreholder, objective);
        return readonlyscoreinfo == null ? false : bounds.matches(readonlyscoreinfo.value());
    }

    private static boolean checkCustomPredicate(CommandSourceStack source, Holder<LootItemCondition> condition) {
        ServerLevel serverlevel = source.getLevel();
        LootParams lootparams = new LootParams.Builder(serverlevel)
            .withParameter(LootContextParams.ORIGIN, source.getPosition())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, source.getEntity())
            .create(LootContextParamSets.COMMAND);
        LootContext lootcontext = new LootContext.Builder(lootparams).create(Optional.empty());
        lootcontext.pushVisitedElement(LootContext.createVisitedEntry(condition.value()));
        return condition.value().test(lootcontext);
    }

    /**
     * If actual and expected match, returns a collection containing only the source player.
     */
    private static Collection<CommandSourceStack> expect(CommandContext<CommandSourceStack> context, boolean actual, boolean expected) {
        return (Collection<CommandSourceStack>)(expected == actual ? Collections.singleton(context.getSource()) : Collections.emptyList());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addConditional(
        CommandNode<CommandSourceStack> commandNode,
        ArgumentBuilder<CommandSourceStack, ?> builder,
        boolean value,
        ExecuteCommand.CommandPredicate test
    ) {
        return builder.fork(commandNode, p_137214_ -> expect(p_137214_, value, test.test(p_137214_))).executes(p_288396_ -> {
            if (value == test.test(p_288396_)) {
                p_288396_.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
                return 1;
            } else {
                throw ERROR_CONDITIONAL_FAILED.create();
            }
        });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addIfBlocksConditional(
        CommandNode<CommandSourceStack> commandNode, ArgumentBuilder<CommandSourceStack, ?> literal, boolean isIf, boolean isMasked
    ) {
        return literal.fork(commandNode, p_137180_ -> expect(p_137180_, isIf, checkRegions(p_137180_, isMasked).isPresent()))
            .executes(isIf ? p_137210_ -> checkIfRegions(p_137210_, isMasked) : p_137165_ -> checkUnlessRegions(p_137165_, isMasked));
    }

    private static int checkIfRegions(CommandContext<CommandSourceStack> context, boolean isMasked) throws CommandSyntaxException {
        OptionalInt optionalint = checkRegions(context, isMasked);
        if (optionalint.isPresent()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass_count", optionalint.getAsInt()), false);
            return optionalint.getAsInt();
        } else {
            throw ERROR_CONDITIONAL_FAILED.create();
        }
    }

    private static int checkUnlessRegions(CommandContext<CommandSourceStack> context, boolean isMasked) throws CommandSyntaxException {
        OptionalInt optionalint = checkRegions(context, isMasked);
        if (optionalint.isPresent()) {
            throw ERROR_CONDITIONAL_FAILED_COUNT.create(optionalint.getAsInt());
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
            return 1;
        }
    }

    private static OptionalInt checkRegions(CommandContext<CommandSourceStack> context, boolean isMasked) throws CommandSyntaxException {
        return checkRegions(
            context.getSource().getLevel(),
            BlockPosArgument.getLoadedBlockPos(context, "start"),
            BlockPosArgument.getLoadedBlockPos(context, "end"),
            BlockPosArgument.getLoadedBlockPos(context, "destination"),
            isMasked
        );
    }

    private static OptionalInt checkRegions(ServerLevel level, BlockPos begin, BlockPos end, BlockPos destination, boolean isMasked) throws CommandSyntaxException {
        BoundingBox boundingbox = BoundingBox.fromCorners(begin, end);
        BoundingBox boundingbox1 = BoundingBox.fromCorners(destination, destination.offset(boundingbox.getLength()));
        BlockPos blockpos = new BlockPos(
            boundingbox1.minX() - boundingbox.minX(), boundingbox1.minY() - boundingbox.minY(), boundingbox1.minZ() - boundingbox.minZ()
        );
        int i = boundingbox.getXSpan() * boundingbox.getYSpan() * boundingbox.getZSpan();
        if (i > 32768) {
            throw ERROR_AREA_TOO_LARGE.create(32768, i);
        } else {
            RegistryAccess registryaccess = level.registryAccess();
            int j = 0;

            for (int k = boundingbox.minZ(); k <= boundingbox.maxZ(); k++) {
                for (int l = boundingbox.minY(); l <= boundingbox.maxY(); l++) {
                    for (int i1 = boundingbox.minX(); i1 <= boundingbox.maxX(); i1++) {
                        BlockPos blockpos1 = new BlockPos(i1, l, k);
                        BlockPos blockpos2 = blockpos1.offset(blockpos);
                        BlockState blockstate = level.getBlockState(blockpos1);
                        if (!isMasked || !blockstate.is(Blocks.AIR)) {
                            if (blockstate != level.getBlockState(blockpos2)) {
                                return OptionalInt.empty();
                            }

                            BlockEntity blockentity = level.getBlockEntity(blockpos1);
                            BlockEntity blockentity1 = level.getBlockEntity(blockpos2);
                            if (blockentity != null) {
                                if (blockentity1 == null) {
                                    return OptionalInt.empty();
                                }

                                if (blockentity1.getType() != blockentity.getType()) {
                                    return OptionalInt.empty();
                                }

                                if (!blockentity.components().equals(blockentity1.components())) {
                                    return OptionalInt.empty();
                                }

                                CompoundTag compoundtag = blockentity.saveCustomOnly(registryaccess);
                                CompoundTag compoundtag1 = blockentity1.saveCustomOnly(registryaccess);
                                if (!compoundtag.equals(compoundtag1)) {
                                    return OptionalInt.empty();
                                }
                            }

                            j++;
                        }
                    }
                }
            }

            return OptionalInt.of(j);
        }
    }

    private static RedirectModifier<CommandSourceStack> expandOneToOneEntityRelation(Function<Entity, Optional<Entity>> relation) {
        return p_264786_ -> {
            CommandSourceStack commandsourcestack = p_264786_.getSource();
            Entity entity = commandsourcestack.getEntity();
            return entity == null
                ? List.of()
                : relation.apply(entity)
                    .filter(p_264783_ -> !p_264783_.isRemoved())
                    .map(p_264775_ -> List.of(commandsourcestack.withEntity(p_264775_)))
                    .orElse(List.of());
        };
    }

    private static RedirectModifier<CommandSourceStack> expandOneToManyEntityRelation(Function<Entity, Stream<Entity>> relation) {
        return p_264780_ -> {
            CommandSourceStack commandsourcestack = p_264780_.getSource();
            Entity entity = commandsourcestack.getEntity();
            return entity == null
                ? List.of()
                : relation.apply(entity).filter(p_264784_ -> !p_264784_.isRemoved()).map(commandsourcestack::withEntity).toList();
        };
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRelationOperations(
        CommandNode<CommandSourceStack> node, LiteralArgumentBuilder<CommandSourceStack> argumentBuilder
    ) {
        return argumentBuilder.then(
                Commands.literal("owner")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            p_269758_ -> p_269758_ instanceof OwnableEntity ownableentity ? Optional.ofNullable(ownableentity.getOwner()) : Optional.empty()
                        )
                    )
            )
            .then(
                Commands.literal("leasher")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            p_353021_ -> p_353021_ instanceof Leashable leashable ? Optional.ofNullable(leashable.getLeashHolder()) : Optional.empty()
                        )
                    )
            )
            .then(
                Commands.literal("target")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            p_272389_ -> p_272389_ instanceof Targeting targeting ? Optional.ofNullable(targeting.getTarget()) : Optional.empty()
                        )
                    )
            )
            .then(
                Commands.literal("attacker")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            p_272388_ -> p_272388_ instanceof Attackable attackable ? Optional.ofNullable(attackable.getLastAttacker()) : Optional.empty()
                        )
                    )
            )
            .then(Commands.literal("vehicle").fork(node, expandOneToOneEntityRelation(p_264776_ -> Optional.ofNullable(p_264776_.getVehicle()))))
            .then(
                Commands.literal("controller")
                    .fork(node, expandOneToOneEntityRelation(p_274815_ -> Optional.ofNullable(p_274815_.getControllingPassenger())))
            )
            .then(
                Commands.literal("origin")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            p_266631_ -> p_266631_ instanceof TraceableEntity traceableentity
                                    ? Optional.ofNullable(traceableentity.getOwner())
                                    : Optional.empty()
                        )
                    )
            )
            .then(Commands.literal("passengers").fork(node, expandOneToManyEntityRelation(p_264777_ -> p_264777_.getPassengers().stream())));
    }

    private static CommandSourceStack spawnEntityAndRedirect(CommandSourceStack source, Holder.Reference<EntityType<?>> entityType) throws CommandSyntaxException {
        Entity entity = SummonCommand.createEntity(source, entityType, source.getPosition(), new CompoundTag(), true);
        return source.withEntity(entity);
    }

    public static <T extends ExecutionCommandSource<T>> void scheduleFunctionConditionsAndTest(
        T originalSource,
        List<T> sources,
        Function<T, T> sourceModifier,
        IntPredicate successCheck,
        ContextChain<T> contextChain,
        @Nullable CompoundTag arguments,
        ExecutionControl<T> executionControl,
        ExecuteCommand.CommandGetter<T, Collection<CommandFunction<T>>> functions,
        ChainModifiers chainModifiers
    ) {
        List<T> list = new ArrayList<>(sources.size());

        Collection<CommandFunction<T>> collection;
        try {
            collection = functions.get(contextChain.getTopContext().copyFor(originalSource));
        } catch (CommandSyntaxException commandsyntaxexception) {
            originalSource.handleError(commandsyntaxexception, chainModifiers.isForked(), executionControl.tracer());
            return;
        }

        int i = collection.size();
        if (i != 0) {
            List<InstantiatedFunction<T>> list1 = new ArrayList<>(i);

            try {
                for (CommandFunction<T> commandfunction : collection) {
                    try {
                        list1.add(commandfunction.instantiate(arguments, originalSource.dispatcher()));
                    } catch (FunctionInstantiationException functioninstantiationexception) {
                        throw ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE.create(commandfunction.id(), functioninstantiationexception.messageComponent());
                    }
                }
            } catch (CommandSyntaxException commandsyntaxexception1) {
                originalSource.handleError(commandsyntaxexception1, chainModifiers.isForked(), executionControl.tracer());
            }

            for (T t1 : sources) {
                T t = (T)sourceModifier.apply(t1.clearCallbacks());
                CommandResultCallback commandresultcallback = (p_309685_, p_305691_) -> {
                    if (successCheck.test(p_305691_)) {
                        list.add(t1);
                    }
                };
                executionControl.queueNext(new IsolatedCall<>(p_309456_ -> {
                    for (InstantiatedFunction<T> instantiatedfunction : list1) {
                        p_309456_.queueNext(new CallFunction<>(instantiatedfunction, p_309456_.currentFrame().returnValueConsumer(), true).bind(t));
                    }

                    p_309456_.queueNext(FallthroughTask.instance());
                }, commandresultcallback));
            }

            ContextChain<T> contextchain = contextChain.nextStage();
            String s = contextChain.getTopContext().getInput();
            executionControl.queueNext(new BuildContexts.Continuation<>(s, contextchain, chainModifiers, originalSource, list));
        }
    }

    @FunctionalInterface
    public interface CommandGetter<T, R> {
        R get(CommandContext<T> context) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface CommandNumericPredicate {
        int test(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface CommandPredicate {
        boolean test(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    static class ExecuteIfFunctionCustomModifier implements CustomModifierExecutor.ModifierAdapter<CommandSourceStack> {
        private final IntPredicate check;

        ExecuteIfFunctionCustomModifier(boolean invert) {
            this.check = invert ? p_305777_ -> p_305777_ != 0 : p_306070_ -> p_306070_ == 0;
        }

        public void apply(
            CommandSourceStack originalSource,
            List<CommandSourceStack> soruces,
            ContextChain<CommandSourceStack> contextChain,
            ChainModifiers chainModifiers,
            ExecutionControl<CommandSourceStack> executionControl
        ) {
            ExecuteCommand.scheduleFunctionConditionsAndTest(
                originalSource,
                soruces,
                FunctionCommand::modifySenderForExecution,
                this.check,
                contextChain,
                null,
                executionControl,
                p_305997_ -> FunctionArgument.getFunctions(p_305997_, "name"),
                chainModifiers
            );
        }
    }

    @FunctionalInterface
    interface IntBiPredicate {
        boolean test(int value1, int value2);
    }
}
