package li.cil.oc.api.audio.synth;

import li.cil.oc.api.audio.AudioType;

public class Wave extends Generator {
    public AudioType type = AudioType.Square;

    public Wave() {
    }

    public Wave(AudioType type) {
        this.type = type;
    }
}