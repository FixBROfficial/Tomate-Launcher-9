package br.com.simplelauncher;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.FlowLayout;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LauncherApp {
    private static final Color TOMATE_PINK = Color.decode("#c85398");
    private static final Color TOMATE_LIGHT_PINK = Color.decode("#f8d8ea");
    private final Path root = Paths.get("").toAbsolutePath();
    private final Config config;
    private final AccountStore accounts;
    private final JFrame frame = new JFrame("Tomate Launcher");
    private final JTextArea changelog = new JTextArea();
    private final JComboBox<String> accountBox = new JComboBox<>();
    private final JButton playButton = new JButton("PLAY");
    private final JButton updateButton = new JButton("Check updates");
    private final JButton addAccountButton = new JButton("Offline account");
    private final JButton removeAccountButton = new JButton("Delete");
    private final JButton settingsButton = new JButton("Settings");
    private final JButton openFolderButton = new JButton("Open .paradise");
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel logoLabel = new JLabel("Tomate Launcher", JLabel.CENTER);

    private LauncherApp() throws Exception {
        config = Config.load();
        accounts = new AccountStore(Config.appDataDir().resolve("TomateLauncher"));
        accounts.load();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                LauncherApp app = new LauncherApp();
                app.start();
            } catch (Exception exception) {
                showError("Could not start the launcher.", exception);
            }
        });
    }

    private void start() throws Exception {
        if (Java8.isBlank(accounts.selected())) {
            askFirstNickname();
        }
        buildHome();
        loadChangelog();
        frame.setVisible(true);
    }

    private void askFirstNickname() throws Exception {
        JTextField field = new JTextField();
        field.setPreferredSize(new Dimension(240, 28));
        int result = JOptionPane.showConfirmDialog(
                null,
                field,
                "Enter your offline nickname",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION || Java8.isBlank(field.getText())) {
            throw new IllegalStateException("Nickname is required.");
        }
        accounts.addAndSelect(cleanNickname(field.getText()));
    }

    private void buildHome() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 560));
        frame.setLocationRelativeTo(null);

        changelog.setEditable(false);
        changelog.setLineWrap(true);
        changelog.setWrapStyleWord(true);
        changelog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        changelog.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        changelog.setBackground(Color.WHITE);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBackground(TOMATE_LIGHT_PINK);
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(topPanel(), BorderLayout.NORTH);
        content.add(new JScrollPane(changelog), BorderLayout.CENTER);
        content.add(bottomBar(), BorderLayout.SOUTH);

        frame.setContentPane(content);
        loadLogo();
    }

    private JPanel topPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(TOMATE_LIGHT_PINK);
        logoLabel.setPreferredSize(new Dimension(320, 104));
        logoLabel.setHorizontalAlignment(JLabel.CENTER);
        logoLabel.setFont(logoLabel.getFont().deriveFont(Font.BOLD, 22f));
        panel.add(logoLabel, BorderLayout.NORTH);
        JLabel title = new JLabel("Changelog", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel bottomBar() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(TOMATE_PINK);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints left = constraints(0, 0, 0.25, GridBagConstraints.WEST);
        GridBagConstraints center = constraints(1, 0, 0.5, GridBagConstraints.CENTER);
        GridBagConstraints right = constraints(2, 0, 0.25, GridBagConstraints.EAST);

        updateButton.addActionListener(event -> checkUpdates());
        settingsButton.addActionListener(event -> openSettings());
        openFolderButton.addActionListener(event -> openMinecraftFolder());

        playButton.setPreferredSize(new Dimension(220, 54));
        playButton.setFont(playButton.getFont().deriveFont(Font.BOLD, 22f));
        playButton.addActionListener(event -> play());

        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        statusLabel.setVisible(false);

        refreshAccounts();
        accountBox.addActionListener(event -> {
            Object selected = accountBox.getSelectedItem();
            if (selected != null) {
                try {
                    accounts.select(selected.toString());
                } catch (Exception exception) {
                    showError("Could not select the account.", exception);
                }
            }
        });
        addAccountButton.addActionListener(event -> addAccount());
        removeAccountButton.addActionListener(event -> removeSelectedAccount());

        JPanel accountPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        accountPanel.setBackground(TOMATE_PINK);
        accountPanel.add(accountBox);
        accountPanel.add(addAccountButton);
        accountPanel.add(removeAccountButton);

        JPanel playPanel = new JPanel(new BorderLayout(4, 4));
        playPanel.setBackground(TOMATE_PINK);
        playPanel.add(playButton, BorderLayout.CENTER);
        playPanel.add(statusPanel(), BorderLayout.SOUTH);

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        leftPanel.setBackground(TOMATE_PINK);
        leftPanel.add(updateButton);
        leftPanel.add(openFolderButton);
        leftPanel.add(settingsButton);

        panel.add(leftPanel, left);
        panel.add(playPanel, center);
        panel.add(accountPanel, right);
        return panel;
    }

    private JPanel statusPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(TOMATE_PINK);
        statusLabel.setForeground(Color.WHITE);
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private void loadLogo() {
        Java8.startThread(() -> {
            try {
                Image image = ImageIO.read(URI.create(Java8.rawGitHubUrl(config.get("logo.url"))).toURL());
                Image scaled = image.getScaledInstance(320, 104, Image.SCALE_SMOOTH);
                SwingUtilities.invokeLater(() -> {
                    logoLabel.setText("");
                    logoLabel.setIcon(new ImageIcon(scaled));
                });
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> logoLabel.setText("Tomate Launcher"));
            }
        });
    }

    private GridBagConstraints constraints(int x, int y, double weight, int anchor) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.weightx = weight;
        c.insets = new Insets(0, 4, 0, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = anchor;
        return c;
    }

    private void refreshAccounts() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String account : accounts.accounts()) {
            model.addElement(account);
        }
        accountBox.setModel(model);
        if (!Java8.isBlank(accounts.selected())) {
            accountBox.setSelectedItem(accounts.selected());
        }
        removeAccountButton.setEnabled(model.getSize() > 0);
    }

    private void addAccount() {
        String nickname = JOptionPane.showInputDialog(frame, "Offline nickname:");
        if (nickname == null || Java8.isBlank(nickname)) {
            return;
        }
        try {
            accounts.addAndSelect(cleanNickname(nickname));
            refreshAccounts();
        } catch (Exception exception) {
            showError("Could not add the account.", exception);
        }
    }

    private void removeSelectedAccount() {
        Object selected = accountBox.getSelectedItem();
        if (selected == null) {
            return;
        }
        String nickname = selected.toString();
        int result = JOptionPane.showConfirmDialog(
                frame,
                "Delete offline account \"" + nickname + "\"?",
                "Delete account",
                JOptionPane.YES_NO_OPTION
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            accounts.remove(nickname);
            if (Java8.isBlank(accounts.selected()) && accounts.accounts().isEmpty()) {
                askFirstNickname();
            }
            refreshAccounts();
        } catch (Exception exception) {
            showError("Could not delete the account.", exception);
        }
    }

    private void loadChangelog() {
        changelog.setText("Loading changelog...");
        changelog.setCaretPosition(0);
        Java8.startThread(() -> {
            try {
                String body = Java8.httpGetString(Java8.rawGitHubUrl(config.get("changelog.url")));
                SwingUtilities.invokeLater(() -> {
                    changelog.setText(body);
                    changelog.setCaretPosition(0);
                    SwingUtilities.invokeLater(() -> changelog.setCaretPosition(0));
                });
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> {
                    changelog.setText("Could not load the changelog.\n\n" + exception.getMessage());
                    changelog.setCaretPosition(0);
                });
            }
        });
    }

    private String rawGitHubUrl(String url) {
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

    private void checkUpdates() {
        updateButton.setEnabled(false);
        updateButton.setText("Updating...");
        Java8.startThread(() -> {
            try {
                ModUpdater updater = new ModUpdater(config);
                ModUpdater.Result result = updater.sync();
                InitialFileInstaller.Result initialFiles = new InitialFileInstaller(config).installMissing();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, result.message() + "\n\n" + initialFiles.message()));
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> showError("Failed to update files.", exception));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    updateButton.setEnabled(true);
                    updateButton.setText("Check updates");
                });
            }
        });
    }

    private void openSettings() {
        JTextField javaPathField = new JTextField(config.get("java.path"), 28);
        JTextField javaArgsField = new JTextField(config.get("java.args"), 28);
        JTextField minecraftDirField = new JTextField(config.get("minecraft.dir"), 28);
        JCheckBox sodiumBox = new JCheckBox("Sodium", config.getBoolean("sodium.enabled", true));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(new JLabel("Java Path:"), settingsConstraints(0, 0));
        panel.add(javaPathField, settingsConstraints(1, 0));
        panel.add(new JLabel("Java arguments:"), settingsConstraints(0, 1));
        panel.add(javaArgsField, settingsConstraints(1, 1));
        panel.add(new JLabel("Minecraft folder:"), settingsConstraints(0, 2));
        panel.add(minecraftDirField, settingsConstraints(1, 2));
        panel.add(new JLabel("Optional mods:"), settingsConstraints(0, 3));
        panel.add(sodiumBox, settingsConstraints(1, 3));

        int result = JOptionPane.showConfirmDialog(frame, panel, "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            config.set("java.path", javaPathField.getText());
            config.set("java.args", javaArgsField.getText());
            config.set("minecraft.dir", minecraftDirField.getText());
            config.set("sodium.enabled", String.valueOf(sodiumBox.isSelected()));
            config.save();
            JOptionPane.showMessageDialog(frame, "Settings saved at:\n" + config.settingsFile());
        } catch (Exception exception) {
            showError("Could not save settings.", exception);
        }
    }

    private GridBagConstraints settingsConstraints(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = x == 1 ? 1 : 0;
        return c;
    }

    private void openMinecraftFolder() {
        try {
            Path folder = config.path("minecraft.dir");
            java.nio.file.Files.createDirectories(folder);
            Desktop.getDesktop().open(folder.toFile());
        } catch (Exception exception) {
            showError("Could not open folder.", exception);
        }
    }

    private void play() {
        Object selected = accountBox.getSelectedItem();
        if (selected == null || Java8.isBlank(selected.toString())) {
            addAccount();
            return;
        }
        setBusy(true, "Preparing...");
        Java8.startThread(() -> {
            try {
                new MinecraftLauncher(config, this::setStatus).launch(selected.toString());
                SwingUtilities.invokeLater(() -> frame.setState(JFrame.ICONIFIED));
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> showError("Could not open Minecraft.", exception));
            } finally {
                SwingUtilities.invokeLater(() -> setBusy(false, " "));
            }
        });
    }

    private void setBusy(boolean busy, String status) {
        playButton.setEnabled(!busy);
        updateButton.setEnabled(!busy);
        addAccountButton.setEnabled(!busy);
        removeAccountButton.setEnabled(!busy && accountBox.getItemCount() > 0);
        settingsButton.setEnabled(!busy);
        openFolderButton.setEnabled(!busy);
        accountBox.setEnabled(!busy);
        progressBar.setVisible(busy);
        statusLabel.setVisible(busy);
        setStatus(status);
    }

    private void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    private static String cleanNickname(String nickname) {
        String cleaned = nickname.trim().replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.length() < 3 || cleaned.length() > 16) {
            throw new IllegalArgumentException("Use a nickname from 3 to 16 characters with letters, numbers, or underscore.");
        }
        return cleaned;
    }

    private static void showError(String message, Exception exception) {
        JOptionPane.showMessageDialog(null, message + "\n\n" + exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}
