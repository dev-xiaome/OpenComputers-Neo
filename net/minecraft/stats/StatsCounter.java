package net.minecraft.stats;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Manages counting a set of {@link net.minecraft.stats.Stat} objects, stored by a map of statistics to their count.
 * <p>
 * This base {@code StatsCounter} is only used client-side for keeping track of and reading counts sent from the server.
 *
 * @see net.minecraft.stats.ServerStatsCounter
 */
public class StatsCounter {
    protected final Object2IntMap<Stat<?>> stats = Object2IntMaps.synchronize(new Object2IntOpenHashMap<>());

    public StatsCounter() {
        this.stats.defaultReturnValue(0);
    }

    public void increment(Player player, Stat<?> stat, int amount) {
        int i = (int)Math.min((long)this.getValue(stat) + (long)amount, 2147483647L);
        this.setValue(player, stat, i);
    }

    public void setValue(Player player, Stat<?> stat, int value) {
        net.neoforged.neoforge.event.StatAwardEvent event = net.neoforged.neoforge.event.EventHooks.onStatAward(player, stat,value);
        if (!event.isCanceled()) this.stats.put(event.getStat(), event.getValue());
    }

    public <T> int getValue(StatType<T> type, T value) {
        return type.contains(value) ? this.getValue(type.get(value)) : 0;
    }

    public int getValue(Stat<?> stat) {
        return this.stats.getInt(stat);
    }
}
