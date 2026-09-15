package br.com.simplelauncher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

final class AccountStore {
    private final Path file;
    private final Properties properties = new Properties();

    AccountStore(Path dataDir) {
        this.file = dataDir.resolve("accounts.properties");
    }

    void load() throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) {
            InputStream in = Files.newInputStream(file);
            try {
                properties.load(in);
            } finally {
                in.close();
            }
        }
    }

    void save() throws IOException {
        Files.createDirectories(file.getParent());
        OutputStream out = Files.newOutputStream(file);
        try {
            properties.store(out, "Offline accounts");
        } finally {
            out.close();
        }
    }

    List<String> accounts() {
        String raw = properties.getProperty("accounts", "").trim();
        List<String> result = new ArrayList<String>();
        if (!raw.isEmpty()) {
            String[] split = raw.split(",");
            for (int i = 0; i < split.length; i++) {
                String cleaned = split[i].trim();
                if (!cleaned.isEmpty()) {
                    result.add(cleaned);
                }
            }
        }
        return result;
    }

    String selected() {
        return properties.getProperty("selected", "").trim();
    }

    void addAndSelect(String nickname) throws IOException {
        List<String> accounts = accounts();
        if (!accounts.contains(nickname)) {
            accounts.add(nickname);
        }
        properties.setProperty("accounts", join(accounts));
        properties.setProperty("selected", nickname);
        save();
    }

    void select(String nickname) throws IOException {
        properties.setProperty("selected", nickname);
        save();
    }

    void remove(String nickname) throws IOException {
        List<String> accounts = accounts();
        accounts.remove(nickname);
        properties.setProperty("accounts", join(accounts));
        properties.setProperty("selected", accounts.isEmpty() ? "" : accounts.get(0));
        save();
    }

    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) result.append(',');
            result.append(values.get(i));
        }
        return result.toString();
    }
}
