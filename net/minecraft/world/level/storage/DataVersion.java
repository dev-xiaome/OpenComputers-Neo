package net.minecraft.world.level.storage;

public class DataVersion {
    private final int version;
    private final String series;
    public static String MAIN_SERIES = "main";

    public DataVersion(int version) {
        this(version, MAIN_SERIES);
    }

    public DataVersion(int version, String series) {
        this.version = version;
        this.series = series;
    }

    public boolean isSideSeries() {
        return !this.series.equals(MAIN_SERIES);
    }

    public String getSeries() {
        return this.series;
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isCompatible(DataVersion dataVersion) {
        return this.getSeries().equals(dataVersion.getSeries());
    }
}
