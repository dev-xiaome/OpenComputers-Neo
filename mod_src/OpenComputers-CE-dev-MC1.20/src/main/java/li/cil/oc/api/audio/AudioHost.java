package li.cil.oc.api.audio;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public interface AudioHost {
    Level level();

    boolean tryChangeBuffer(double delta);

    String address();

    Vec3 position();

    void setChanged();

    int getId();
    
    Set<AudioReceiver> getReceivers();
}
