package com.mojang.realmsclient.client;

import com.mojang.realmsclient.exception.RealmsHttpException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class Request<T extends Request<T>> {
    protected HttpURLConnection connection;
    private boolean connected;
    protected String url;
    private static final int DEFAULT_READ_TIMEOUT = 60000;
    private static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    private static final String IS_SNAPSHOT_KEY = "Is-Prerelease";
    private static final String COOKIE_KEY = "Cookie";

    public Request(String url, int connectTimeout, int readTimeout) {
        try {
            this.url = url;
            Proxy proxy = RealmsClientConfig.getProxy();
            if (proxy != null) {
                this.connection = (HttpURLConnection)new URL(url).openConnection(proxy);
            } else {
                this.connection = (HttpURLConnection)new URL(url).openConnection();
            }

            this.connection.setConnectTimeout(connectTimeout);
            this.connection.setReadTimeout(readTimeout);
        } catch (MalformedURLException malformedurlexception) {
            throw new RealmsHttpException(malformedurlexception.getMessage(), malformedurlexception);
        } catch (IOException ioexception) {
            throw new RealmsHttpException(ioexception.getMessage(), ioexception);
        }
    }

    public void cookie(String key, String value) {
        cookie(this.connection, key, value);
    }

    public static void cookie(HttpURLConnection connection, String key, String value) {
        String s = connection.getRequestProperty("Cookie");
        if (s == null) {
            connection.setRequestProperty("Cookie", key + "=" + value);
        } else {
            connection.setRequestProperty("Cookie", s + ";" + key + "=" + value);
        }
    }

    public void addSnapshotHeader(boolean isSnapshot) {
        this.connection.addRequestProperty("Is-Prerelease", String.valueOf(isSnapshot));
    }

    public int getRetryAfterHeader() {
        return getRetryAfterHeader(this.connection);
    }

    public static int getRetryAfterHeader(HttpURLConnection connection) {
        String s = connection.getHeaderField("Retry-After");

        try {
            return Integer.valueOf(s);
        } catch (Exception exception) {
            return 5;
        }
    }

    public int responseCode() {
        try {
            this.connect();
            return this.connection.getResponseCode();
        } catch (Exception exception) {
            throw new RealmsHttpException(exception.getMessage(), exception);
        }
    }

    public String text() {
        try {
            this.connect();
            String s;
            if (this.responseCode() >= 400) {
                s = this.read(this.connection.getErrorStream());
            } else {
                s = this.read(this.connection.getInputStream());
            }

            this.dispose();
            return s;
        } catch (IOException ioexception) {
            throw new RealmsHttpException(ioexception.getMessage(), ioexception);
        }
    }

    private String read(@Nullable InputStream in) throws IOException {
        if (in == null) {
            return "";
        } else {
            InputStreamReader inputstreamreader = new InputStreamReader(in, StandardCharsets.UTF_8);
            StringBuilder stringbuilder = new StringBuilder();

            for (int i = inputstreamreader.read(); i != -1; i = inputstreamreader.read()) {
                stringbuilder.append((char)i);
            }

            return stringbuilder.toString();
        }
    }

    private void dispose() {
        byte[] abyte = new byte[1024];

        try {
            InputStream inputstream = this.connection.getInputStream();

            while (inputstream.read(abyte) > 0) {
            }

            inputstream.close();
            return;
        } catch (Exception exception) {
            try {
                InputStream inputstream1 = this.connection.getErrorStream();
                if (inputstream1 != null) {
                    while (inputstream1.read(abyte) > 0) {
                    }

                    inputstream1.close();
                    return;
                }
            } catch (IOException ioexception) {
                return;
            }
        } finally {
            if (this.connection != null) {
                this.connection.disconnect();
            }
        }
    }

    protected T connect() {
        if (this.connected) {
            return (T)this;
        } else {
            T t = this.doConnect();
            this.connected = true;
            return t;
        }
    }

    protected abstract T doConnect();

    public static Request<?> get(String url) {
        return new Request.Get(url, 5000, 60000);
    }

    public static Request<?> get(String url, int connectTimeout, int readTimeout) {
        return new Request.Get(url, connectTimeout, readTimeout);
    }

    public static Request<?> post(String url, String content) {
        return new Request.Post(url, content, 5000, 60000);
    }

    public static Request<?> post(String url, String content, int connectTimeout, int readTimeout) {
        return new Request.Post(url, content, connectTimeout, readTimeout);
    }

    public static Request<?> delete(String url) {
        return new Request.Delete(url, 5000, 60000);
    }

    public static Request<?> put(String url, String content) {
        return new Request.Put(url, content, 5000, 60000);
    }

    public static Request<?> put(String url, String content, int connectTimeout, int readTimeout) {
        return new Request.Put(url, content, connectTimeout, readTimeout);
    }

    public String getHeader(String name) {
        return getHeader(this.connection, name);
    }

    public static String getHeader(HttpURLConnection connection, String name) {
        try {
            return connection.getHeaderField(name);
        } catch (Exception exception) {
            return "";
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Delete extends Request<Request.Delete> {
        public Delete(String p_87359_, int p_87360_, int p_87361_) {
            super(p_87359_, p_87360_, p_87361_);
        }

        public Request.Delete doConnect() {
            try {
                this.connection.setDoOutput(true);
                this.connection.setRequestMethod("DELETE");
                this.connection.connect();
                return this;
            } catch (Exception exception) {
                throw new RealmsHttpException(exception.getMessage(), exception);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Get extends Request<Request.Get> {
        public Get(String p_87365_, int p_87366_, int p_87367_) {
            super(p_87365_, p_87366_, p_87367_);
        }

        public Request.Get doConnect() {
            try {
                this.connection.setDoInput(true);
                this.connection.setDoOutput(true);
                this.connection.setUseCaches(false);
                this.connection.setRequestMethod("GET");
                return this;
            } catch (Exception exception) {
                throw new RealmsHttpException(exception.getMessage(), exception);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Post extends Request<Request.Post> {
        private final String content;

        public Post(String url, String content, int connectTimeout, int readTimeout) {
            super(url, connectTimeout, readTimeout);
            this.content = content;
        }

        public Request.Post doConnect() {
            try {
                if (this.content != null) {
                    this.connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                }

                this.connection.setDoInput(true);
                this.connection.setDoOutput(true);
                this.connection.setUseCaches(false);
                this.connection.setRequestMethod("POST");
                OutputStream outputstream = this.connection.getOutputStream();
                OutputStreamWriter outputstreamwriter = new OutputStreamWriter(outputstream, "UTF-8");
                outputstreamwriter.write(this.content);
                outputstreamwriter.close();
                outputstream.flush();
                return this;
            } catch (Exception exception) {
                throw new RealmsHttpException(exception.getMessage(), exception);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Put extends Request<Request.Put> {
        private final String content;

        public Put(String url, String content, int connectTimeout, int readTimeout) {
            super(url, connectTimeout, readTimeout);
            this.content = content;
        }

        public Request.Put doConnect() {
            try {
                if (this.content != null) {
                    this.connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                }

                this.connection.setDoOutput(true);
                this.connection.setDoInput(true);
                this.connection.setRequestMethod("PUT");
                OutputStream outputstream = this.connection.getOutputStream();
                OutputStreamWriter outputstreamwriter = new OutputStreamWriter(outputstream, "UTF-8");
                outputstreamwriter.write(this.content);
                outputstreamwriter.close();
                outputstream.flush();
                return this;
            } catch (Exception exception) {
                throw new RealmsHttpException(exception.getMessage(), exception);
            }
        }
    }
}
