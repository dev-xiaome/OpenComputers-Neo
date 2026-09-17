package net.minecraft.world.level.gameevent;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class EuclideanGameEventListenerRegistry implements GameEventListenerRegistry {
    private final List<GameEventListener> listeners = Lists.newArrayList();
    private final Set<GameEventListener> listenersToRemove = Sets.newHashSet();
    private final List<GameEventListener> listenersToAdd = Lists.newArrayList();
    private boolean processing;
    private final ServerLevel level;
    private final int sectionY;
    private final EuclideanGameEventListenerRegistry.OnEmptyAction onEmptyAction;

    public EuclideanGameEventListenerRegistry(ServerLevel level, int sectionY, EuclideanGameEventListenerRegistry.OnEmptyAction onEmptyAction) {
        this.level = level;
        this.sectionY = sectionY;
        this.onEmptyAction = onEmptyAction;
    }

    @Override
    public boolean isEmpty() {
        return this.listeners.isEmpty();
    }

    @Override
    public void register(GameEventListener listener) {
        if (this.processing) {
            this.listenersToAdd.add(listener);
        } else {
            this.listeners.add(listener);
        }

        DebugPackets.sendGameEventListenerInfo(this.level, listener);
    }

    @Override
    public void unregister(GameEventListener listener) {
        if (this.processing) {
            this.listenersToRemove.add(listener);
        } else {
            this.listeners.remove(listener);
        }

        if (this.listeners.isEmpty()) {
            this.onEmptyAction.apply(this.sectionY);
        }
    }

    @Override
    public boolean visitInRangeListeners(
        Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context, GameEventListenerRegistry.ListenerVisitor visitor
    ) {
        this.processing = true;
        boolean flag = false;

        try {
            Iterator<GameEventListener> iterator = this.listeners.iterator();

            while (iterator.hasNext()) {
                GameEventListener gameeventlistener = iterator.next();
                if (this.listenersToRemove.remove(gameeventlistener)) {
                    iterator.remove();
                } else {
                    Optional<Vec3> optional = getPostableListenerPosition(this.level, pos, gameeventlistener);
                    if (optional.isPresent()) {
                        visitor.visit(gameeventlistener, optional.get());
                        flag = true;
                    }
                }
            }
        } finally {
            this.processing = false;
        }

        if (!this.listenersToAdd.isEmpty()) {
            this.listeners.addAll(this.listenersToAdd);
            this.listenersToAdd.clear();
        }

        if (!this.listenersToRemove.isEmpty()) {
            this.listeners.removeAll(this.listenersToRemove);
            this.listenersToRemove.clear();
        }

        return flag;
    }

    private static Optional<Vec3> getPostableListenerPosition(ServerLevel level, Vec3 pos, GameEventListener listener) {
        Optional<Vec3> optional = listener.getListenerSource().getPosition(level);
        if (optional.isEmpty()) {
            return Optional.empty();
        } else {
            double d0 = BlockPos.containing(optional.get()).distSqr(BlockPos.containing(pos));
            int i = listener.getListenerRadius() * listener.getListenerRadius();
            return d0 > (double)i ? Optional.empty() : optional;
        }
    }

    @FunctionalInterface
    public interface OnEmptyAction {
        void apply(int sectionY);
    }
}
