package br.com.simplelauncher;

import java.nio.file.Path;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class MinecraftLauncher {
    private final Config config;
    private final Consumer<String> status;

    MinecraftLauncher(Config config) {
        this(config, ignored -> {
        });
    }

    MinecraftLauncher(Config config, Consumer<String> status) {
        this.config = config;
        this.status = status;
    }

    Process launch(String nickname) throws Exception {
        GameInstaller.Installation installation = new GameInstaller(config, status).prepare();
        status.accept("Checking initial files...");
        new InitialFileInstaller(config, status).installMissing();
        status.accept("Checking mods...");
        new ModUpdater(config, status).sync();
        Path minecraftDir = installation.minecraftDir();

        status.accept("Opening Minecraft...");
        List<String> command = new ArrayList<>();
        command.add(config.get("java.path"));
        command.addAll(splitArguments(config.get("java.args")));
        command.add("-Djava.library.path=" + installation.nativesDir());
        command.add("-cp");
        command.add(installation.classpath().stream().map(Path::toString).collect(Collectors.joining(System.getProperty("path.separator"))));
        command.add(installation.mainClass());

        Map<String, String> variables = installation.variables(nickname, config);
        for (String argument : installation.gameArguments()) {
            command.add(replaceVariables(argument, variables));
        }
        if (command.contains("--demo")) {
            throw new IllegalStateException("The command tried to start demo mode. The launcher blocked it.");
        }

        Process process = new ProcessBuilder(command)
                .directory(minecraftDir.toFile())
                .start();

        GameLogWindow logWindow = new GameLogWindow();
        logWindow.show();
        logWindow.append("Minecraft started.");
        logWindow.append("Directory: " + minecraftDir);
        logWindow.append("Command: " + String.join(" ", command));
        readLog(process.getInputStream(), logWindow);
        readLog(process.getErrorStream(), logWindow);
        Java8.startThread(() -> {
            try {
                int exitCode = process.waitFor();
                logWindow.append("Minecraft exited with code: " + exitCode);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                logWindow.append("Process reading interrupted.");
            }
        });
        return process;
    }

    private void readLog(InputStream stream, GameLogWindow logWindow) {
        Java8.startThread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logWindow.append(line);
                }
            } catch (Exception exception) {
                logWindow.append("Errorr reading log: " + exception.getMessage());
            }
        });
    }

    private String replaceVariables(String text, Map<String, String> variables) {
        String result = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result.replaceAll("\\$\\{[^}]+}", "");
    }

    private List<String> splitArguments(String raw) {
        List<String> result = new ArrayList<>();
        if (Java8.isBlank(raw)) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '"' || c == '\'') && (!quoted || quote == c)) {
                quoted = !quoted;
                quote = quoted ? c : 0;
                continue;
            }
            if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }
}
