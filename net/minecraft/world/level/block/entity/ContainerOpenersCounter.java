package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public abstract class ContainerOpenersCounter {
    private static final int CHECK_TICK_DELAY = 5;
    private int openCount;
    private double maxInteractionRange;

    protected abstract void onOpen(Level level, BlockPos pos, BlockState state);

    protected abstract void onClose(Level level, BlockPos pos, BlockState state);

    protected abstract void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int openCount);

    protected abstract boolean isOwnContainer(Player player);

    public void incrementOpeners(Player player, Level level, BlockPos pos, BlockState state) {
        int i = this.openCount++;
        if (i == 0) {
            this.onOpen(level, pos, state);
            level.gameEvent(player, GameEvent.CONTAINER_OPEN, pos);
            scheduleRecheck(level, pos, state);
        }

        this.openerCountChanged(level, pos, state, i, this.openCount);
        this.maxInteractionRange = Math.max(player.blockInteractionRange(), this.maxInteractionRange);
    }

    public void decrementOpeners(Player player, Level level, BlockPos pos, BlockState state) {
        int i = this.openCount--;
        if (this.openCount == 0) {
            this.onClose(level, pos, state);
            level.gameEvent(player, GameEvent.CONTAINER_CLOSE, pos);
            this.maxInteractionRange = 0.0;
        }

        this.openerCountChanged(level, pos, state, i, this.openCount);
    }

    private List<Player> getPlayersWithContainerOpen(Level level, BlockPos pos) {
        double d0 = this.maxInteractionRange + 4.0;
        AABB aabb = new AABB(pos).inflate(d0);
        return level.getEntities(EntityTypeTest.forClass(Player.class), aabb, this::isOwnContainer);
    }

    public void recheckOpeners(Level level, BlockPos pos, BlockState state) {
        List<Player> list = this.getPlayersWithContainerOpen(level, pos);
        this.maxInteractionRange = 0.0;

        for (Player player : list) {
            this.maxInteractionRange = Math.max(player.blockInteractionRange(), this.maxInteractionRange);
        }

        int i = list.size();
        int j = this.openCount;
        if (j != i) {
            boolean flag = i != 0;
            boolean flag1 = j != 0;
            if (flag && !flag1) {
                this.onOpen(level, pos, state);
                level.gameEvent(null, GameEvent.CONTAINER_OPEN, pos);
            } else if (!flag) {
                this.onClose(level, pos, state);
                level.gameEvent(null, GameEvent.CONTAINER_CLOSE, pos);
            }

            this.openCount = i;
        }

        this.openerCountChanged(level, pos, state, j, i);
        if (i > 0) {
            scheduleRecheck(level, pos, state);
        }
    }

    public int getOpenerCount() {
        return this.openCount;
    }

    private static void scheduleRecheck(Level level, BlockPos pos, BlockState state) {
        level.scheduleTick(pos, state.getBlock(), 5);
    }
}
