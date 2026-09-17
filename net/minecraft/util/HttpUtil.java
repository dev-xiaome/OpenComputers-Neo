package net.minecraft.util;

import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class HttpUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HttpUtil() {
    }

    public static Path downloadFile(
        Path saveFile,
        URL url,
        Map<String, String> requestProperties,
        HashFunction hashFunction,
        @Nullable HashCode hash,
        int maxSize,
        Proxy proxy,
        HttpUtil.DownloadProgressListener progressListener
    ) {
        HttpURLConnection httpurlconnection = null;
        InputStream inputstream = null;
        progressListener.requestStart();
        Path path;
        if (hash != null) {
            path = cachedFilePath(saveFile, hash);

            try {
                if (checkExistingFile(path, hashFunction, hash)) {
                    LOGGER.info("Returning cached file since actual hash matches requested");
                    progressListener.requestFinished(true);
                    updateModificationTime(path);
                    return path;
                }
            } catch (IOException ioexception1) {
                LOGGER.warn("Failed to check cached file {}", path, ioexception1);
            }

            try {
                LOGGER.warn("Existing file {} not found or had mismatched hash", path);
                Files.deleteIfExists(path);
            } catch (IOException ioexception) {
                progressListener.requestFinished(false);
                throw new UncheckedIOException("Failed to remove existing file " + path, ioexception);
            }
        } else {
            path = null;
        }

        Path $$18;
        try {
            httpurlconnection = (HttpURLConnection)url.openConnection(proxy);
            httpurlconnection.setInstanceFollowRedirects(true);
            requestProperties.forEach(httpurlconnection::setRequestProperty);
            inputstream = httpurlconnection.getInputStream();
            long i = httpurlconnection.getContentLengthLong();
            OptionalLong optionallong = i != -1L ? OptionalLong.of(i) : OptionalLong.empty();
            FileUtil.createDirectoriesSafe(saveFile);
            progressListener.downloadStart(optionallong);
            if (optionallong.isPresent() && optionallong.getAsLong() > (long)maxSize) {
                throw new IOException("Filesize is bigger than maximum allowed (file is " + optionallong + ", limit is " + maxSize + ")");
            }

            if (path == null) {
                Path path3 = Files.createTempFile(saveFile, "download", ".tmp");

                try {
                    HashCode hashcode1 = downloadAndHash(hashFunction, maxSize, progressListener, inputstream, path3);
                    Path path2 = cachedFilePath(saveFile, hashcode1);
                    if (!checkExistingFile(path2, hashFunction, hashcode1)) {
                        Files.move(path3, path2, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        updateModificationTime(path2);
                    }

                    progressListener.requestFinished(true);
                    return path2;
                } finally {
                    Files.deleteIfExists(path3);
                }
            }

            HashCode hashcode = downloadAndHash(hashFunction, maxSize, progressListener, inputstream, path);
            if (!hashcode.equals(hash)) {
                throw new IOException("Hash of downloaded file (" + hashcode + ") did not match requested (" + hash + ")");
            }

            progressListener.requestFinished(true);
            $$18 = path;
        } catch (Throwable throwable) {
            if (httpurlconnection != null) {
                InputStream inputstream1 = httpurlconnection.getErrorStream();
                if (inputstream1 != null) {
                    try {
                        LOGGER.error("HTTP response error: {}", IOUtils.toString(inputstream1, StandardCharsets.UTF_8));
                    } catch (Exception exception) {
                        LOGGER.error("Failed to read response from server");
                    }
                }
            }

            progressListener.requestFinished(false);
            throw new IllegalStateException("Failed to download file " + url, throwable);
        } finally {
            IOUtils.closeQuietly(inputstream);
        }

        return $$18;
    }

    private static void updateModificationTime(Path path) {
        try {
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to update modification time of {}", path, ioexception);
        }
    }

    private static HashCode hashFile(Path path, HashFunction hashFunction) throws IOException {
        Hasher hasher = hashFunction.newHasher();

        try (
            OutputStream outputstream = Funnels.asOutputStream(hasher);
            InputStream inputstream = Files.newInputStream(path);
        ) {
            inputstream.transferTo(outputstream);
        }

        return hasher.hash();
    }

    private static boolean checkExistingFile(Path path, HashFunction hashFunction, HashCode expectedHash) throws IOException {
        if (Files.exists(path)) {
            HashCode hashcode = hashFile(path, hashFunction);
            if (hashcode.equals(expectedHash)) {
                return true;
            }

            LOGGER.warn("Mismatched hash of file {}, expected {} but found {}", path, expectedHash, hashcode);
        }

        return false;
    }

    private static Path cachedFilePath(Path path, HashCode hash) {
        return path.resolve(hash.toString());
    }

    private static HashCode downloadAndHash(
        HashFunction hashFuntion, int maxSize, HttpUtil.DownloadProgressListener progressListener, InputStream stream, Path outputPath
    ) throws IOException {
        HashCode hashcode;
        try (OutputStream outputstream = Files.newOutputStream(outputPath, StandardOpenOption.CREATE)) {
            Hasher hasher = hashFuntion.newHasher();
            byte[] abyte = new byte[8196];
            long j = 0L;

            int i;
            while ((i = stream.read(abyte)) >= 0) {
                j += (long)i;
                progressListener.downloadedBytes(j);
                if (j > (long)maxSize) {
                    throw new IOException("Filesize was bigger than maximum allowed (got >= " + j + ", limit was " + maxSize + ")");
                }

                if (Thread.interrupted()) {
                    LOGGER.error("INTERRUPTED");
                    throw new IOException("Download interrupted");
                }

                outputstream.write(abyte, 0, i);
                hasher.putBytes(abyte, 0, i);
            }

            hashcode = hasher.hash();
        }

        return hashcode;
    }

    public static int getAvailablePort() {
        try {
            int i;
            try (ServerSocket serversocket = new ServerSocket(0)) {
                i = serversocket.getLocalPort();
            }

            return i;
        } catch (IOException ioexception) {
            return 25564;
        }
    }

    public static boolean isPortAvailable(int port) {
        if (port >= 0 && port <= 65535) {
            try {
                boolean flag;
                try (ServerSocket serversocket = new ServerSocket(port)) {
                    flag = serversocket.getLocalPort() == port;
                }

                return flag;
            } catch (IOException ioexception) {
                return false;
            }
        } else {
            return false;
        }
    }

    public interface DownloadProgressListener {
        void requestStart();

        void downloadStart(OptionalLong totalSize);

        void downloadedBytes(long progress);

        void requestFinished(boolean success);
    }
}
