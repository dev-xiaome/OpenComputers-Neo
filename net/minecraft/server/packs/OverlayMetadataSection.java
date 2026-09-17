package net.minecraft.server.packs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.InclusiveRange;

public record OverlayMetadataSection(List<OverlayMetadataSection.OverlayEntry> overlays) {
    private static final Pattern DIR_VALIDATOR = Pattern.compile("[-_a-zA-Z0-9.]+");
    private static final Codec<OverlayMetadataSection> CODEC = RecordCodecBuilder.create(
        p_294898_ -> p_294898_.group(net.neoforged.neoforge.common.conditions.ConditionalOps.decodeListWithElementConditions(OverlayMetadataSection.OverlayEntry.CODEC).fieldOf("entries").forGetter(OverlayMetadataSection::overlays))
                .apply(p_294898_, OverlayMetadataSection::new)
    );
    public static final MetadataSectionType<OverlayMetadataSection> TYPE = MetadataSectionType.fromCodec("overlays", CODEC);
    // Neo: alternative metadata section that will only be loaded on neoforged. Useful for making datapacks with special
    // logic on different modloaders, or when running on neo vs in vanilla, without having to invert the main pack and overlays
    public static final MetadataSectionType<OverlayMetadataSection> NEOFORGE_TYPE = MetadataSectionType.fromCodec("neoforge:overlays", CODEC);

    private static DataResult<String> validateOverlayDir(String directoryName) {
        return !DIR_VALIDATOR.matcher(directoryName).matches()
            ? DataResult.error(() -> directoryName + " is not accepted directory name")
            : DataResult.success(directoryName);
    }

    public List<String> overlaysForVersion(int version) {
        return this.overlays.stream().filter(p_296207_ -> p_296207_.isApplicable(version)).map(OverlayMetadataSection.OverlayEntry::overlay).toList();
    }

    public static record OverlayEntry(InclusiveRange<Integer> format, String overlay) {
        public static final Codec<OverlayMetadataSection.OverlayEntry> CODEC = RecordCodecBuilder.create(
            p_337556_ -> p_337556_.group(
                        InclusiveRange.codec(Codec.INT).fieldOf("formats").forGetter(OverlayMetadataSection.OverlayEntry::format),
                        Codec.STRING
                            .validate(OverlayMetadataSection::validateOverlayDir)
                            .fieldOf("directory")
                            .forGetter(OverlayMetadataSection.OverlayEntry::overlay)
                    )
                    .apply(p_337556_, OverlayMetadataSection.OverlayEntry::new)
        );

        public boolean isApplicable(int version) {
            return this.format.isValueInRange(version);
        }
    }
}
