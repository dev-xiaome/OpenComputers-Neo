package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;

public class BossBarCommands {
    private static final DynamicCommandExceptionType ERROR_ALREADY_EXISTS = new DynamicCommandExceptionType(
        p_304191_ -> Component.translatableEscape("commands.bossbar.create.failed", p_304191_)
    );
    private static final DynamicCommandExceptionType ERROR_DOESNT_EXIST = new DynamicCommandExceptionType(
        p_304190_ -> Component.translatableEscape("commands.bossbar.unknown", p_304190_)
    );
    private static final SimpleCommandExceptionType ERROR_NO_PLAYER_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.players.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_NAME_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.name.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_COLOR_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.color.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_STYLE_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.style.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_VALUE_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.value.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_NO_MAX_CHANGE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.max.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_HIDDEN = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.visibility.unchanged.hidden")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_VISIBLE = new SimpleCommandExceptionType(
        Component.translatable("commands.bossbar.set.visibility.unchanged.visible")
    );
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_BOSS_BAR = (p_136587_, p_136588_) -> SharedSuggestionProvider.suggestResource(
            p_136587_.getSource().getServer().getCustomBossEvents().getIds(), p_136588_
        );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("bossbar")
                .requires(p_136627_ -> p_136627_.hasPermission(2))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("id", ResourceLocationArgument.id())
                                .then(
                                    Commands.argument("name", ComponentArgument.textComponent(context))
                                        .executes(
                                            p_136693_ -> createBar(
                                                    p_136693_.getSource(),
                                                    ResourceLocationArgument.getId(p_136693_, "id"),
                                                    ComponentArgument.getComponent(p_136693_, "name")
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_BOSS_BAR)
                                .executes(p_136691_ -> removeBar(p_136691_.getSource(), getBossBar(p_136691_)))
                        )
                )
                .then(Commands.literal("list").executes(p_136689_ -> listBars(p_136689_.getSource())))
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_BOSS_BAR)
                                .then(
                                    Commands.literal("name")
                                        .then(
                                            Commands.argument("name", ComponentArgument.textComponent(context))
                                                .executes(
                                                    p_136687_ -> setName(
                                                            p_136687_.getSource(), getBossBar(p_136687_), ComponentArgument.getComponent(p_136687_, "name")
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("color")
                                        .then(
                                            Commands.literal("pink")
                                                .executes(p_136685_ -> setColor(p_136685_.getSource(), getBossBar(p_136685_), BossEvent.BossBarColor.PINK))
                                        )
                                        .then(
                                            Commands.literal("blue")
                                                .executes(p_136683_ -> setColor(p_136683_.getSource(), getBossBar(p_136683_), BossEvent.BossBarColor.BLUE))
                                        )
                                        .then(
                                            Commands.literal("red")
                                                .executes(p_136681_ -> setColor(p_136681_.getSource(), getBossBar(p_136681_), BossEvent.BossBarColor.RED))
                                        )
                                        .then(
                                            Commands.literal("green")
                                                .executes(p_136679_ -> setColor(p_136679_.getSource(), getBossBar(p_136679_), BossEvent.BossBarColor.GREEN))
                                        )
                                        .then(
                                            Commands.literal("yellow")
                                                .executes(p_136677_ -> setColor(p_136677_.getSource(), getBossBar(p_136677_), BossEvent.BossBarColor.YELLOW))
                                        )
                                        .then(
                                            Commands.literal("purple")
                                                .executes(p_136675_ -> setColor(p_136675_.getSource(), getBossBar(p_136675_), BossEvent.BossBarColor.PURPLE))
                                        )
                                        .then(
                                            Commands.literal("white")
                                                .executes(p_136673_ -> setColor(p_136673_.getSource(), getBossBar(p_136673_), BossEvent.BossBarColor.WHITE))
                                        )
                                )
                                .then(
                                    Commands.literal("style")
                                        .then(
                                            Commands.literal("progress")
                                                .executes(
                                                    p_136671_ -> setStyle(p_136671_.getSource(), getBossBar(p_136671_), BossEvent.BossBarOverlay.PROGRESS)
                                                )
                                        )
                                        .then(
                                            Commands.literal("notched_6")
                                                .executes(
                                                    p_136669_ -> setStyle(p_136669_.getSource(), getBossBar(p_136669_), BossEvent.BossBarOverlay.NOTCHED_6)
                                                )
                                        )
                                        .then(
                                            Commands.literal("notched_10")
                                                .executes(
                                                    p_136667_ -> setStyle(p_136667_.getSource(), getBossBar(p_136667_), BossEvent.BossBarOverlay.NOTCHED_10)
                                                )
                                        )
                                        .then(
                                            Commands.literal("notched_12")
                                                .executes(
                                                    p_136665_ -> setStyle(p_136665_.getSource(), getBossBar(p_136665_), BossEvent.BossBarOverlay.NOTCHED_12)
                                                )
                                        )
                                        .then(
                                            Commands.literal("notched_20")
                                                .executes(
                                                    p_136663_ -> setStyle(p_136663_.getSource(), getBossBar(p_136663_), BossEvent.BossBarOverlay.NOTCHED_20)
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("value")
                                        .then(
                                            Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(
                                                    p_136661_ -> setValue(
                                                            p_136661_.getSource(), getBossBar(p_136661_), IntegerArgumentType.getInteger(p_136661_, "value")
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("max")
                                        .then(
                                            Commands.argument("max", IntegerArgumentType.integer(1))
                                                .executes(
                                                    p_136659_ -> setMax(
                                                            p_136659_.getSource(), getBossBar(p_136659_), IntegerArgumentType.getInteger(p_136659_, "max")
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("visible")
                                        .then(
                                            Commands.argument("visible", BoolArgumentType.bool())
                                                .executes(
                                                    p_136657_ -> setVisible(
                                                            p_136657_.getSource(), getBossBar(p_136657_), BoolArgumentType.getBool(p_136657_, "visible")
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("players")
                                        .executes(p_136655_ -> setPlayers(p_136655_.getSource(), getBossBar(p_136655_), Collections.emptyList()))
                                        .then(
                                            Commands.argument("targets", EntityArgument.players())
                                                .executes(
                                                    p_136653_ -> setPlayers(
                                                            p_136653_.getSource(),
                                                            getBossBar(p_136653_),
                                                            EntityArgument.getOptionalPlayers(p_136653_, "targets")
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("get")
                        .then(
                            Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_BOSS_BAR)
                                .then(Commands.literal("value").executes(p_136648_ -> getValue(p_136648_.getSource(), getBossBar(p_136648_))))
                                .then(Commands.literal("max").executes(p_136643_ -> getMax(p_136643_.getSource(), getBossBar(p_136643_))))
                                .then(Commands.literal("visible").executes(p_136638_ -> getVisible(p_136638_.getSource(), getBossBar(p_136638_))))
                                .then(Commands.literal("players").executes(p_136625_ -> getPlayers(p_136625_.getSource(), getBossBar(p_136625_))))
                        )
                )
        );
    }

    private static int getValue(CommandSourceStack source, CustomBossEvent bossbar) {
        source.sendSuccess(() -> Component.translatable("commands.bossbar.get.value", bossbar.getDisplayName(), bossbar.getValue()), true);
        return bossbar.getValue();
    }

    private static int getMax(CommandSourceStack source, CustomBossEvent bossbar) {
        source.sendSuccess(() -> Component.translatable("commands.bossbar.get.max", bossbar.getDisplayName(), bossbar.getMax()), true);
        return bossbar.getMax();
    }

    private static int getVisible(CommandSourceStack source, CustomBossEvent bossbar) {
        if (bossbar.isVisible()) {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.get.visible.visible", bossbar.getDisplayName()), true);
            return 1;
        } else {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.get.visible.hidden", bossbar.getDisplayName()), true);
            return 0;
        }
    }

    private static int getPlayers(CommandSourceStack source, CustomBossEvent bossbar) {
        if (bossbar.getPlayers().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.get.players.none", bossbar.getDisplayName()), true);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.bossbar.get.players.some",
                        bossbar.getDisplayName(),
                        bossbar.getPlayers().size(),
                        ComponentUtils.formatList(bossbar.getPlayers(), Player::getDisplayName)
                    ),
                true
            );
        }

        return bossbar.getPlayers().size();
    }

    private static int setVisible(CommandSourceStack source, CustomBossEvent bossbar, boolean visible) throws CommandSyntaxException {
        if (bossbar.isVisible() == visible) {
            if (visible) {
                throw ERROR_ALREADY_VISIBLE.create();
            } else {
                throw ERROR_ALREADY_HIDDEN.create();
            }
        } else {
            bossbar.setVisible(visible);
            if (visible) {
                source.sendSuccess(() -> Component.translatable("commands.bossbar.set.visible.success.visible", bossbar.getDisplayName()), true);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.bossbar.set.visible.success.hidden", bossbar.getDisplayName()), true);
            }

            return 0;
        }
    }

    private static int setValue(CommandSourceStack source, CustomBossEvent bossbar, int value) throws CommandSyntaxException {
        if (bossbar.getValue() == value) {
            throw ERROR_NO_VALUE_CHANGE.create();
        } else {
            bossbar.setValue(value);
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.value.success", bossbar.getDisplayName(), value), true);
            return value;
        }
    }

    private static int setMax(CommandSourceStack source, CustomBossEvent bossbar, int max) throws CommandSyntaxException {
        if (bossbar.getMax() == max) {
            throw ERROR_NO_MAX_CHANGE.create();
        } else {
            bossbar.setMax(max);
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.max.success", bossbar.getDisplayName(), max), true);
            return max;
        }
    }

    private static int setColor(CommandSourceStack source, CustomBossEvent bossbar, BossEvent.BossBarColor color) throws CommandSyntaxException {
        if (bossbar.getColor().equals(color)) {
            throw ERROR_NO_COLOR_CHANGE.create();
        } else {
            bossbar.setColor(color);
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.color.success", bossbar.getDisplayName()), true);
            return 0;
        }
    }

    private static int setStyle(CommandSourceStack source, CustomBossEvent bossbar, BossEvent.BossBarOverlay style) throws CommandSyntaxException {
        if (bossbar.getOverlay().equals(style)) {
            throw ERROR_NO_STYLE_CHANGE.create();
        } else {
            bossbar.setOverlay(style);
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.style.success", bossbar.getDisplayName()), true);
            return 0;
        }
    }

    private static int setName(CommandSourceStack source, CustomBossEvent bossbar, Component name) throws CommandSyntaxException {
        Component component = ComponentUtils.updateForEntity(source, name, null, 0);
        if (bossbar.getName().equals(component)) {
            throw ERROR_NO_NAME_CHANGE.create();
        } else {
            bossbar.setName(component);
            source.sendSuccess(() -> Component.translatable("commands.bossbar.set.name.success", bossbar.getDisplayName()), true);
            return 0;
        }
    }

    private static int setPlayers(CommandSourceStack source, CustomBossEvent bossbar, Collection<ServerPlayer> players) throws CommandSyntaxException {
        boolean flag = bossbar.setPlayers(players);
        if (!flag) {
            throw ERROR_NO_PLAYER_CHANGE.create();
        } else {
            if (bossbar.getPlayers().isEmpty()) {
                source.sendSuccess(() -> Component.translatable("commands.bossbar.set.players.success.none", bossbar.getDisplayName()), true);
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.bossbar.set.players.success.some",
                            bossbar.getDisplayName(),
                            players.size(),
                            ComponentUtils.formatList(players, Player::getDisplayName)
                        ),
                    true
                );
            }

            return bossbar.getPlayers().size();
        }
    }

    private static int listBars(CommandSourceStack source) {
        Collection<CustomBossEvent> collection = source.getServer().getCustomBossEvents().getEvents();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.bossbar.list.bars.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.bossbar.list.bars.some", collection.size(), ComponentUtils.formatList(collection, CustomBossEvent::getDisplayName)
                    ),
                false
            );
        }

        return collection.size();
    }

    private static int createBar(CommandSourceStack source, ResourceLocation id, Component displayName) throws CommandSyntaxException {
        CustomBossEvents custombossevents = source.getServer().getCustomBossEvents();
        if (custombossevents.get(id) != null) {
            throw ERROR_ALREADY_EXISTS.create(id.toString());
        } else {
            CustomBossEvent custombossevent = custombossevents.create(id, ComponentUtils.updateForEntity(source, displayName, null, 0));
            source.sendSuccess(() -> Component.translatable("commands.bossbar.create.success", custombossevent.getDisplayName()), true);
            return custombossevents.getEvents().size();
        }
    }

    private static int removeBar(CommandSourceStack source, CustomBossEvent bossbar) {
        CustomBossEvents custombossevents = source.getServer().getCustomBossEvents();
        bossbar.removeAllPlayers();
        custombossevents.remove(bossbar);
        source.sendSuccess(() -> Component.translatable("commands.bossbar.remove.success", bossbar.getDisplayName()), true);
        return custombossevents.getEvents().size();
    }

    public static CustomBossEvent getBossBar(CommandContext<CommandSourceStack> source) throws CommandSyntaxException {
        ResourceLocation resourcelocation = ResourceLocationArgument.getId(source, "id");
        CustomBossEvent custombossevent = source.getSource().getServer().getCustomBossEvents().get(resourcelocation);
        if (custombossevent == null) {
            throw ERROR_DOESNT_EXIST.create(resourcelocation.toString());
        } else {
            return custombossevent;
        }
    }
}
