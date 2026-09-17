package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class LootCommand {
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_LOOT_TABLE = (p_335248_, p_335249_) -> {
        ReloadableServerRegistries.Holder reloadableserverregistries$holder = p_335248_.getSource().getServer().reloadableRegistries();
        return SharedSuggestionProvider.suggestResource(reloadableserverregistries$holder.getKeys(Registries.LOOT_TABLE), p_335249_);
    };
    private static final DynamicCommandExceptionType ERROR_NO_HELD_ITEMS = new DynamicCommandExceptionType(
        p_304268_ -> Component.translatableEscape("commands.drop.no_held_items", p_304268_)
    );
    private static final DynamicCommandExceptionType ERROR_NO_LOOT_TABLE = new DynamicCommandExceptionType(
        p_304262_ -> Component.translatableEscape("commands.drop.no_loot_table", p_304262_)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            addTargets(
                Commands.literal("loot").requires(p_137937_ -> p_137937_.hasPermission(2)),
                (p_214520_, p_214521_) -> p_214520_.then(
                            Commands.literal("fish")
                                .then(
                                    Commands.argument("loot_table", ResourceOrIdArgument.lootTable(context))
                                        .suggests(SUGGEST_LOOT_TABLE)
                                        .then(
                                            Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(
                                                    p_335247_ -> dropFishingLoot(
                                                            p_335247_,
                                                            ResourceOrIdArgument.getLootTable(p_335247_, "loot_table"),
                                                            BlockPosArgument.getLoadedBlockPos(p_335247_, "pos"),
                                                            ItemStack.EMPTY,
                                                            p_214521_
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("tool", ItemArgument.item(context))
                                                        .executes(
                                                            p_335234_ -> dropFishingLoot(
                                                                    p_335234_,
                                                                    ResourceOrIdArgument.getLootTable(p_335234_, "loot_table"),
                                                                    BlockPosArgument.getLoadedBlockPos(p_335234_, "pos"),
                                                                    ItemArgument.getItem(p_335234_, "tool").createItemStack(1, false),
                                                                    p_214521_
                                                                )
                                                        )
                                                )
                                                .then(
                                                    Commands.literal("mainhand")
                                                        .executes(
                                                            p_335238_ -> dropFishingLoot(
                                                                    p_335238_,
                                                                    ResourceOrIdArgument.getLootTable(p_335238_, "loot_table"),
                                                                    BlockPosArgument.getLoadedBlockPos(p_335238_, "pos"),
                                                                    getSourceHandItem(p_335238_.getSource(), EquipmentSlot.MAINHAND),
                                                                    p_214521_
                                                                )
                                                        )
                                                )
                                                .then(
                                                    Commands.literal("offhand")
                                                        .executes(
                                                            p_335240_ -> dropFishingLoot(
                                                                    p_335240_,
                                                                    ResourceOrIdArgument.getLootTable(p_335240_, "loot_table"),
                                                                    BlockPosArgument.getLoadedBlockPos(p_335240_, "pos"),
                                                                    getSourceHandItem(p_335240_.getSource(), EquipmentSlot.OFFHAND),
                                                                    p_214521_
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("loot")
                                .then(
                                    Commands.argument("loot_table", ResourceOrIdArgument.lootTable(context))
                                        .suggests(SUGGEST_LOOT_TABLE)
                                        .executes(p_335229_ -> dropChestLoot(p_335229_, ResourceOrIdArgument.getLootTable(p_335229_, "loot_table"), p_214521_))
                                )
                        )
                        .then(
                            Commands.literal("kill")
                                .then(
                                    Commands.argument("target", EntityArgument.entity())
                                        .executes(p_180406_ -> dropKillLoot(p_180406_, EntityArgument.getEntity(p_180406_, "target"), p_214521_))
                                )
                        )
                        .then(
                            Commands.literal("mine")
                                .then(
                                    Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(
                                            p_180403_ -> dropBlockLoot(
                                                    p_180403_, BlockPosArgument.getLoadedBlockPos(p_180403_, "pos"), ItemStack.EMPTY, p_214521_
                                                )
                                        )
                                        .then(
                                            Commands.argument("tool", ItemArgument.item(context))
                                                .executes(
                                                    p_180400_ -> dropBlockLoot(
                                                            p_180400_,
                                                            BlockPosArgument.getLoadedBlockPos(p_180400_, "pos"),
                                                            ItemArgument.getItem(p_180400_, "tool").createItemStack(1, false),
                                                            p_214521_
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("mainhand")
                                                .executes(
                                                    p_180397_ -> dropBlockLoot(
                                                            p_180397_,
                                                            BlockPosArgument.getLoadedBlockPos(p_180397_, "pos"),
                                                            getSourceHandItem(p_180397_.getSource(), EquipmentSlot.MAINHAND),
                                                            p_214521_
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("offhand")
                                                .executes(
                                                    p_180394_ -> dropBlockLoot(
                                                            p_180394_,
                                                            BlockPosArgument.getLoadedBlockPos(p_180394_, "pos"),
                                                            getSourceHandItem(p_180394_.getSource(), EquipmentSlot.OFFHAND),
                                                            p_214521_
                                                        )
                                                )
                                        )
                                )
                        )
            )
        );
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T addTargets(T builder, LootCommand.TailProvider tailProvider) {
        return builder.then(
                Commands.literal("replace")
                    .then(
                        Commands.literal("entity")
                            .then(
                                Commands.argument("entities", EntityArgument.entities())
                                    .then(
                                        tailProvider.construct(
                                                Commands.argument("slot", SlotArgument.slot()),
                                                (p_138032_, p_138033_, p_138034_) -> entityReplace(
                                                        EntityArgument.getEntities(p_138032_, "entities"),
                                                        SlotArgument.getSlot(p_138032_, "slot"),
                                                        p_138033_.size(),
                                                        p_138033_,
                                                        p_138034_
                                                    )
                                            )
                                            .then(
                                                tailProvider.construct(
                                                    Commands.argument("count", IntegerArgumentType.integer(0)),
                                                    (p_138025_, p_138026_, p_138027_) -> entityReplace(
                                                            EntityArgument.getEntities(p_138025_, "entities"),
                                                            SlotArgument.getSlot(p_138025_, "slot"),
                                                            IntegerArgumentType.getInteger(p_138025_, "count"),
                                                            p_138026_,
                                                            p_138027_
                                                        )
                                                )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("block")
                            .then(
                                Commands.argument("targetPos", BlockPosArgument.blockPos())
                                    .then(
                                        tailProvider.construct(
                                                Commands.argument("slot", SlotArgument.slot()),
                                                (p_138018_, p_138019_, p_138020_) -> blockReplace(
                                                        p_138018_.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(p_138018_, "targetPos"),
                                                        SlotArgument.getSlot(p_138018_, "slot"),
                                                        p_138019_.size(),
                                                        p_138019_,
                                                        p_138020_
                                                    )
                                            )
                                            .then(
                                                tailProvider.construct(
                                                    Commands.argument("count", IntegerArgumentType.integer(0)),
                                                    (p_138011_, p_138012_, p_138013_) -> blockReplace(
                                                            p_138011_.getSource(),
                                                            BlockPosArgument.getLoadedBlockPos(p_138011_, "targetPos"),
                                                            IntegerArgumentType.getInteger(p_138011_, "slot"),
                                                            IntegerArgumentType.getInteger(p_138011_, "count"),
                                                            p_138012_,
                                                            p_138013_
                                                        )
                                                )
                                            )
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("insert")
                    .then(
                        tailProvider.construct(
                            Commands.argument("targetPos", BlockPosArgument.blockPos()),
                            (p_138004_, p_138005_, p_138006_) -> blockDistribute(
                                    p_138004_.getSource(), BlockPosArgument.getLoadedBlockPos(p_138004_, "targetPos"), p_138005_, p_138006_
                                )
                        )
                    )
            )
            .then(
                Commands.literal("give")
                    .then(
                        tailProvider.construct(
                            Commands.argument("players", EntityArgument.players()),
                            (p_137992_, p_137993_, p_137994_) -> playerGive(EntityArgument.getPlayers(p_137992_, "players"), p_137993_, p_137994_)
                        )
                    )
            )
            .then(
                Commands.literal("spawn")
                    .then(
                        tailProvider.construct(
                            Commands.argument("targetPos", Vec3Argument.vec3()),
                            (p_137918_, p_137919_, p_137920_) -> dropInWorld(
                                    p_137918_.getSource(), Vec3Argument.getVec3(p_137918_, "targetPos"), p_137919_, p_137920_
                                )
                        )
                    )
            );
    }

    private static Container getContainer(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        BlockEntity blockentity = source.getLevel().getBlockEntity(pos);
        if (!(blockentity instanceof Container)) {
            throw ItemCommands.ERROR_TARGET_NOT_A_CONTAINER.create(pos.getX(), pos.getY(), pos.getZ());
        } else {
            return (Container)blockentity;
        }
    }

    private static int blockDistribute(CommandSourceStack source, BlockPos pos, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        Container container = getContainer(source, pos);
        List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

        for (ItemStack itemstack : items) {
            if (distributeToContainer(container, itemstack.copy())) {
                container.setChanged();
                list.add(itemstack);
            }
        }

        callback.accept(list);
        return list.size();
    }

    private static boolean distributeToContainer(Container container, ItemStack item) {
        boolean flag = false;

        for (int i = 0; i < container.getContainerSize() && !item.isEmpty(); i++) {
            ItemStack itemstack = container.getItem(i);
            if (container.canPlaceItem(i, item)) {
                if (itemstack.isEmpty()) {
                    container.setItem(i, item);
                    flag = true;
                    break;
                }

                if (canMergeItems(itemstack, item)) {
                    int j = item.getMaxStackSize() - itemstack.getCount();
                    int k = Math.min(item.getCount(), j);
                    item.shrink(k);
                    itemstack.grow(k);
                    flag = true;
                }
            }
        }

        return flag;
    }

    private static int blockReplace(
        CommandSourceStack source, BlockPos pos, int slot, int numSlots, List<ItemStack> items, LootCommand.Callback callback
    ) throws CommandSyntaxException {
        Container container = getContainer(source, pos);
        int i = container.getContainerSize();
        if (slot >= 0 && slot < i) {
            List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

            for (int j = 0; j < numSlots; j++) {
                int k = slot + j;
                ItemStack itemstack = j < items.size() ? items.get(j) : ItemStack.EMPTY;
                if (container.canPlaceItem(k, itemstack)) {
                    container.setItem(k, itemstack);
                    list.add(itemstack);
                }
            }

            callback.accept(list);
            return list.size();
        } else {
            throw ItemCommands.ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
        }
    }

    private static boolean canMergeItems(ItemStack first, ItemStack second) {
        return first.getCount() <= first.getMaxStackSize() && ItemStack.isSameItemSameComponents(first, second);
    }

    private static int playerGive(Collection<ServerPlayer> targets, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

        for (ItemStack itemstack : items) {
            for (ServerPlayer serverplayer : targets) {
                if (serverplayer.getInventory().add(itemstack.copy())) {
                    list.add(itemstack);
                }
            }
        }

        callback.accept(list);
        return list.size();
    }

    private static void setSlots(Entity target, List<ItemStack> items, int startSlot, int numSlots, List<ItemStack> setItems) {
        for (int i = 0; i < numSlots; i++) {
            ItemStack itemstack = i < items.size() ? items.get(i) : ItemStack.EMPTY;
            SlotAccess slotaccess = target.getSlot(startSlot + i);
            if (slotaccess != SlotAccess.NULL && slotaccess.set(itemstack.copy())) {
                setItems.add(itemstack);
            }
        }
    }

    private static int entityReplace(
        Collection<? extends Entity> targets, int startSlot, int numSlots, List<ItemStack> items, LootCommand.Callback callback
    ) throws CommandSyntaxException {
        List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

        for (Entity entity : targets) {
            if (entity instanceof ServerPlayer serverplayer) {
                setSlots(entity, items, startSlot, numSlots, list);
                serverplayer.containerMenu.broadcastChanges();
            } else {
                setSlots(entity, items, startSlot, numSlots, list);
            }
        }

        callback.accept(list);
        return list.size();
    }

    private static int dropInWorld(CommandSourceStack source, Vec3 pos, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();
        items.forEach(p_137884_ -> {
            ItemEntity itementity = new ItemEntity(serverlevel, pos.x, pos.y, pos.z, p_137884_.copy());
            itementity.setDefaultPickUpDelay();
            serverlevel.addFreshEntity(itementity);
        });
        callback.accept(items);
        return items.size();
    }

    private static void callback(CommandSourceStack source, List<ItemStack> items) {
        if (items.size() == 1) {
            ItemStack itemstack = items.get(0);
            source.sendSuccess(() -> Component.translatable("commands.drop.success.single", itemstack.getCount(), itemstack.getDisplayName()), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.drop.success.multiple", items.size()), false);
        }
    }

    private static void callback(CommandSourceStack source, List<ItemStack> items, ResourceKey<LootTable> lootTable) {
        if (items.size() == 1) {
            ItemStack itemstack = items.get(0);
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.drop.success.single_with_table",
                        itemstack.getCount(),
                        itemstack.getDisplayName(),
                        Component.translationArg(lootTable.location())
                    ),
                false
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.drop.success.multiple_with_table", items.size(), Component.translationArg(lootTable.location())),
                false
            );
        }
    }

    private static ItemStack getSourceHandItem(CommandSourceStack source, EquipmentSlot slot) throws CommandSyntaxException {
        Entity entity = source.getEntityOrException();
        if (entity instanceof LivingEntity) {
            return ((LivingEntity)entity).getItemBySlot(slot);
        } else {
            throw ERROR_NO_HELD_ITEMS.create(entity.getDisplayName());
        }
    }

    private static int dropBlockLoot(CommandContext<CommandSourceStack> context, BlockPos pos, ItemStack tool, LootCommand.DropConsumer dropConsumer) throws CommandSyntaxException {
        CommandSourceStack commandsourcestack = context.getSource();
        ServerLevel serverlevel = commandsourcestack.getLevel();
        BlockState blockstate = serverlevel.getBlockState(pos);
        BlockEntity blockentity = serverlevel.getBlockEntity(pos);
        LootParams.Builder lootparams$builder = new LootParams.Builder(serverlevel)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.BLOCK_STATE, blockstate)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockentity)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, commandsourcestack.getEntity())
            .withParameter(LootContextParams.TOOL, tool);
        List<ItemStack> list = blockstate.getDrops(lootparams$builder);
        return dropConsumer.accept(context, list, p_335243_ -> callback(commandsourcestack, p_335243_, blockstate.getBlock().getLootTable()));
    }

    private static int dropKillLoot(CommandContext<CommandSourceStack> context, Entity p_entity, LootCommand.DropConsumer dropConsumer) throws CommandSyntaxException {
        if (!(p_entity instanceof LivingEntity)) {
            throw ERROR_NO_LOOT_TABLE.create(p_entity.getDisplayName());
        } else {
            ResourceKey<LootTable> resourcekey = ((LivingEntity)p_entity).getLootTable();
            CommandSourceStack commandsourcestack = context.getSource();
            LootParams.Builder lootparams$builder = new LootParams.Builder(commandsourcestack.getLevel());
            Entity entity = commandsourcestack.getEntity();
            if (entity instanceof Player player) {
                lootparams$builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
            }

            lootparams$builder.withParameter(LootContextParams.DAMAGE_SOURCE, p_entity.damageSources().magic());
            lootparams$builder.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, entity);
            lootparams$builder.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, entity);
            lootparams$builder.withParameter(LootContextParams.THIS_ENTITY, p_entity);
            lootparams$builder.withParameter(LootContextParams.ORIGIN, commandsourcestack.getPosition());
            LootParams lootparams = lootparams$builder.create(LootContextParamSets.ENTITY);
            LootTable loottable = commandsourcestack.getServer().reloadableRegistries().getLootTable(resourcekey);
            List<ItemStack> list = loottable.getRandomItems(lootparams);
            return dropConsumer.accept(context, list, p_335232_ -> callback(commandsourcestack, p_335232_, resourcekey));
        }
    }

    private static int dropChestLoot(CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, LootCommand.DropConsumer dropCOnsimer) throws CommandSyntaxException {
        CommandSourceStack commandsourcestack = context.getSource();
        LootParams lootparams = new LootParams.Builder(commandsourcestack.getLevel())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, commandsourcestack.getEntity())
            .withParameter(LootContextParams.ORIGIN, commandsourcestack.getPosition())
            .create(LootContextParamSets.CHEST);
        return drop(context, lootTable, lootparams, dropCOnsimer);
    }

    private static int dropFishingLoot(
        CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, BlockPos pos, ItemStack tool, LootCommand.DropConsumer dropConsumet
    ) throws CommandSyntaxException {
        CommandSourceStack commandsourcestack = context.getSource();
        LootParams lootparams = new LootParams.Builder(commandsourcestack.getLevel())
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, tool)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, commandsourcestack.getEntity())
            .create(LootContextParamSets.FISHING);
        return drop(context, lootTable, lootparams, dropConsumet);
    }

    private static int drop(CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, LootParams params, LootCommand.DropConsumer dropConsumer) throws CommandSyntaxException {
        CommandSourceStack commandsourcestack = context.getSource();
        List<ItemStack> list = lootTable.value().getRandomItems(params);
        return dropConsumer.accept(context, list, p_137997_ -> callback(commandsourcestack, p_137997_));
    }

    @FunctionalInterface
    interface Callback {
        void accept(List<ItemStack> items) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface DropConsumer {
        int accept(CommandContext<CommandSourceStack> context, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface TailProvider {
        ArgumentBuilder<CommandSourceStack, ?> construct(ArgumentBuilder<CommandSourceStack, ?> builder, LootCommand.DropConsumer dropConsumer);
    }
}
