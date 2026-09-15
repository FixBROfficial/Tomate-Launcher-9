package br.com.simplelauncher;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class GameInstaller {
    private final Config config;
    private final Consumer<String> status;

    GameInstaller(Config config) {
        this(config, new Consumer<String>() { public void accept(String ignored) { } });
    }

    GameInstaller(Config config, Consumer<String> status) {
        this.config = config;
        this.status = status;
    }

    Installation prepare() throws Exception {
        status.accept("Preparing Minecraft folder...");
        Path minecraftDir = config.path("minecraft.dir");
        Files.createDirectories(minecraftDir);

        status.accept("Downloading dados da versao...");
        Map<String, Object> vanilla = downloadVanillaVersionJson(minecraftDir);
        status.accept("Downloading perfil Fabric...");
        Map<String, Object> fabric = downloadFabricProfileJson(minecraftDir);

        status.accept("Checking client jar...");
        downloadClientJar(minecraftDir, vanilla);
        status.accept("Checking assets...");
        String assetIndex = downloadAssets(minecraftDir, vanilla);

        Path nativesDir = minecraftDir.resolve("natives").resolve(config.get("minecraft.version"));
        Files.createDirectories(nativesDir);

        Set<Path> classpath = new LinkedHashSet<Path>();
        status.accept("Checking Minecraft libraries...");
        downloadLibraries(minecraftDir, vanilla, classpath, nativesDir);
        status.accept("Checking Fabric libraries...");
        downloadLibraries(minecraftDir, fabric, classpath, nativesDir);
        classpath.add(clientJar(minecraftDir));

        String mainClass = Json.string(fabric, "mainClass");
        if (Java8.isBlank(mainClass)) mainClass = Json.string(vanilla, "mainClass");
        return new Installation(minecraftDir, nativesDir, new ArrayList<Path>(classpath), mainClass, assetIndex, gameArguments(vanilla));
    }

    private Map<String, Object> downloadVanillaVersionJson(Path minecraftDir) throws Exception {
        Map<String, Object> manifest = getJson(config.get("mojang.version.manifest.url"));
        String wantedVersion = config.get("minecraft.version");
        List<Object> versions = Json.array(manifest.get("versions"));
        for (int i = 0; i < versions.size(); i++) {
            Map<String, Object> version = Json.object(versions.get(i));
            if (wantedVersion.equals(Json.string(version, "id"))) {
                Path target = minecraftDir.resolve("versions").resolve(wantedVersion).resolve(wantedVersion + ".json");
                downloadText(Json.string(version, "url"), target);
                return Json.object(Json.parse(Java8.readString(target)));
            }
        }
        throw new IllegalStateException("Minecraft version not found in manifest: " + wantedVersion);
    }

    private Map<String, Object> downloadFabricProfileJson(Path minecraftDir) throws Exception {
        String profile = config.get("fabric.profile");
        Path target = minecraftDir.resolve("versions").resolve(profile).resolve(profile + ".json");
        downloadText(config.get("fabric.profile.url"), target);
        return Json.object(Json.parse(Java8.readString(target)));
    }

    private void downloadClientJar(Path minecraftDir, Map<String, Object> vanilla) throws Exception {
        Map<String, Object> downloads = Json.object(vanilla.get("downloads"));
        Map<String, Object> clientDownload = Json.object(downloads.get("client"));
        downloadFile(Json.string(clientDownload, "url"), clientJar(minecraftDir));
    }

    private String downloadAssets(Path minecraftDir, Map<String, Object> vanilla) throws Exception {
        Map<String, Object> assetIndexInfo = Json.object(vanilla.get("assetIndex"));
        String id = Json.string(assetIndexInfo, "id");
        Path indexPath = minecraftDir.resolve("assets").resolve("indexes").resolve(id + ".json");
        downloadText(Json.string(assetIndexInfo, "url"), indexPath);

        Map<String, Object> index = Json.object(Json.parse(Java8.readString(indexPath)));
        Map<String, Object> objects = Json.object(index.get("objects"));
        for (Object value : objects.values()) {
            Map<String, Object> object = Json.object(value);
            String hash = Json.string(object, "hash");
            String prefix = hash.substring(0, 2);
            Path target = minecraftDir.resolve("assets").resolve("objects").resolve(prefix).resolve(hash);
            downloadFile("https://resources.download.minecraft.net/" + prefix + "/" + hash, target);
        }
        return id;
    }

    private void downloadLibraries(Path minecraftDir, Map<String, Object> versionJson, Set<Path> classpath, Path nativesDir) throws Exception {
        Object rawLibraries = versionJson.get("libraries");
        if (rawLibraries == null) return;
        List<Object> libraries = Json.array(rawLibraries);
        for (int i = 0; i < libraries.size(); i++) {
            Map<String, Object> library = Json.object(libraries.get(i));
            if (!rulesAllow(library)) continue;
            Object downloadsValue = library.get("downloads");
            if (downloadsValue != null) {
                Map<String, Object> downloads = Json.object(downloadsValue);
                Object artifactValue = downloads.get("artifact");
                if (artifactValue != null) {
                    Map<String, Object> artifact = Json.object(artifactValue);
                    Path target = minecraftDir.resolve("libraries").resolve(Json.string(artifact, "path"));
                    downloadFile(Json.string(artifact, "url"), target);
                    classpath.add(target);
                }
                Object natives = library.get("natives");
                Object classifiersValue = downloads.get("classifiers");
                if (natives != null && classifiersValue != null) {
                    String classifierName = Json.string(Json.object(natives), osName()).replace("${arch}", "64");
                    if (!Java8.isBlank(classifierName)) {
                        Object classifierValue = Json.object(classifiersValue).get(classifierName);
                        if (classifierValue != null) {
                            Map<String, Object> classifier = Json.object(classifierValue);
                            Path target = minecraftDir.resolve("libraries").resolve(Json.string(classifier, "path"));
                            downloadFile(Json.string(classifier, "url"), target);
                            extractNatives(target, nativesDir);
                        }
                    }
                }
            } else {
                MavenArtifact artifact = mavenArtifact(Json.string(library, "name"), Json.string(library, "url"));
                Path target = minecraftDir.resolve("libraries").resolve(artifact.path());
                downloadFile(artifact.url(), target);
                classpath.add(target);
            }
        }
    }

    private List<String> gameArguments(Map<String, Object> vanilla) {
        Object arguments = vanilla.get("arguments");
        if (arguments == null) return legacyArguments(vanilla);
        Object game = Json.object(arguments).get("game");
        List<String> result = new ArrayList<String>();
        List<Object> gameArgs = Json.array(game);
        for (int i = 0; i < gameArgs.size(); i++) {
            Object item = gameArgs.get(i);
            if (item instanceof String) {
                result.add((String) item);
            } else {
                Map<String, Object> object = Json.object(item);
                if (rulesAllow(object)) {
                    Object value = object.get("value");
                    if (value instanceof String) {
                        result.add((String) value);
                    } else {
                        List<Object> parts = Json.array(value);
                        for (int p = 0; p < parts.size(); p++) result.add(parts.get(p).toString());
                    }
                }
            }
        }
        return result;
    }

    private List<String> legacyArguments(Map<String, Object> vanilla) {
        String raw = Json.string(vanilla, "minecraftArguments");
        List<String> result = new ArrayList<String>();
        if (Java8.isBlank(raw)) return result;
        String[] split = raw.split(" ");
        for (int i = 0; i < split.length; i++) result.add(split[i]);
        return result;
    }

    private boolean rulesAllow(Map<String, Object> object) {
        Object rulesValue = object.get("rules");
        if (rulesValue == null) return true;
        boolean allowed = false;
        List<Object> rules = Json.array(rulesValue);
        for (int i = 0; i < rules.size(); i++) {
            Map<String, Object> rule = Json.object(rules.get(i));
            Object osValue = rule.get("os");
            Object featuresValue = rule.get("features");
            boolean applies = (osValue == null || osName().equals(Json.string(Json.object(osValue), "name")))
                    && (featuresValue == null || featuresAllow(Json.object(featuresValue)));
            if (applies) allowed = "allow".equals(Json.string(rule, "action"));
        }
        return allowed;
    }

    private boolean featuresAllow(Map<String, Object> features) {
        for (Map.Entry<String, Object> feature : features.entrySet()) {
            boolean wanted = Boolean.TRUE.equals(feature.getValue());
            boolean actual = featureEnabled(feature.getKey());
            if (wanted != actual) return false;
        }
        return true;
    }

    private boolean featureEnabled(String key) {
        if ("is_demo_user".equals(key)) return false;
        if ("has_custom_resolution".equals(key)) return true;
        if ("has_quick_plays_support".equals(key) || "is_quick_play_multiplayer".equals(key)) return !Java8.isBlank(config.get("quickplay.server"));
        if ("is_quick_play_singleplayer".equals(key) || "is_quick_play_realms".equals(key)) return false;
        return false;
    }

    private void extractNatives(Path archive, Path targetDir) throws Exception {
        ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive));
        try {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory() && !entry.getName().startsWith("META-INF/")) {
                    Path target = targetDir.resolve(Paths.get(entry.getName()).getFileName().toString());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
                entry = zip.getNextEntry();
            }
        } finally {
            zip.close();
        }
    }

    private MavenArtifact mavenArtifact(String name, String repository) {
        String[] parts = name.split(":");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid Maven coordinate: " + name);
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String path = group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
        String base = repository.endsWith("/") ? repository : repository + "/";
        return new MavenArtifact(path, base + path);
    }

    private Map<String, Object> getJson(String url) throws Exception {
        return Json.object(Json.parse(Java8.httpGetString(url)));
    }

    private void downloadText(String url, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Java8.writeString(target, Java8.httpGetString(url));
    }

    private void downloadFile(String url, Path target) throws Exception {
        if (Files.exists(target) && Files.size(target) > 0) return;
        status.accept("Downloading " + target.getFileName());
        Java8.httpDownload(url, target);
    }

    private Path clientJar(Path minecraftDir) {
        String version = config.get("minecraft.version");
        return minecraftDir.resolve("versions").resolve(version).resolve(version + ".jar");
    }

    private String osName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    static final class MavenArtifact {
        private final String path;
        private final String url;
        MavenArtifact(String path, String url) { this.path = path; this.url = url; }
        String path() { return path; }
        String url() { return url; }
    }

    static final class Installation {
        private final Path minecraftDir;
        private final Path nativesDir;
        private final List<Path> classpath;
        private final String mainClass;
        private final String assetIndex;
        private final List<String> gameArguments;
        Installation(Path minecraftDir, Path nativesDir, List<Path> classpath, String mainClass, String assetIndex, List<String> gameArguments) {
            this.minecraftDir = minecraftDir;
            this.nativesDir = nativesDir;
            this.classpath = classpath;
            this.mainClass = mainClass;
            this.assetIndex = assetIndex;
            this.gameArguments = gameArguments;
        }
        Path minecraftDir() { return minecraftDir; }
        Path nativesDir() { return nativesDir; }
        List<Path> classpath() { return classpath; }
        String mainClass() { return mainClass; }
        List<String> gameArguments() { return gameArguments; }
        Map<String, String> variables(String nickname, Config config) {
            try {
                Files.createDirectories(minecraftDir.resolve("quickPlay"));
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not create quickPlay folder.", exception);
            }
            Map<String, String> values = new HashMap<String, String>();
            values.put("auth_player_name", nickname);
            values.put("version_name", config.get("fabric.profile"));
            values.put("game_directory", minecraftDir.toString());
            values.put("assets_root", minecraftDir.resolve("assets").toString());
            values.put("assets_index_name", assetIndex);
            values.put("auth_uuid", java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname).getBytes()).toString());
            values.put("auth_access_token", "offline");
            values.put("auth_xuid", "");
            values.put("clientid", "");
            values.put("user_properties", "{}");
            values.put("user_type", "legacy");
            values.put("version_type", "release");
            values.put("resolution_width", String.valueOf(config.getInt("game.width", 854)));
            values.put("resolution_height", String.valueOf(config.getInt("game.height", 480)));
            values.put("quickPlayPath", minecraftDir.resolve("quickPlay").resolve("quickPlay.json").toString());
            values.put("quickPlayMultiplayer", config.get("quickplay.server"));
            return values;
        }
    }
}
