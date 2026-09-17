package net.minecraft.server.commands;

import com.google.common.base.Stopwatch;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

public class LocateCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_NOT_FOUND = new DynamicCommandExceptionType(
        p_304261_ -> Component.translatableEscape("commands.locate.structure.not_found", p_304261_)
    );
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_INVALID = new DynamicCommandExceptionType(
        p_304258_ -> Component.translatableEscape("commands.locate.structure.invalid", p_304258_)
    );
    private static final DynamicCommandExceptionType ERROR_BIOME_NOT_FOUND = new DynamicCommandExceptionType(
        p_304259_ -> Component.translatableEscape("commands.locate.biome.not_found", p_304259_)
    );
    private static final DynamicCommandExceptionType ERROR_POI_NOT_FOUND = new DynamicCommandExceptionType(
        p_304260_ -> Component.translatableEscape("commands.locate.poi.not_found", p_304260_)
    );
    private static final int MAX_STRUCTURE_SEARCH_RADIUS = 100;
    private static final int MAX_BIOME_SEARCH_RADIUS = 6400;
    private static final int BIOME_SAMPLE_RESOLUTION_HORIZONTAL = 32;
    private static final int BIOME_SAMPLE_RESOLUTION_VERTICAL = 64;
    private static final int POI_SEARCH_RADIUS = 256;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("locate")
                .requires(p_214470_ -> p_214470_.hasPermission(2))
                .then(
                    Commands.literal("structure")
                        .then(
                            Commands.argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.STRUCTURE))
                                .executes(
                                    p_258233_ -> locateStructure(
                                            p_258233_.getSource(),
                                            ResourceOrTagKeyArgument.getResourceOrTagKey(p_258233_, "structure", Registries.STRUCTURE, ERROR_STRUCTURE_INVALID)
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("biome")
                        .then(
                            Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(context, Registries.BIOME))
                                .executes(
                                    p_258232_ -> locateBiome(
                                            p_258232_.getSource(), ResourceOrTagArgument.getResourceOrTag(p_258232_, "biome", Registries.BIOME)
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("poi")
                        .then(
                            Commands.argument("poi", ResourceOrTagArgument.resourceOrTag(context, Registries.POINT_OF_INTEREST_TYPE))
                                .executes(
                                    p_258234_ -> locatePoi(
                                            p_258234_.getSource(), ResourceOrTagArgument.getResourceOrTag(p_258234_, "poi", Registries.POINT_OF_INTEREST_TYPE)
                                        )
                                )
                        )
                )
        );
    }

    private static Optional<? extends HolderSet.ListBacked<Structure>> getHolders(
        ResourceOrTagKeyArgument.Result<Structure> structure, Registry<Structure> structureRegistry
    ) {
        return structure.unwrap()
            .map(p_258231_ -> structureRegistry.getHolder((ResourceKey<Structure>)p_258231_).map(p_214491_ -> HolderSet.direct(p_214491_)), structureRegistry::getTag);
    }

    private static int locateStructure(CommandSourceStack source, ResourceOrTagKeyArgument.Result<Structure> structure) throws CommandSyntaxException {
        Registry<Structure> registry = source.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        HolderSet<Structure> holderset = (HolderSet<Structure>)getHolders(structure, registry)
            .orElseThrow(() -> ERROR_STRUCTURE_INVALID.create(structure.asPrintable()));
        BlockPos blockpos = BlockPos.containing(source.getPosition());
        ServerLevel serverlevel = source.getLevel();
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Pair<BlockPos, Holder<Structure>> pair = serverlevel.getChunkSource()
            .getGenerator()
            .findNearestMapStructure(serverlevel, holderset, blockpos, 100, false);
        stopwatch.stop();
        if (pair == null) {
            throw ERROR_STRUCTURE_NOT_FOUND.create(structure.asPrintable());
        } else {
            return showLocateResult(source, structure, blockpos, pair, "commands.locate.structure.success", false, stopwatch.elapsed());
        }
    }

    private static int locateBiome(CommandSourceStack source, ResourceOrTagArgument.Result<Biome> biome) throws CommandSyntaxException {
        BlockPos blockpos = BlockPos.containing(source.getPosition());
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Pair<BlockPos, Holder<Biome>> pair = source.getLevel().findClosestBiome3d(biome, blockpos, 6400, 32, 64);
        stopwatch.stop();
        if (pair == null) {
            throw ERROR_BIOME_NOT_FOUND.create(biome.asPrintable());
        } else {
            return showLocateResult(source, biome, blockpos, pair, "commands.locate.biome.success", true, stopwatch.elapsed());
        }
    }

    private static int locatePoi(CommandSourceStack source, ResourceOrTagArgument.Result<PoiType> poiType) throws CommandSyntaxException {
        BlockPos blockpos = BlockPos.containing(source.getPosition());
        ServerLevel serverlevel = source.getLevel();
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Optional<Pair<Holder<PoiType>, BlockPos>> optional = serverlevel.getPoiManager()
            .findClosestWithType(poiType, blockpos, 256, PoiManager.Occupancy.ANY);
        stopwatch.stop();
        if (optional.isEmpty()) {
            throw ERROR_POI_NOT_FOUND.create(poiType.asPrintable());
        } else {
            return showLocateResult(source, poiType, blockpos, optional.get().swap(), "commands.locate.poi.success", false, stopwatch.elapsed());
        }
    }

    public static int showLocateResult(
        CommandSourceStack source,
        ResourceOrTagArgument.Result<?> result,
        BlockPos sourcePosition,
        Pair<BlockPos, ? extends Holder<?>> resultWithPosition,
        String translationKey,
        boolean absoluteY,
        Duration duration
    ) {
        String s = result.unwrap()
            .map(p_248147_ -> result.asPrintable(), p_315917_ -> result.asPrintable() + " (" + resultWithPosition.getSecond().getRegisteredName() + ")");
        return showLocateResult(source, sourcePosition, resultWithPosition, translationKey, absoluteY, s, duration);
    }

    public static int showLocateResult(
        CommandSourceStack source,
        ResourceOrTagKeyArgument.Result<?> result,
        BlockPos sourcePosition,
        Pair<BlockPos, ? extends Holder<?>> resultWithPosition,
        String translationKey,
        boolean absoluteY,
        Duration duration
    ) {
        String s = result.unwrap()
            .map(p_214498_ -> p_214498_.location().toString(), p_339434_ -> "#" + p_339434_.location() + " (" + resultWithPosition.getSecond().getRegisteredName() + ")");
        return showLocateResult(source, sourcePosition, resultWithPosition, translationKey, absoluteY, s, duration);
    }

    private static int showLocateResult(
        CommandSourceStack source,
        BlockPos sourcePosition,
        Pair<BlockPos, ? extends Holder<?>> resultWithoutPosition,
        String translationKey,
        boolean absoluteY,
        String elementName,
        Duration duration
    ) {
        BlockPos blockpos = resultWithoutPosition.getFirst();
        int i = absoluteY
            ? Mth.floor(Mth.sqrt((float)sourcePosition.distSqr(blockpos)))
            : Mth.floor(dist(sourcePosition.getX(), sourcePosition.getZ(), blockpos.getX(), blockpos.getZ()));
        String s = absoluteY ? String.valueOf(blockpos.getY()) : "~";
        Component component = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", blockpos.getX(), s, blockpos.getZ()))
            .withStyle(
                p_214489_ -> p_214489_.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + blockpos.getX() + " " + s + " " + blockpos.getZ()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip")))
            );
        source.sendSuccess(() -> Component.translatable(translationKey, elementName, component, i), false);
        LOGGER.info("Locating element " + elementName + " took " + duration.toMillis() + " ms");
        return i;
    }

    private static float dist(int x1, int z1, int x2, int z2) {
        int i = x2 - x1;
        int j = z2 - z1;
        return Mth.sqrt((float)(i * i + j * j));
    }
}
