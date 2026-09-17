package li.cil.oc.api.audio;

public enum AudioMode {
    MONO_8(8),
    MONO_16(16),
    @Deprecated
    STEREO_8(8, true),
    @Deprecated
    STEREO_16(16, true);

    private final int bit;
    private final boolean stereo;

    AudioMode(int bit, boolean stereo) {
        this.bit = bit;
        this.stereo = stereo;
    }

    AudioMode(int bit) {
        this.bit = bit;
        this.stereo = false;
    }

    public int getBit() {
        return bit;
    }

    public boolean isStereo() {
        return stereo;
    }
}
