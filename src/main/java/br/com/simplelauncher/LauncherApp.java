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
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.FlowLayout;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LauncherApp {
    private static final Color TOMATE_PINK = Color.decode("#c85398");
    private static final Color TOMATE_LIGHT_PINK = Color.decode("#f8d8ea");
    private static final Color TOMATE_SOFT_PINK = Color.decode("#df8ebd");
    private final Path root = Paths.get("").toAbsolutePath();
    private final Config config;
    private final AccountStore accounts;
    private final JFrame frame = new JFrame("Tomate Launcher");
    private final JEditorPane changelog = new JEditorPane();
    private final JScrollPane changelogScrollPane = new JScrollPane(changelog);
    private final JPanel changelogTitleWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    private final JComboBox<String> accountBox = new JComboBox<>();
    private final JButton playButton = new JButton("PLAY");
    private Color defaultPlayButtonForeground;
    private volatile Process gameProcess;
    private final JButton addAccountButton = new JButton("Offline account");
    private final JButton removeAccountButton = new JButton("Delete");
    private final JButton settingsButton = new JButton("Settings");
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

        defaultPlayButtonForeground = playButton.getForeground();

        changelog.setEditable(false);
        changelog.setContentType("text/html");
        changelog.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        changelog.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        changelog.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        changelog.setBackground(Color.WHITE);
        changelog.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE) && e.getURL() != null) {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    }
                } catch (Exception ignored) {
                }
            }
        });

        JPanel content = new JPanel(new BorderLayout(12, 12)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int h = Math.max(getHeight(), 1);
                    float[] fractions = { 0.0f, 0.15f, 0.35f, 1.0f };
                    Color[] colors = { TOMATE_PINK, TOMATE_PINK, TOMATE_SOFT_PINK, TOMATE_SOFT_PINK };
                    LinearGradientPaint gp = new LinearGradientPaint(0, 0, 0, h, fractions, colors);
                    g2.setPaint(gp);
                    g2.fillRect(0, 0, getWidth(), h);
                } finally {
                    g2.dispose();
                }
            }
        };
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(topPanel(), BorderLayout.NORTH);
        content.add(changelogScrollPane, BorderLayout.CENTER);
        content.add(bottomBar(), BorderLayout.SOUTH);

        frame.setContentPane(content);
        loadLogo();
    }

    private JPanel topPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        logoLabel.setPreferredSize(new Dimension(320, 104));
        logoLabel.setHorizontalAlignment(JLabel.CENTER);
        logoLabel.setFont(logoLabel.getFont().deriveFont(Font.BOLD, 22f));
        logoLabel.setForeground(Color.WHITE);
        panel.add(logoLabel, BorderLayout.NORTH);

        changelogTitleWrapper.setOpaque(false);
        changelogTitleWrapper.removeAll();

        JPanel titleBadge = new JPanel(new BorderLayout());
        titleBadge.setBackground(Color.BLACK);
        titleBadge.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));

        JLabel title = new JLabel("Changelog");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setForeground(Color.WHITE);

        titleBadge.add(title, BorderLayout.CENTER);
        changelogTitleWrapper.add(titleBadge);
        panel.add(changelogTitleWrapper, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel bottomBar() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(TOMATE_PINK);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints left = constraints(0, 0, 0.25, GridBagConstraints.WEST);
        GridBagConstraints center = constraints(1, 0, 0.5, GridBagConstraints.CENTER);
        GridBagConstraints right = constraints(2, 0, 0.25, GridBagConstraints.EAST);

        settingsButton.addActionListener(event -> openSettings());

        playButton.setPreferredSize(new Dimension(220, 54));
        playButton.setFont(playButton.getFont().deriveFont(Font.BOLD, 22f));
        playButton.addActionListener(event -> onPlayOrKillClicked());

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

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.setBackground(TOMATE_PINK);
        rightPanel.add(accountBox);
        rightPanel.add(addAccountButton);
        rightPanel.add(removeAccountButton);
        rightPanel.add(settingsButton);

        JPanel playPanel = new JPanel(new BorderLayout(4, 4));
        playPanel.setBackground(TOMATE_PINK);
        playPanel.add(playButton, BorderLayout.CENTER);
        playPanel.add(statusPanel(), BorderLayout.SOUTH);

        JPanel leftPanel = new JPanel();
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(rightPanel.getPreferredSize().width, 1));

        panel.add(leftPanel, left);
        panel.add(playPanel, center);
        panel.add(rightPanel, right);
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
        changelog.setText("<html><body style='font-family:sans-serif;padding:8px;'><p>Loading changelog...</p></body></html>");
        changelog.setCaretPosition(0);
        Java8.startThread(() -> {
            try {
                String changelogUrl = config.get("changelog.url");
                if (Java8.isBlank(changelogUrl) || changelogUrl.contains("changelog.txt")) {
                    changelogUrl = "https://fixbrofficial.neocities.org/tomate/changelog";
                }
                String body = Java8.httpGetString(Java8.rawGitHubUrl(changelogUrl));
                SwingUtilities.invokeLater(() -> {
                    changelog.setText(body);
                    changelog.setCaretPosition(0);
                    SwingUtilities.invokeLater(() -> changelog.setCaretPosition(0));
                });
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> {
                    changelog.setText("<html><body style='font-family:sans-serif;padding:8px;'><p>Could not load the changelog.</p><p>"
                            + exception.getMessage() + "</p></body></html>");
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


    private void openSettings() {
        JTextField javaPathField = new JTextField(config.get("java.path"), 28);
        JTextField javaArgsField = new JTextField(config.get("java.args"), 28);
        JTextField minecraftDirField = new JTextField(config.get("minecraft.dir"), 20);
        JCheckBox sodiumBox = new JCheckBox("Sodium", config.getBoolean("sodium.enabled", true));

        JButton openDirButton = new JButton("Open .paradise");
        openDirButton.addActionListener(event -> openMinecraftFolder());

        JPanel folderPanel = new JPanel(new BorderLayout(6, 0));
        folderPanel.add(minecraftDirField, BorderLayout.CENTER);
        folderPanel.add(openDirButton, BorderLayout.EAST);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(new JLabel("Java Path:"), settingsConstraints(0, 0));
        panel.add(javaPathField, settingsConstraints(1, 0));
        panel.add(new JLabel("Java arguments:"), settingsConstraints(0, 1));
        panel.add(javaArgsField, settingsConstraints(1, 1));
        panel.add(new JLabel("Minecraft folder:"), settingsConstraints(0, 2));
        panel.add(folderPanel, settingsConstraints(1, 2));
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

    private void setChangelogVisible(boolean visible) {
        changelogTitleWrapper.setVisible(visible);
        changelogScrollPane.setVisible(visible);
        if (!visible) {
            changelog.setText("");
            System.gc();
        }
        if (frame.getContentPane() != null) {
            frame.getContentPane().revalidate();
            frame.getContentPane().repaint();
        }
    }

    private void onPlayOrKillClicked() {
        Process process = this.gameProcess;
        if (process != null && process.isAlive()) {
            killGame();
            return;
        }
        play();
    }

    private void killGame() {
        Process process = this.gameProcess;
        if (process != null && process.isAlive()) {
            playButton.setEnabled(false);
            setStatus("Closing Minecraft...");
            Java8.startThread(() -> {
                try {
                    process.destroyForcibly();
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void play() {
        Object selected = accountBox.getSelectedItem();
        if (selected == null || Java8.isBlank(selected.toString())) {
            addAccount();
            return;
        }
        setChangelogVisible(false);
        setBusy(true, "Preparing...");
        Java8.startThread(() -> {
            try {
                Process process = new MinecraftLauncher(config, this::setStatus).launch(selected.toString());
                this.gameProcess = process;
                SwingUtilities.invokeLater(() -> {
                    onGameRunning();
                    frame.setState(JFrame.ICONIFIED);
                });
                try {
                    process.waitFor();
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> showError("Could not open Minecraft.", exception));
            } finally {
                this.gameProcess = null;
                SwingUtilities.invokeLater(this::onGameFinished);
            }
        });
    }

    private void onGameRunning() {
        progressBar.setVisible(false);
        statusLabel.setText("Minecraft is running");
        statusLabel.setVisible(true);
        playButton.setText("Kill");
        playButton.setForeground(new Color(220, 30, 30));
        playButton.setEnabled(true);
    }

    private void onGameFinished() {
        playButton.setText("PLAY");
        if (defaultPlayButtonForeground != null) {
            playButton.setForeground(defaultPlayButtonForeground);
        }
        setBusy(false, " ");
        setChangelogVisible(true);
        loadChangelog();
    }

    private void setBusy(boolean busy, String status) {
        playButton.setEnabled(!busy);
        addAccountButton.setEnabled(!busy);
        removeAccountButton.setEnabled(!busy && accountBox.getItemCount() > 0);
        settingsButton.setEnabled(!busy);
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
