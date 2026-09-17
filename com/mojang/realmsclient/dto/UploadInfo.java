package com.mojang.realmsclient.dto;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class UploadInfo extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_SCHEMA = "http://";
    private static final int DEFAULT_PORT = 8080;
    private static final Pattern URI_SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z][-a-zA-Z0-9+.]+:");
    private final boolean worldClosed;
    @Nullable
    private final String token;
    private final URI uploadEndpoint;

    private UploadInfo(boolean worldClosed, @Nullable String token, URI uploadEndpoint) {
        this.worldClosed = worldClosed;
        this.token = token;
        this.uploadEndpoint = uploadEndpoint;
    }

    @Nullable
    public static UploadInfo parse(String json) {
        try {
            JsonParser jsonparser = new JsonParser();
            JsonObject jsonobject = jsonparser.parse(json).getAsJsonObject();
            String s = JsonUtils.getStringOr("uploadEndpoint", jsonobject, null);
            if (s != null) {
                int i = JsonUtils.getIntOr("port", jsonobject, -1);
                URI uri = assembleUri(s, i);
                if (uri != null) {
                    boolean flag = JsonUtils.getBooleanOr("worldClosed", jsonobject, false);
                    String s1 = JsonUtils.getStringOr("token", jsonobject, null);
                    return new UploadInfo(flag, s1, uri);
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Could not parse UploadInfo: {}", exception.getMessage());
        }

        return null;
    }

    @Nullable
    @VisibleForTesting
    public static URI assembleUri(String p_uri, int port) {
        Matcher matcher = URI_SCHEMA_PATTERN.matcher(p_uri);
        String s = ensureEndpointSchema(p_uri, matcher);

        try {
            URI uri = new URI(s);
            int i = selectPortOrDefault(port, uri.getPort());
            return i != uri.getPort() ? new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), i, uri.getPath(), uri.getQuery(), uri.getFragment()) : uri;
        } catch (URISyntaxException urisyntaxexception) {
            LOGGER.warn("Failed to parse URI {}", s, urisyntaxexception);
            return null;
        }
    }

    private static int selectPortOrDefault(int port, int defaultPort) {
        if (port != -1) {
            return port;
        } else {
            return defaultPort != -1 ? defaultPort : 8080;
        }
    }

    private static String ensureEndpointSchema(String uri, Matcher matcher) {
        return matcher.find() ? uri : "http://" + uri;
    }

    public static String createRequest(@Nullable String token) {
        JsonObject jsonobject = new JsonObject();
        if (token != null) {
            jsonobject.addProperty("token", token);
        }

        return jsonobject.toString();
    }

    @Nullable
    public String getToken() {
        return this.token;
    }

    public URI getUploadEndpoint() {
        return this.uploadEndpoint;
    }

    public boolean isWorldClosed() {
        return this.worldClosed;
    }
}
