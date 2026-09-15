package br.com.simplelauncher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class Java8 {
    private Java8() {
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static void startThread(final Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.start();
    }

    static String readString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    static void writeString(Path path, String value) throws IOException {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }

    static String httpGetString(String url) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpGetTo(url, out);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static void httpDownload(String url, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        OutputStream out = Files.newOutputStream(target);
        try {
            httpGetTo(url, out);
        } finally {
            out.close();
        }
    }

    private static void httpGetTo(String url, OutputStream out) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url.replace(" ", "%20")).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("User-Agent", "TomateLauncher-Java8");
        int status = connection.getResponseCode();
        if (status >= 400) {
            throw new IOException("HTTP " + status + " em " + url);
        }
        InputStream in = connection.getInputStream();
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
            connection.disconnect();
        }
    }

    static String rawGitHubUrl(String url) {
        URI uri = URI.create(url);
        if (!"github.com".equalsIgnoreCase(uri.getHost()) || !uri.getPath().contains("/blob/")) {
            return url;
        }
        String[] parts = uri.getPath().replaceFirst("^/", "").split("/", 5);
        if (parts.length < 5) {
            return url;
        }
        return "https://raw.githubusercontent.com/" + parts[0] + "/" + parts[1] + "/" + parts[3] + "/" + parts[4];
    }
}
