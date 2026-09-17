package net.minecraft.server.network;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.thread.ProcessorMailbox;
import org.slf4j.Logger;

public class TextFilterClient implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger WORKER_COUNT = new AtomicInteger(1);
    private static final ThreadFactory THREAD_FACTORY = p_10148_ -> {
        Thread thread = new Thread(p_10148_);
        thread.setName("Chat-Filter-Worker-" + WORKER_COUNT.getAndIncrement());
        return thread;
    };
    private static final String DEFAULT_ENDPOINT = "v1/chat";
    private final URL chatEndpoint;
    private final TextFilterClient.MessageEncoder chatEncoder;
    final URL joinEndpoint;
    final TextFilterClient.JoinOrLeaveEncoder joinEncoder;
    final URL leaveEndpoint;
    final TextFilterClient.JoinOrLeaveEncoder leaveEncoder;
    private final String authKey;
    final TextFilterClient.IgnoreStrategy chatIgnoreStrategy;
    final ExecutorService workerPool;

    private TextFilterClient(
        URL chatEndpoint,
        TextFilterClient.MessageEncoder chatEncoder,
        URL joinEndpoint,
        TextFilterClient.JoinOrLeaveEncoder joinEncoder,
        URL leaveEndpoint,
        TextFilterClient.JoinOrLeaveEncoder leaveEncoder,
        String authKey,
        TextFilterClient.IgnoreStrategy chatIgnoreStrategy,
        int workerThreadCount
    ) {
        this.authKey = authKey;
        this.chatIgnoreStrategy = chatIgnoreStrategy;
        this.chatEndpoint = chatEndpoint;
        this.chatEncoder = chatEncoder;
        this.joinEndpoint = joinEndpoint;
        this.joinEncoder = joinEncoder;
        this.leaveEndpoint = leaveEndpoint;
        this.leaveEncoder = leaveEncoder;
        this.workerPool = Executors.newFixedThreadPool(workerThreadCount, THREAD_FACTORY);
    }

    private static URL getEndpoint(URI uri, @Nullable JsonObject json, String memberName, String fallback) throws MalformedURLException {
        String s = getEndpointFromConfig(json, memberName, fallback);
        return uri.resolve("/" + s).toURL();
    }

    private static String getEndpointFromConfig(@Nullable JsonObject json, String memberName, String fallback) {
        return json != null ? GsonHelper.getAsString(json, memberName, fallback) : fallback;
    }

    @Nullable
    public static TextFilterClient createFromConfig(String config) {
        if (Strings.isNullOrEmpty(config)) {
            return null;
        } else {
            try {
                JsonObject jsonobject = GsonHelper.parse(config);
                URI uri = new URI(GsonHelper.getAsString(jsonobject, "apiServer"));
                String s = GsonHelper.getAsString(jsonobject, "apiKey");
                if (s.isEmpty()) {
                    throw new IllegalArgumentException("Missing API key");
                } else {
                    int i = GsonHelper.getAsInt(jsonobject, "ruleId", 1);
                    String s1 = GsonHelper.getAsString(jsonobject, "serverId", "");
                    String s2 = GsonHelper.getAsString(jsonobject, "roomId", "Java:Chat");
                    int j = GsonHelper.getAsInt(jsonobject, "hashesToDrop", -1);
                    int k = GsonHelper.getAsInt(jsonobject, "maxConcurrentRequests", 7);
                    JsonObject jsonobject1 = GsonHelper.getAsJsonObject(jsonobject, "endpoints", null);
                    String s3 = getEndpointFromConfig(jsonobject1, "chat", "v1/chat");
                    boolean flag = s3.equals("v1/chat");
                    URL url = uri.resolve("/" + s3).toURL();
                    URL url1 = getEndpoint(uri, jsonobject1, "join", "v1/join");
                    URL url2 = getEndpoint(uri, jsonobject1, "leave", "v1/leave");
                    TextFilterClient.JoinOrLeaveEncoder textfilterclient$joinorleaveencoder = p_215310_ -> {
                        JsonObject jsonobject2 = new JsonObject();
                        jsonobject2.addProperty("server", s1);
                        jsonobject2.addProperty("room", s2);
                        jsonobject2.addProperty("user_id", p_215310_.getId().toString());
                        jsonobject2.addProperty("user_display_name", p_215310_.getName());
                        return jsonobject2;
                    };
                    TextFilterClient.MessageEncoder textfilterclient$messageencoder;
                    if (flag) {
                        textfilterclient$messageencoder = (p_238214_, p_238215_) -> {
                            JsonObject jsonobject2 = new JsonObject();
                            jsonobject2.addProperty("rule", i);
                            jsonobject2.addProperty("server", s1);
                            jsonobject2.addProperty("room", s2);
                            jsonobject2.addProperty("player", p_238214_.getId().toString());
                            jsonobject2.addProperty("player_display_name", p_238214_.getName());
                            jsonobject2.addProperty("text", p_238215_);
                            jsonobject2.addProperty("language", "*");
                            return jsonobject2;
                        };
                    } else {
                        String s4 = String.valueOf(i);
                        textfilterclient$messageencoder = (p_238220_, p_238221_) -> {
                            JsonObject jsonobject2 = new JsonObject();
                            jsonobject2.addProperty("rule_id", s4);
                            jsonobject2.addProperty("category", s1);
                            jsonobject2.addProperty("subcategory", s2);
                            jsonobject2.addProperty("user_id", p_238220_.getId().toString());
                            jsonobject2.addProperty("user_display_name", p_238220_.getName());
                            jsonobject2.addProperty("text", p_238221_);
                            jsonobject2.addProperty("language", "*");
                            return jsonobject2;
                        };
                    }

                    TextFilterClient.IgnoreStrategy textfilterclient$ignorestrategy = TextFilterClient.IgnoreStrategy.select(j);
                    String s5 = Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.US_ASCII));
                    return new TextFilterClient(
                        url,
                        textfilterclient$messageencoder,
                        url1,
                        textfilterclient$joinorleaveencoder,
                        url2,
                        textfilterclient$joinorleaveencoder,
                        s5,
                        textfilterclient$ignorestrategy,
                        k
                    );
                }
            } catch (Exception exception) {
                LOGGER.warn("Failed to parse chat filter config {}", config, exception);
                return null;
            }
        }
    }

    void processJoinOrLeave(GameProfile profile, URL url, TextFilterClient.JoinOrLeaveEncoder encoder, Executor executor) {
        executor.execute(() -> {
            JsonObject jsonobject = encoder.encode(profile);

            try {
                this.processRequest(jsonobject, url);
            } catch (Exception exception) {
                LOGGER.warn("Failed to send join/leave packet to {} for player {}", url, profile, exception);
            }
        });
    }

    CompletableFuture<FilteredText> requestMessageProcessing(GameProfile profile, String text, TextFilterClient.IgnoreStrategy ignoreStrategy, Executor executor) {
        return text.isEmpty() ? CompletableFuture.completedFuture(FilteredText.EMPTY) : CompletableFuture.supplyAsync(() -> {
            JsonObject jsonobject = this.chatEncoder.encode(profile, text);

            try {
                JsonObject jsonobject1 = this.processRequestResponse(jsonobject, this.chatEndpoint);
                boolean flag = GsonHelper.getAsBoolean(jsonobject1, "response", false);
                if (flag) {
                    return FilteredText.passThrough(text);
                } else {
                    String s = GsonHelper.getAsString(jsonobject1, "hashed", null);
                    if (s == null) {
                        return FilteredText.fullyFiltered(text);
                    } else {
                        JsonArray jsonarray = GsonHelper.getAsJsonArray(jsonobject1, "hashes");
                        FilterMask filtermask = this.parseMask(text, jsonarray, ignoreStrategy);
                        return new FilteredText(text, filtermask);
                    }
                }
            } catch (Exception exception) {
                LOGGER.warn("Failed to validate message '{}'", text, exception);
                return FilteredText.fullyFiltered(text);
            }
        }, executor);
    }

    private FilterMask parseMask(String text, JsonArray hashes, TextFilterClient.IgnoreStrategy ignoreStrategy) {
        if (hashes.isEmpty()) {
            return FilterMask.PASS_THROUGH;
        } else if (ignoreStrategy.shouldIgnore(text, hashes.size())) {
            return FilterMask.FULLY_FILTERED;
        } else {
            FilterMask filtermask = new FilterMask(text.length());

            for (int i = 0; i < hashes.size(); i++) {
                filtermask.setFiltered(hashes.get(i).getAsInt());
            }

            return filtermask;
        }
    }

    @Override
    public void close() {
        this.workerPool.shutdownNow();
    }

    private void drainStream(InputStream stream) throws IOException {
        byte[] abyte = new byte[1024];

        while (stream.read(abyte) != -1) {
        }
    }

    private JsonObject processRequestResponse(JsonObject json, URL requestUrl) throws IOException {
        HttpURLConnection httpurlconnection = this.makeRequest(json, requestUrl);

        JsonObject jsonobject;
        try (InputStream inputstream = httpurlconnection.getInputStream()) {
            if (httpurlconnection.getResponseCode() == 204) {
                return new JsonObject();
            }

            try {
                jsonobject = Streams.parse(new JsonReader(new InputStreamReader(inputstream, StandardCharsets.UTF_8))).getAsJsonObject();
            } finally {
                this.drainStream(inputstream);
            }
        }

        return jsonobject;
    }

    private void processRequest(JsonObject json, URL requestUrl) throws IOException {
        HttpURLConnection httpurlconnection = this.makeRequest(json, requestUrl);

        try (InputStream inputstream = httpurlconnection.getInputStream()) {
            this.drainStream(inputstream);
        }
    }

    private HttpURLConnection makeRequest(JsonObject json, URL requestUrl) throws IOException {
        HttpURLConnection httpurlconnection = (HttpURLConnection)requestUrl.openConnection();
        httpurlconnection.setConnectTimeout(15000);
        httpurlconnection.setReadTimeout(2000);
        httpurlconnection.setUseCaches(false);
        httpurlconnection.setDoOutput(true);
        httpurlconnection.setDoInput(true);
        httpurlconnection.setRequestMethod("POST");
        httpurlconnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        httpurlconnection.setRequestProperty("Accept", "application/json");
        httpurlconnection.setRequestProperty("Authorization", "Basic " + this.authKey);
        httpurlconnection.setRequestProperty("User-Agent", "Minecraft server" + SharedConstants.getCurrentVersion().getName());
        OutputStreamWriter outputstreamwriter = new OutputStreamWriter(httpurlconnection.getOutputStream(), StandardCharsets.UTF_8);

        try (JsonWriter jsonwriter = new JsonWriter(outputstreamwriter)) {
            Streams.write(json, jsonwriter);
        } catch (Throwable throwable1) {
            try {
                outputstreamwriter.close();
            } catch (Throwable throwable) {
                throwable1.addSuppressed(throwable);
            }

            throw throwable1;
        }

        outputstreamwriter.close();
        int i = httpurlconnection.getResponseCode();
        if (i >= 200 && i < 300) {
            return httpurlconnection;
        } else {
            throw new TextFilterClient.RequestFailedException(i + " " + httpurlconnection.getResponseMessage());
        }
    }

    public TextFilter createContext(GameProfile profile) {
        return new TextFilterClient.PlayerContext(profile);
    }

    @FunctionalInterface
    public interface IgnoreStrategy {
        TextFilterClient.IgnoreStrategy NEVER_IGNORE = (p_10169_, p_10170_) -> false;
        TextFilterClient.IgnoreStrategy IGNORE_FULLY_FILTERED = (p_10166_, p_10167_) -> p_10166_.length() == p_10167_;

        static TextFilterClient.IgnoreStrategy ignoreOverThreshold(int threshold) {
            return (p_143742_, p_143743_) -> p_143743_ >= threshold;
        }

        static TextFilterClient.IgnoreStrategy select(int threshold) {
            return switch (threshold) {
                case -1 -> NEVER_IGNORE;
                case 0 -> IGNORE_FULLY_FILTERED;
                default -> ignoreOverThreshold(threshold);
            };
        }

        boolean shouldIgnore(String text, int threshold);
    }

    @FunctionalInterface
    interface JoinOrLeaveEncoder {
        JsonObject encode(GameProfile profile);
    }

    @FunctionalInterface
    interface MessageEncoder {
        JsonObject encode(GameProfile profile, String text);
    }

    class PlayerContext implements TextFilter {
        private final GameProfile profile;
        private final Executor streamExecutor;

        PlayerContext(GameProfile profile) {
            this.profile = profile;
            ProcessorMailbox<Runnable> processormailbox = ProcessorMailbox.create(TextFilterClient.this.workerPool, "chat stream for " + profile.getName());
            this.streamExecutor = processormailbox::tell;
        }

        @Override
        public void join() {
            TextFilterClient.this.processJoinOrLeave(this.profile, TextFilterClient.this.joinEndpoint, TextFilterClient.this.joinEncoder, this.streamExecutor);
        }

        @Override
        public void leave() {
            TextFilterClient.this.processJoinOrLeave(this.profile, TextFilterClient.this.leaveEndpoint, TextFilterClient.this.leaveEncoder, this.streamExecutor);
        }

        @Override
        public CompletableFuture<List<FilteredText>> processMessageBundle(List<String> texts) {
            List<CompletableFuture<FilteredText>> list = texts.stream()
                .map(
                    p_10195_ -> TextFilterClient.this.requestMessageProcessing(
                            this.profile, p_10195_, TextFilterClient.this.chatIgnoreStrategy, this.streamExecutor
                        )
                )
                .collect(ImmutableList.toImmutableList());
            return Util.sequenceFailFast(list).exceptionally(p_143747_ -> ImmutableList.of());
        }

        @Override
        public CompletableFuture<FilteredText> processStreamMessage(String text) {
            return TextFilterClient.this.requestMessageProcessing(this.profile, text, TextFilterClient.this.chatIgnoreStrategy, this.streamExecutor);
        }
    }

    public static class RequestFailedException extends RuntimeException {
        RequestFailedException(String message) {
            super(message);
        }
    }
}
