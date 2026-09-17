package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

public class TickingTracker extends ChunkTracker {
    public static final int MAX_LEVEL = 33;
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
    private final Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = new Long2ObjectOpenHashMap<>();

    public TickingTracker() {
        super(34, 16, 256);
        this.chunks.defaultReturnValue((byte)33);
    }

    private SortedArraySet<Ticket<?>> getTickets(long chunkPos) {
        return this.tickets.computeIfAbsent(chunkPos, p_184180_ -> SortedArraySet.create(4));
    }

    private int getTicketLevelAt(SortedArraySet<Ticket<?>> tickets) {
        return tickets.isEmpty() ? 34 : tickets.first().getTicketLevel();
    }

    public void addTicket(long chunkPos, Ticket<?> ticket) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.getTickets(chunkPos);
        int i = this.getTicketLevelAt(sortedarrayset);
        sortedarrayset.add(ticket);
        if (ticket.getTicketLevel() < i) {
            this.update(chunkPos, ticket.getTicketLevel(), true);
        }
    }

    public void removeTicket(long chunkPos, Ticket<?> ticket) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.getTickets(chunkPos);
        sortedarrayset.remove(ticket);
        if (sortedarrayset.isEmpty()) {
            this.tickets.remove(chunkPos);
        }

        this.update(chunkPos, this.getTicketLevelAt(sortedarrayset), false);
    }

    public <T> void addTicket(TicketType<T> type, ChunkPos chunkPos, int ticketLevel, T key) {
        this.addTicket(chunkPos.toLong(), new Ticket<>(type, ticketLevel, key));
    }

    public <T> void removeTicket(TicketType<T> type, ChunkPos chunkPos, int ticketLevel, T key) {
        Ticket<T> ticket = new Ticket<>(type, ticketLevel, key);
        this.removeTicket(chunkPos.toLong(), ticket);
    }

    public void replacePlayerTicketsLevel(int ticketLevel) {
        List<Pair<Ticket<ChunkPos>, Long>> list = new ArrayList<>();

        for (Entry<SortedArraySet<Ticket<?>>> entry : this.tickets.long2ObjectEntrySet()) {
            for (Ticket<?> ticket : entry.getValue()) {
                if (ticket.getType() == TicketType.PLAYER) {
                    list.add(Pair.of((Ticket<ChunkPos>)ticket, entry.getLongKey()));
                }
            }
        }

        for (Pair<Ticket<ChunkPos>, Long> pair : list) {
            Long olong = pair.getSecond();
            Ticket<ChunkPos> ticket1 = pair.getFirst();
            this.removeTicket(olong, ticket1);
            ChunkPos chunkpos = new ChunkPos(olong);
            TicketType<ChunkPos> tickettype = ticket1.getType();
            this.addTicket(tickettype, chunkpos, ticketLevel, chunkpos);
        }
    }

    @Override
    protected int getLevelFromSource(long pos) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.tickets.get(pos);
        return sortedarrayset != null && !sortedarrayset.isEmpty() ? sortedarrayset.first().getTicketLevel() : Integer.MAX_VALUE;
    }

    public int getLevel(ChunkPos chunkPos) {
        return this.getLevel(chunkPos.toLong());
    }

    @Override
    protected int getLevel(long chunkPos) {
        return this.chunks.get(chunkPos);
    }

    @Override
    protected void setLevel(long chunkPos, int level) {
        if (level >= 33) {
            this.chunks.remove(chunkPos);
        } else {
            this.chunks.put(chunkPos, (byte)level);
        }
    }

    public void runAllUpdates() {
        this.runUpdates(Integer.MAX_VALUE);
    }

    public String getTicketDebugString(long chunkPos) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.tickets.get(chunkPos);
        return sortedarrayset != null && !sortedarrayset.isEmpty() ? sortedarrayset.first().toString() : "no_ticket";
    }
}
