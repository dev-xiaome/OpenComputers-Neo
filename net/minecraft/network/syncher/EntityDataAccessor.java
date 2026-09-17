package net.minecraft.network.syncher;

/**
 * A Key for {@link SynchedEntityData}.
 */
public record EntityDataAccessor<T>(int id, EntityDataSerializer<T> serializer) {
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            EntityDataAccessor<?> entitydataaccessor = (EntityDataAccessor<?>)other;
            return this.id == entitydataaccessor.id;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.id;
    }

    @Override
    public String toString() {
        return "<entity data: " + this.id + ">";
    }
}
