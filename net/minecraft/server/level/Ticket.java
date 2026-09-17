package net.minecraft.server.level;

import java.util.Objects;

public final class Ticket<T> implements Comparable<Ticket<?>> {
    private final TicketType<T> type;
    private final int ticketLevel;
    private final T key;
    private long createdTick;

    protected Ticket(TicketType<T> type, int ticketLevel, T key) {
        this(type, ticketLevel, key, false);
    }

    public Ticket(TicketType<T> type, int ticketLevel, T key, boolean forceTicks) {
        this.type = type;
        this.ticketLevel = ticketLevel;
        this.key = key;
        this.forceTicks = forceTicks;
    }

    public int compareTo(Ticket<?> other) {
        int i = Integer.compare(this.ticketLevel, other.ticketLevel);
        if (i != 0) {
            return i;
        } else {
            int j = Integer.compare(System.identityHashCode(this.type), System.identityHashCode(other.type));
            return j != 0 ? j : this.type.getComparator().compare(this.key, (T)other.key);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof Ticket<?> ticket)
                ? false
                : this.ticketLevel == ticket.ticketLevel && Objects.equals(this.type, ticket.type) && Objects.equals(this.key, ticket.key) && this.forceTicks == ticket.forceTicks;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.type, this.ticketLevel, this.key, this.forceTicks);
    }

    @Override
    public String toString() {
        return "Ticket[" + this.type + " " + this.ticketLevel + " (" + this.key + ")] at " + this.createdTick + " force ticks " + this.forceTicks;
    }

    public TicketType<T> getType() {
        return this.type;
    }

    public int getTicketLevel() {
        return this.ticketLevel;
    }

    protected void setCreatedTick(long timestamp) {
        this.createdTick = timestamp;
    }

    protected boolean timedOut(long currentTime) {
        long i = this.type.timeout();
        return i != 0L && currentTime - this.createdTick > i;
    }

    // Neo: Injected ability to force chunks to tick
    private final boolean forceTicks;

    public boolean isForceTicks() {
        return forceTicks;
    }
}
