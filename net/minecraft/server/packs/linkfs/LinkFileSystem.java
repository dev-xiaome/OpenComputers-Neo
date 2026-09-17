package net.minecraft.server.packs.linkfs;

import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class LinkFileSystem extends FileSystem {
    private static final Set<String> VIEWS = Set.of("basic");
    public static final String PATH_SEPARATOR = "/";
    private static final Splitter PATH_SPLITTER = Splitter.on('/');
    private final FileStore store;
    private final FileSystemProvider provider = new LinkFSProvider();
    private final LinkFSPath root;

    LinkFileSystem(String name, LinkFileSystem.DirectoryEntry root) {
        this.store = new LinkFSFileStore(name);
        this.root = buildPath(root, this, "", null);
    }

    private static LinkFSPath buildPath(LinkFileSystem.DirectoryEntry directory, LinkFileSystem fileSystem, String name, @Nullable LinkFSPath parent) {
        Object2ObjectOpenHashMap<String, LinkFSPath> object2objectopenhashmap = new Object2ObjectOpenHashMap<>();
        LinkFSPath linkfspath = new LinkFSPath(fileSystem, name, parent, new PathContents.DirectoryContents(object2objectopenhashmap));
        directory.files
            .forEach(
                (p_249491_, p_250850_) -> object2objectopenhashmap.put(
                        p_249491_, new LinkFSPath(fileSystem, p_249491_, linkfspath, new PathContents.FileContents(p_250850_))
                    )
            );
        directory.children.forEach((p_251592_, p_251728_) -> object2objectopenhashmap.put(p_251592_, buildPath(p_251728_, fileSystem, p_251592_, linkfspath)));
        object2objectopenhashmap.trim();
        return linkfspath;
    }

    @Override
    public FileSystemProvider provider() {
        return this.provider;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return List.of(this.root);
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return List.of(this.store);
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return VIEWS;
    }

    @Override
    public Path getPath(String first, String... more) {
        Stream<String> stream = Stream.of(first);
        if (more.length > 0) {
            stream = Stream.concat(stream, Stream.of(more));
        }

        String s = stream.collect(Collectors.joining("/"));
        if (s.equals("/")) {
            return this.root;
        } else if (s.startsWith("/")) {
            LinkFSPath linkfspath1 = this.root;

            for (String s2 : PATH_SPLITTER.split(s.substring(1))) {
                if (s2.isEmpty()) {
                    throw new IllegalArgumentException("Empty paths not allowed");
                }

                linkfspath1 = linkfspath1.resolveName(s2);
            }

            return linkfspath1;
        } else {
            LinkFSPath linkfspath = null;

            for (String s1 : PATH_SPLITTER.split(s)) {
                if (s1.isEmpty()) {
                    throw new IllegalArgumentException("Empty paths not allowed");
                }

                linkfspath = new LinkFSPath(this, s1, linkfspath, PathContents.RELATIVE);
            }

            if (linkfspath == null) {
                throw new IllegalArgumentException("Empty paths not allowed");
            } else {
                return linkfspath;
            }
        }
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    public FileStore store() {
        return this.store;
    }

    public LinkFSPath rootPath() {
        return this.root;
    }

    public static LinkFileSystem.Builder builder() {
        return new LinkFileSystem.Builder();
    }

    public static class Builder {
        private final LinkFileSystem.DirectoryEntry root = new LinkFileSystem.DirectoryEntry();

        public LinkFileSystem.Builder put(List<String> pathString, String fileName, Path filePath) {
            LinkFileSystem.DirectoryEntry linkfilesystem$directoryentry = this.root;

            for (String s : pathString) {
                linkfilesystem$directoryentry = linkfilesystem$directoryentry.children.computeIfAbsent(s, p_249671_ -> new LinkFileSystem.DirectoryEntry());
            }

            linkfilesystem$directoryentry.files.put(fileName, filePath);
            return this;
        }

        public LinkFileSystem.Builder put(List<String> pathString, Path filePath) {
            if (pathString.isEmpty()) {
                throw new IllegalArgumentException("Path can't be empty");
            } else {
                int i = pathString.size() - 1;
                return this.put(pathString.subList(0, i), pathString.get(i), filePath);
            }
        }

        public FileSystem build(String name) {
            return new LinkFileSystem(name, this.root);
        }
    }

    static record DirectoryEntry(Map<String, LinkFileSystem.DirectoryEntry> children, Map<String, Path> files) {
        public DirectoryEntry() {
            this(new HashMap<>(), new HashMap<>());
        }
    }
}
