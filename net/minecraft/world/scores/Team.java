package net.minecraft.world.scores;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public abstract class Team {
    /**
     * Same as ==
     */
    public boolean isAlliedTo(@Nullable Team other) {
        return other == null ? false : this == other;
    }

    public abstract String getName();

    public abstract MutableComponent getFormattedName(Component formattedName);

    public abstract boolean canSeeFriendlyInvisibles();

    public abstract boolean isAllowFriendlyFire();

    public abstract Team.Visibility getNameTagVisibility();

    public abstract ChatFormatting getColor();

    public abstract Collection<String> getPlayers();

    public abstract Team.Visibility getDeathMessageVisibility();

    public abstract Team.CollisionRule getCollisionRule();

    public static enum CollisionRule {
        ALWAYS("always", 0),
        NEVER("never", 1),
        PUSH_OTHER_TEAMS("pushOtherTeams", 2),
        PUSH_OWN_TEAM("pushOwnTeam", 3);

        private static final Map<String, Team.CollisionRule> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toMap(p_83559_ -> p_83559_.name, p_83554_ -> (Team.CollisionRule)p_83554_));
        public final String name;
        public final int id;

        @Nullable
        public static Team.CollisionRule byName(String name) {
            return BY_NAME.get(name);
        }

        private CollisionRule(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public Component getDisplayName() {
            return Component.translatable("team.collision." + this.name);
        }
    }

    public static enum Visibility {
        ALWAYS("always", 0),
        NEVER("never", 1),
        HIDE_FOR_OTHER_TEAMS("hideForOtherTeams", 2),
        HIDE_FOR_OWN_TEAM("hideForOwnTeam", 3);

        private static final Map<String, Team.Visibility> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toMap(p_83583_ -> p_83583_.name, p_83578_ -> (Team.Visibility)p_83578_));
        public final String name;
        public final int id;

        public static String[] getAllNames() {
            return BY_NAME.keySet().toArray(new String[0]);
        }

        @Nullable
        public static Team.Visibility byName(String name) {
            return BY_NAME.get(name);
        }

        private Visibility(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public Component getDisplayName() {
            return Component.translatable("team.visibility." + this.name);
        }
    }
}
