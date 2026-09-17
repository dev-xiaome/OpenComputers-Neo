package net.minecraft.advancements;

import java.time.Instant;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

public class CriterionProgress {
    @Nullable
    private Instant obtained;

    public CriterionProgress() {
    }

    public CriterionProgress(Instant obtained) {
        this.obtained = obtained;
    }

    public boolean isDone() {
        return this.obtained != null;
    }

    public void grant() {
        this.obtained = Instant.now();
    }

    public void revoke() {
        this.obtained = null;
    }

    @Nullable
    public Instant getObtained() {
        return this.obtained;
    }

    @Override
    public String toString() {
        return "CriterionProgress{obtained=" + (this.obtained == null ? "false" : this.obtained) + "}";
    }

    public void serializeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeNullable(this.obtained, FriendlyByteBuf::writeInstant);
    }

    public static CriterionProgress fromNetwork(FriendlyByteBuf buffer) {
        CriterionProgress criterionprogress = new CriterionProgress();
        criterionprogress.obtained = buffer.readNullable(FriendlyByteBuf::readInstant);
        return criterionprogress;
    }
}
