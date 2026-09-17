package li.cil.oc.api.audio;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public interface AudioReceiver {
    Level level();

    String address();

    Vec3 position();

    void setChanged();
    
    int distance();
}
