package br.com.simplelauncher;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class ModUpdater {
    private final Config config;
    private final Consumer<String> status;

    ModUpdater(Config config) {
        this(config, new Consumer<String>() { public void accept(String ignored) { } });
    }

    ModUpdater(Config config, Consumer<String> status) {
        this.config = config;
        this.status = status;
    }

    Result sync() throws Exception {
        Result prepared = prepareLocalFolders();
        Result mods = syncSource("mods", "mods.source.url", "mods.manifest.url", "mods", true);
        Result configs = syncSource("configs", "configs.source.url", "", "config", false);
        Result resourcepacks = syncSource("resourcepacks", "resourcepacks.source.url", "", "resourcepacks", false);
        Result sodium = syncSodium();
        return prepared.plus(mods).plus(configs).plus(resourcepacks).plus(sodium);
    }

    private Result prepareLocalFolders() throws Exception {
        Path minecraftDir = config.path("minecraft.dir");
        Path modsDir = minecraftDir.resolve("mods");
        Path externalModsDir = minecraftDir.resolve("modsexternos");
        Files.createDirectories(modsDir);
        Files.createDirectories(externalModsDir);

        int moved = moveExternalMods(externalModsDir, modsDir);
        int removedMods = deleteManagedMods(modsDir);
        int removedFancyMenu = deleteFancyMenu(minecraftDir.resolve("config").resolve("fancymenu"));
        return new Result(moved, 0, removedMods + removedFancyMenu);
    }

    private int moveExternalMods(Path externalModsDir, Path modsDir) throws Exception {
        int moved = 0;
        DirectoryStream<Path> stream = Files.newDirectoryStream(externalModsDir, "*.jar");
        try {
            for (Path externalMod : stream) {
                if (!Files.isRegularFile(externalMod)) {
                    continue;
                }
                String name = externalMod.getFileName().toString();
                String targetName = name.startsWith("EXT-") ? name : "EXT-" + name;
                Path target = modsDir.resolve(targetName).normalize();
                if (!target.startsWith(modsDir)) {
                    throw new IllegalStateException("Invalid external mod path: " + name);
                }
                status.accept("Importing external mod: " + name);
                Files.move(externalMod, target, StandardCopyOption.REPLACE_EXISTING);
                moved++;
            }
        } finally {
            stream.close();
        }
        return moved;
    }

    private int deleteManagedMods(Path modsDir) throws Exception {
        return deleteManagedModsInside(modsDir, false);
    }

    private int deleteManagedModsInside(Path dir, boolean removeDirWhenEmpty) throws Exception {
        int removed = 0;
        DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
        try {
            for (Path mod : stream) {
                if (Files.isDirectory(mod)) {
                    removed += deleteManagedModsInside(mod, true);
                    continue;
                }
                String name = mod.getFileName().toString();
                if (Files.isRegularFile(mod) && name.toLowerCase().endsWith(".jar") && !name.startsWith("EXT-")) {
                    status.accept("Removing managed mod: " + name);
                    Files.deleteIfExists(mod);
                    removed++;
                }
            }
        } finally {
            stream.close();
        }
        if (removeDirWhenEmpty && isDirectoryEmpty(dir) && Files.deleteIfExists(dir)) {
            removed++;
        }
        return removed;
    }

    private boolean isDirectoryEmpty(Path dir) throws Exception {
        DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
        try {
            return !stream.iterator().hasNext();
        } finally {
            stream.close();
        }
    }

    private int deleteFancyMenu(Path fancyMenuDir) throws Exception {
        if (!Files.exists(fancyMenuDir)) {
            return 0;
        }
        status.accept("Refreshing FancyMenu config...");
        return deleteDirectory(fancyMenuDir);
    }

    private int deleteDirectory(Path path) throws Exception {
        int removed = 0;
        if (Files.isDirectory(path)) {
            DirectoryStream<Path> stream = Files.newDirectoryStream(path);
            try {
                for (Path child : stream) {
                    removed += deleteDirectory(child);
                }
            } finally {
                stream.close();
            }
        }
        if (Files.deleteIfExists(path)) {
            removed++;
        }
        return removed;
    }
    private Result syncSodium() throws Exception {
        if (config.getBoolean("sodium.enabled", true)) {
            return syncSource("sodium", "sodium.source.url", "", "mods", true);
        }
        return removeSodiumFiles();
    }

    private Result removeSodiumFiles() throws Exception {
        String source = config.get("sodium.source.url");
        if (Java8.isBlank(source)) {
            return new Result(0, 0, 0);
        }
        Path modsDir = config.path("minecraft.dir").resolve("mods");
        Files.createDirectories(modsDir);
        status.accept("Removing Sodium files...");
        List<RemoteFile> remoteFiles = readGitHubFolder(source, true);
        int removed = 0;
        for (int i = 0; i < remoteFiles.size(); i++) {
            RemoteFile file = remoteFiles.get(i);
            Path target = modsDir.resolve(file.path()).normalize();
            if (!target.startsWith(modsDir)) {
                throw new IllegalStateException("Invalid remote path: " + file.path());
            }
            if (Files.deleteIfExists(target)) {
                removed++;
            }
        }
        return new Result(0, 0, removed);
    }
    private Result syncSource(String label, String sourceKey, String manifestKey, String targetFolder, boolean jarOnly) throws Exception {
        Path targetDir = config.path("minecraft.dir").resolve(targetFolder);
        Files.createDirectories(targetDir);

        String source = config.get(sourceKey);
        List<RemoteFile> remoteFiles;
        if (!Java8.isBlank(source)) {
            status.accept("Reading folder: " + label + "...");
            remoteFiles = readGitHubFolder(source, jarOnly);
        } else if (!Java8.isBlank(manifestKey) && !Java8.isBlank(config.get(manifestKey))) {
            status.accept("Reading manifest: " + label + "...");
            remoteFiles = readManifest(config.get(manifestKey));
        } else {
            return new Result(0, 0, 0);
        }

        int downloaded = 0;
        int alreadyOk = 0;
        for (RemoteFile file : remoteFiles) {
            Path target = targetDir.resolve(file.path()).normalize();
            if (!target.startsWith(targetDir)) {
                throw new IllegalStateException("Invalid remote path: " + file.path());
            }
            if (Files.exists(target)) {
                alreadyOk++;
                continue;
            }
            status.accept("Downloading " + label + ": " + file.path());
            Java8.httpDownload(file.url(), target);
            if (!Java8.isBlank(file.sha256())) {
                String actualHash = sha256(target);
                if (!actualHash.equalsIgnoreCase(file.sha256())) {
                    Files.deleteIfExists(target);
                    throw new IllegalStateException("Invalid hash for " + file.path());
                }
            }
            downloaded++;
        }
        return new Result(downloaded, alreadyOk, 0);
    }

    private List<RemoteFile> readGitHubFolder(String url, boolean jarOnly) throws Exception {
        GitHubFolder folder = GitHubFolder.parse(url);
        String apiUrl = "https://api.github.com/repos/" + folder.owner() + "/" + folder.repo() + "/git/trees/" + folder.branch() + "?recursive=1";
        Map<String, Object> body = Json.object(Json.parse(Java8.httpGetString(apiUrl)));
        List<RemoteFile> files = new ArrayList<RemoteFile>();
        String prefix = Java8.isBlank(folder.path()) ? "" : folder.path() + "/";
        List<Object> tree = Json.array(body.get("tree"));
        for (int i = 0; i < tree.size(); i++) {
            Map<String, Object> node = Json.object(tree.get(i));
            String type = Json.string(node, "type");
            String path = Json.string(node, "path");
            if (!"blob".equals(type) || !path.startsWith(prefix)) continue;
            String relativePath = path.substring(prefix.length());
            if (Java8.isBlank(relativePath) || (jarOnly && !relativePath.endsWith(".jar"))) continue;
            String raw = "https://raw.githubusercontent.com/" + folder.owner() + "/" + folder.repo() + "/" + folder.branch() + "/" + path;
            files.add(new RemoteFile(relativePath, Json.string(node, "sha"), "", raw));
        }
        return files;
    }

    private List<RemoteFile> readManifest(String url) throws Exception {
        String body = Java8.httpGetString(url);
        List<RemoteFile> files = new ArrayList<RemoteFile>();
        String[] lines = body.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String clean = lines[i].trim();
            if (clean.isEmpty() || clean.startsWith("#")) continue;
            String[] parts = clean.split("\\|", 3);
            if (parts.length != 3) throw new IllegalArgumentException("Invalid manifest line: " + lines[i]);
            files.add(new RemoteFile(parts[0].trim(), parts[1].trim(), parts[1].trim(), parts[2].trim()));
        }
        return files;
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = Files.newInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        } finally {
            in.close();
        }
        return toHex(digest.digest());
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            result.append(String.format("%02x", bytes[i] & 0xff));
        }
        return result.toString();
    }

    static final class RemoteFile {
        private final String path;
        private final String fingerprint;
        private final String sha256;
        private final String url;
        RemoteFile(String path, String fingerprint, String sha256, String url) {
            this.path = path;
            this.fingerprint = fingerprint;
            this.sha256 = sha256;
            this.url = url;
        }
        String path() { return path; }
        String fingerprint() { return fingerprint; }
        String sha256() { return sha256; }
        String url() { return url; }
    }

    static final class GitHubFolder {
        private final String owner;
        private final String repo;
        private final String branch;
        private final String path;
        GitHubFolder(String owner, String repo, String branch, String path) {
            this.owner = owner;
            this.repo = repo;
            this.branch = branch;
            this.path = path;
        }
        String owner() { return owner; }
        String repo() { return repo; }
        String branch() { return branch; }
        String path() { return path; }
        static GitHubFolder parse(String url) {
            URI uri = URI.create(url);
            String[] parts = uri.getPath().replaceFirst("^/", "").split("/");
            if (parts.length < 5 || !"github.com".equalsIgnoreCase(uri.getHost()) || !"tree".equals(parts[2])) {
                throw new IllegalArgumentException("Use a GitHub folder link, like https://github.com/user/repo/tree/main/mods");
            }
            StringBuilder folderPath = new StringBuilder();
            for (int i = 4; i < parts.length; i++) {
                if (folderPath.length() > 0) folderPath.append('/');
                folderPath.append(parts[i]);
            }
            return new GitHubFolder(parts[0], parts[1], parts[3], folderPath.toString());
        }
    }

    static final class Result {
        private final int downloaded;
        private final int alreadyOk;
        private final int removed;
        Result(int downloaded, int alreadyOk, int removed) {
            this.downloaded = downloaded;
            this.alreadyOk = alreadyOk;
            this.removed = removed;
        }
        Result plus(Result other) {
            return new Result(downloaded + other.downloaded, alreadyOk + other.alreadyOk, removed + other.removed);
        }
        String message() {
            return "Updates finished.\nDownloaded: " + downloaded + "\nAlready present: " + alreadyOk + "\nRemoved: " + removed;
        }
    }
}
