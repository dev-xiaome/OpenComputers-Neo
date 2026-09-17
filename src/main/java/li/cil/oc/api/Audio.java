package li.cil.oc.api;

public final class Audio {
    public static int getSampleRate() {
        if (API.audio != null)
            return API.audio.getSampleRate();
        return -1;
    }
}
