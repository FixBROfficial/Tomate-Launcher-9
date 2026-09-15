package br.com.simplelauncher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class InitialFileInstaller {
    private final Config config;
    private final Consumer<String> status;

    InitialFileInstaller(Config config) {
        this(config, new Consumer<String>() { public void accept(String ignored) { } });
    }

    InitialFileInstaller(Config config, Consumer<String> status) {
        this.config = config;
        this.status = status;
    }

    Result installMissing() throws Exception {
        Path minecraftDir = config.path("minecraft.dir");
        Files.createDirectories(minecraftDir);
        List<String> installed = new ArrayList<String>();
        List<String> skipped = new ArrayList<String>();
        installIfMissing("options.txt", config.get("initial.options.url"), minecraftDir, installed, skipped);
        installIfMissing("servers.dat", config.get("initial.servers.url"), minecraftDir, installed, skipped);
        return new Result(installed, skipped);
    }

    private void installIfMissing(String filename, String url, Path minecraftDir, List<String> installed, List<String> skipped) throws Exception {
        if (Java8.isBlank(url)) return;
        Path target = minecraftDir.resolve(filename);
        if (Files.exists(target)) {
            skipped.add(filename);
            return;
        }
        status.accept("Downloading arquivo inicial: " + filename);
        Java8.httpDownload(Java8.rawGitHubUrl(url), target);
        installed.add(filename);
    }

    static final class Result {
        private final List<String> installed;
        private final List<String> skipped;
        Result(List<String> installed, List<String> skipped) {
            this.installed = installed;
            this.skipped = skipped;
        }
        String message() {
            return "Initial files downloaded: " + installed.size() + "\nKept because they already exist: " + skipped.size();
        }
    }
}
