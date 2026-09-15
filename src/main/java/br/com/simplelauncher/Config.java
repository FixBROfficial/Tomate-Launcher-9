package br.com.simplelauncher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

final class Config {
    private final Properties values = new Properties();
    private final Path file;

    private Config(Path file) {
        this.file = file;
        defaults();
    }

    static Config load() throws IOException {
        Path file = appDataDir().resolve("TomateLauncher").resolve("launcher.properties");
        Config config = new Config(file);
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) {
            InputStream in = Files.newInputStream(file);
            try {
                config.values.load(in);
            } finally {
                in.close();
            }
        }
        config.save();
        return config;
    }

    static Config load(Path ignored) throws IOException {
        return load();
    }

    String get(String key) {
        return expand(values.getProperty(key, ""));
    }

    void set(String key, String value) {
        values.setProperty(key, value == null ? "" : value.trim());
    }

    void save() throws IOException {
        Files.createDirectories(file.getParent());
        OutputStream out = Files.newOutputStream(file);
        try {
            values.store(out, "Tomate Launcher settings");
        } finally {
            out.close();
        }
    }

    boolean getBoolean(String key, boolean fallback) {
        String value = values.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    int getInt(String key, int fallback) {
        String value = values.getProperty(key);
        if (Java8.isBlank(value)) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }

    Path path(String key) {
        return Paths.get(get(key)).toAbsolutePath().normalize();
    }

    Path settingsFile() {
        return file;
    }

    static Path appDataDir() {
        String appData = System.getenv("APPDATA");
        if (!Java8.isBlank(appData)) {
            return Paths.get(appData);
        }
        return Paths.get(System.getProperty("user.home"));
    }

    private void defaults() {
        values.setProperty("changelog.url", "https://raw.githubusercontent.com/FixBROfficial/testmods/main/changelog.txt");
        values.setProperty("logo.url", "https://github.com/FixBROfficial/testmods/blob/main/TOMATELOGO.png");
        values.setProperty("mods.source.url", "https://github.com/FixBROfficial/testmods/tree/main/mods");
        values.setProperty("configs.source.url", "https://github.com/FixBROfficial/testmods/tree/main/config");
        values.setProperty("resourcepacks.source.url", "https://github.com/FixBROfficial/testmods/tree/main/resourcepacks");
        values.setProperty("sodium.source.url", "https://github.com/FixBROfficial/testmods/tree/main/sodiums");
        values.setProperty("sodium.enabled", "true");
        values.setProperty("initial.options.url", "https://github.com/FixBROfficial/testmods/blob/main/options.txt");
        values.setProperty("initial.servers.url", "https://github.com/FixBROfficial/testmods/blob/main/servers.dat");
        values.setProperty("minecraft.dir", "${appdata}/.paradise");
        values.setProperty("java.path", "java");
        values.setProperty("java.args", "");
        values.setProperty("minecraft.version", "1.21.1");
        values.setProperty("fabric.loader.version", "0.19.3");
        values.setProperty("fabric.profile", "fabric-loader-0.19.3-1.21.1");
        values.setProperty("mojang.version.manifest.url", "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        values.setProperty("fabric.profile.url", "https://meta.fabricmc.net/v2/versions/loader/${minecraft.version}/${fabric.loader.version}/profile/json");
        values.setProperty("game.width", "854");
        values.setProperty("game.height", "480");
        values.setProperty("quickplay.server", "paradisecolorsrp.play.hosting");
        values.setProperty("remove.unknown.mods", "false");
        values.setProperty("remove.unknown.configs", "false");
        values.setProperty("remove.unknown.resourcepacks", "false");
    }

    private String expand(String value) {
        String expanded = value
                .replace("${user.home}", System.getProperty("user.home"))
                .replace("${appdata}", appDataDir().toString().replace("\\", "/"));
        for (String key : values.stringPropertyNames()) {
            expanded = expanded.replace("${" + key + "}", values.getProperty(key));
        }
        return expanded;
    }
}
