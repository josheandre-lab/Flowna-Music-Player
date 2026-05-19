package com.flowna.musicplayer;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class NewPipeDownloader extends Downloader {
    private static volatile NewPipeDownloader instance;

    private NewPipeDownloader() {
    }

    static NewPipeDownloader getInstance() {
        if (instance == null) {
            synchronized (NewPipeDownloader.class) {
                if (instance == null) instance = new NewPipeDownloader();
            }
        }
        return instance;
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();
        try {
            connection.setRequestMethod(request.httpMethod());
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(true);

            Map<String, List<String>> headers = request.headers();
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                for (String value : entry.getValue()) {
                    connection.addRequestProperty(entry.getKey(), value);
                }
            }
            if (!headers.containsKey("User-Agent")) {
                connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
                );
            }

            byte[] data = request.dataToSend();
            if (data != null) {
                connection.setDoOutput(true);
                connection.getOutputStream().write(data);
            }

            int code = connection.getResponseCode();
            if (code == 429) {
                throw new ReCaptchaException("Rate limited", request.url());
            }

            String body = "";
            try {
                InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) {
                    byte[] buffer = new byte[8192];
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    body = out.toString("UTF-8");
                }
            } catch (Exception ignored) {
            }

            Map<String, List<String>> rawHeaders = connection.getHeaderFields();
            Map<String, List<String>> responseHeaders = rawHeaders == null
                    ? Collections.emptyMap()
                    : new HashMap<>(rawHeaders);
            responseHeaders.remove(null);
            return new Response(
                    code,
                    connection.getResponseMessage() == null ? "" : connection.getResponseMessage(),
                    responseHeaders,
                    body,
                    connection.getURL() == null ? request.url() : connection.getURL().toString()
            );
        } finally {
            connection.disconnect();
        }
    }
}
