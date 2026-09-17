package net.minecraft.server;

import com.mojang.datafixers.util.Either;
import io.netty.buffer.ByteBuf;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

public record ServerLinks(List<ServerLinks.Entry> entries) {
    public static final ServerLinks EMPTY = new ServerLinks(List.of());
    public static final StreamCodec<ByteBuf, Either<ServerLinks.KnownLinkType, Component>> TYPE_STREAM_CODEC = ByteBufCodecs.either(
        ServerLinks.KnownLinkType.STREAM_CODEC, ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC
    );
    public static final StreamCodec<ByteBuf, List<ServerLinks.UntrustedEntry>> UNTRUSTED_LINKS_STREAM_CODEC = ServerLinks.UntrustedEntry.STREAM_CODEC
        .apply(ByteBufCodecs.list());

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public Optional<ServerLinks.Entry> findKnownType(ServerLinks.KnownLinkType type) {
        return this.entries.stream().filter(p_350399_ -> p_350399_.type.map(p_350899_ -> p_350899_ == type, p_350339_ -> false)).findFirst();
    }

    public List<ServerLinks.UntrustedEntry> untrust() {
        return this.entries.stream().map(p_351724_ -> new ServerLinks.UntrustedEntry(p_351724_.type, p_351724_.link.toString())).toList();
    }

    public static record Entry(Either<ServerLinks.KnownLinkType, Component> type, URI link) {
        public static ServerLinks.Entry knownType(ServerLinks.KnownLinkType type, URI link) {
            return new ServerLinks.Entry(Either.left(type), link);
        }

        public static ServerLinks.Entry custom(Component type, URI link) {
            return new ServerLinks.Entry(Either.right(type), link);
        }

        public Component displayName() {
            return this.type.map(ServerLinks.KnownLinkType::displayName, p_350340_ -> (Component)p_350340_);
        }
    }

    public static enum KnownLinkType {
        BUG_REPORT(0, "report_bug"),
        COMMUNITY_GUIDELINES(1, "community_guidelines"),
        SUPPORT(2, "support"),
        STATUS(3, "status"),
        FEEDBACK(4, "feedback"),
        COMMUNITY(5, "community"),
        WEBSITE(6, "website"),
        FORUMS(7, "forums"),
        NEWS(8, "news"),
        ANNOUNCEMENTS(9, "announcements");

        private static final IntFunction<ServerLinks.KnownLinkType> BY_ID = ByIdMap.continuous(
            p_350758_ -> p_350758_.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO
        );
        public static final StreamCodec<ByteBuf, ServerLinks.KnownLinkType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, p_350736_ -> p_350736_.id);
        private final int id;
        private final String name;

        private KnownLinkType(int id, String name) {
            this.id = id;
            this.name = name;
        }

        private Component displayName() {
            return Component.translatable("known_server_link." + this.name);
        }

        public ServerLinks.Entry create(URI uri) {
            return ServerLinks.Entry.knownType(this, uri);
        }
    }

    public static record UntrustedEntry(Either<ServerLinks.KnownLinkType, Component> type, String link) {
        public static final StreamCodec<ByteBuf, ServerLinks.UntrustedEntry> STREAM_CODEC = StreamCodec.composite(
            ServerLinks.TYPE_STREAM_CODEC,
            ServerLinks.UntrustedEntry::type,
            ByteBufCodecs.STRING_UTF8,
            ServerLinks.UntrustedEntry::link,
            ServerLinks.UntrustedEntry::new
        );
    }
}
