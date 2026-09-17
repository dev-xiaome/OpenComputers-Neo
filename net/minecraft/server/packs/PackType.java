package net.minecraft.server.packs;

public enum PackType implements net.minecraft.util.StringRepresentable {
    CLIENT_RESOURCES("assets"),
    SERVER_DATA("data");

    private final String directory;

    private PackType(String directory) {
        this.directory = directory;
    }

    public String getDirectory() {
        return this.directory;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
