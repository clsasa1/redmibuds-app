package org.kravchuk.redmibuds;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/** Pure-Java HTTP POST (no Android deps) — shared by LogSender and the box JVM test. */
public final class HttpPost {
    private HttpPost() {}

    /** Returns HTTP status code; throws on network errors. */
    public static int post(String url, Map<String, String> headers, byte[] body, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(timeoutMs); c.setReadTimeout(timeoutMs);
            c.setDoOutput(true); c.setRequestMethod("POST");
            c.setFixedLengthStreamingMode(body.length);
            for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            try (OutputStream o = c.getOutputStream()) { o.write(body); }
            int code = c.getResponseCode();
            try (InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream()) {
                if (in != null) { byte[] b = new byte[4096]; while (in.read(b) > 0) { } }
            } catch (Exception ignored) {}
            return code;
        } finally { c.disconnect(); }
    }

    /** ntfy headers must be ASCII/ISO-8859-1. */
    public static String asciiHeader(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch >= 32 && ch < 127 ? ch : '?');
        return b.toString();
    }
}
