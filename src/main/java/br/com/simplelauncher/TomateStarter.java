package br.com.simplelauncher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TomateStarter {
    private static final String GITHUB_REPO = "FixBROfficial/Tomate-Launcher-9";
    private static final String GITHUB_BRANCH = "main";
    private static final String COMMITS_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/commits/" + GITHUB_BRANCH;
    private static final String RAW_BASE_URL = "https://raw.githubusercontent.com/" + GITHUB_REPO + "/";

    private volatile String latestCommitSha = GITHUB_BRANCH;

    private static final Color TOMATE_PINK = Color.decode("#c85398");
    private static final Color TOMATE_LIGHT_PINK = Color.decode("#f8d8ea");
    private static final Color BG_DARK = Color.decode("#202026");
    private static final Color BG_CARD = Color.decode("#2a2a33");
    private static final Color TEXT_WHITE = Color.decode("#f0f0f5");
    private static final Color TEXT_MUTED = Color.decode("#a5a5b5");

    private final Path launcherDir;

    private final JFrame frame = new JFrame("Tomate Starter");
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel centerPanel = new JPanel(cardLayout);

    // Card 1: Progresso / Carregamento
    private final JLabel statusLabel = new JLabel("Iniciando...");
    private final JLabel detailLabel = new JLabel("Preparando ambiente...");
    private final JProgressBar progressBar = new JProgressBar();

    // Card 2: Pergunta de Atualização
    private final JLabel updateNoticeLabel = new JLabel("Nova atualização disponível!");
    private final JLabel updateDetailLabel = new JLabel("Arquivos novos ou alterados foram encontrados.");
    private final JButton updateNowButton = new JButton("Atualizar Agora");
    private final JButton updateLaterButton = new JButton("Atualizar Depois");

    private List<RemoteFile> filesToUpdate = new ArrayList<RemoteFile>();

    public TomateStarter() {
        this.launcherDir = appDataDir().resolve("TomateLauncher9").resolve("Launcher");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                TomateStarter starter = new TomateStarter();
                starter.buildUI();
                starter.startScan();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static Path appDataDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.trim().isEmpty()) {
            return Paths.get(appData);
        }
        return Paths.get(System.getProperty("user.home"));
    }

    private void buildUI() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Janela 2/5 maior: 480 * 1.4 = 672, 270 * 1.4 = 378 (usando 672x380)
        frame.setSize(672, 380);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG_DARK);
        frame.setLayout(new BorderLayout(15, 15));

        // Cabeçalho Centralizado
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(BG_DARK);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 5, 20));

        JLabel titleLabel = new JLabel("Tomate Starter", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titleLabel.setForeground(TOMATE_PINK);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLabel = new JLabel("Gerenciador e Atualizador do Launcher", SwingConstants.CENTER);
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subLabel.setForeground(TEXT_MUTED);
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(subLabel);
        frame.add(headerPanel, BorderLayout.NORTH);

        // Card 1: Painel de Progresso Centralizado
        JPanel progressCard = new JPanel();
        progressCard.setLayout(new BoxLayout(progressCard, BoxLayout.Y_AXIS));
        progressCard.setBackground(BG_CARD);
        progressCard.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        statusLabel.setForeground(TEXT_WHITE);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        detailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        detailLabel.setForeground(TEXT_MUTED);
        detailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        detailLabel.setHorizontalAlignment(SwingConstants.CENTER);

        progressBar.setPreferredSize(new Dimension(580, 16));
        progressBar.setMaximumSize(new Dimension(580, 16));
        progressBar.setForeground(TOMATE_PINK);
        progressBar.setBackground(BG_DARK);
        progressBar.setBorder(BorderFactory.createLineBorder(TOMATE_PINK, 1));
        progressBar.setIndeterminate(true);
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        progressCard.add(Box.createVerticalGlue());
        progressCard.add(statusLabel);
        progressCard.add(Box.createVerticalStrut(8));
        progressCard.add(detailLabel);
        progressCard.add(Box.createVerticalStrut(18));
        progressCard.add(progressBar);
        progressCard.add(Box.createVerticalGlue());

        // Card 2: Painel de Pergunta de Atualização Centralizado
        JPanel promptCard = new JPanel();
        promptCard.setLayout(new BoxLayout(promptCard, BoxLayout.Y_AXIS));
        promptCard.setBackground(BG_CARD);
        promptCard.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        updateNoticeLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        updateNoticeLabel.setForeground(TOMATE_LIGHT_PINK);
        updateNoticeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        updateNoticeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        updateDetailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        updateDetailLabel.setForeground(TEXT_WHITE);
        updateDetailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        updateDetailLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttonPanel.setBackground(BG_CARD);
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        styleButton(updateLaterButton, BG_DARK, TEXT_MUTED, BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BG_DARK.brighter(), 1),
                BorderFactory.createEmptyBorder(10, 22, 10, 22)
        ));
        updateLaterButton.addActionListener(e -> launchLauncher());

        styleButton(updateNowButton, TOMATE_PINK, Color.WHITE, BorderFactory.createEmptyBorder(10, 24, 10, 24));
        updateNowButton.addActionListener(e -> startDownload());

        buttonPanel.add(updateLaterButton);
        buttonPanel.add(updateNowButton);

        promptCard.add(Box.createVerticalGlue());
        promptCard.add(updateNoticeLabel);
        promptCard.add(Box.createVerticalStrut(8));
        promptCard.add(updateDetailLabel);
        promptCard.add(Box.createVerticalStrut(20));
        promptCard.add(buttonPanel);
        promptCard.add(Box.createVerticalGlue());

        centerPanel.setBackground(BG_DARK);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        centerPanel.add(progressCard, "PROGRESS");
        centerPanel.add(promptCard, "PROMPT");
        frame.add(centerPanel, BorderLayout.CENTER);

        // Rodapé Centralizado
        JLabel footerLabel = new JLabel("Pasta: %appdata%/TomateLauncher9/Launcher", SwingConstants.CENTER);
        footerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        footerLabel.setForeground(Color.decode("#707080"));
        footerLabel.setBorder(BorderFactory.createEmptyBorder(5, 20, 15, 20));
        frame.add(footerLabel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void styleButton(JButton button, Color bg, Color fg, javax.swing.border.Border border) {
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFocusPainted(false);
        button.setBorder(border);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private void startScan() {
        showProgressCard("Verificando atualizações...", "Consultando repositório no GitHub...", true);

        new Thread(() -> {
            try {
                Files.createDirectories(launcherDir);

                boolean localLauncherExists = checkLocalLauncherExists();

                List<RemoteFile> remoteFiles = fetchRemoteFileList();
                List<RemoteFile> pending = new ArrayList<RemoteFile>();

                for (int i = 0; i < remoteFiles.size(); i++) {
                    RemoteFile remote = remoteFiles.get(i);
                    final String currentFile = remote.path;
                    final int idx = i + 1;
                    final int total = remoteFiles.size();

                    SwingUtilities.invokeLater(() -> {
                        detailLabel.setText("Examinando (" + idx + "/" + total + "): " + currentFile);
                    });

                    Path localFile = launcherDir.resolve(remote.path);
                    if (!Files.exists(localFile)) {
                        pending.add(remote);
                    } else {
                        String localSha = computeGitBlobSha1(localFile);
                        if (!localSha.equalsIgnoreCase(remote.sha)) {
                            pending.add(remote);
                        }
                    }
                }

                this.filesToUpdate = pending;

                if (pending.isEmpty()) {
                    // Sem atualizações pendentes
                    SwingUtilities.invokeLater(() -> {
                        showProgressCard("Tudo atualizado!", "Iniciando Tomate Launcher...", false);
                        progressBar.setValue(100);
                    });
                    Thread.sleep(600);
                    launchLauncher();
                } else if (!localLauncherExists) {
                    // Primeira instalação: não tem launcher local, baixa tudo direto!
                    SwingUtilities.invokeLater(() -> {
                        showProgressCard("Primeira instalação", "Baixando arquivos do Launcher pela primeira vez...", false);
                    });
                    performDownload();
                } else {
                    // Tem atualização e já tem versão local: Pergunta ao usuário!
                    SwingUtilities.invokeLater(() -> {
                        updateNoticeLabel.setText("Atualização disponível!");
                        updateDetailLabel.setText("Há " + pending.size() + " arquivo(s) modificado(s) ou novo(s) no GitHub.");
                        cardLayout.show(centerPanel, "PROMPT");
                    });
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                // Em caso de falha de conexão (offline), tenta abrir o launcher local se existir
                if (checkLocalLauncherExists()) {
                    SwingUtilities.invokeLater(() -> {
                        showProgressCard("Modo Offline", "Sem conexão com o GitHub. Abrindo versão local...", true);
                    });
                    try {
                        Thread.sleep(1200);
                    } catch (InterruptedException ignored) {}
                    launchLauncher();
                } else {
                    SwingUtilities.invokeLater(() -> {
                        showProgressCard("Erro de Conexão", "Não foi possível conectar ao GitHub para baixar o Launcher.", false);
                        detailLabel.setText("Verifique sua internet e abra o Starter novamente.");
                    });
                }
            }
        }).start();
    }

    private void startDownload() {
        cardLayout.show(centerPanel, "PROGRESS");
        new Thread(this::performDownload).start();
    }

    private void performDownload() {
        try {
            int total = filesToUpdate.size();
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setMinimum(0);
                progressBar.setMaximum(total);
                progressBar.setValue(0);
            });

            for (int i = 0; i < total; i++) {
                RemoteFile file = filesToUpdate.get(i);
                final int current = i + 1;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Baixando atualização (" + current + "/" + total + ")...");
                    detailLabel.setText(file.path);
                    progressBar.setValue(current);
                });

                Path target = launcherDir.resolve(file.path).normalize();
                if (!target.startsWith(launcherDir)) {
                    continue; // Proteção contra path traversal
                }

                Files.createDirectories(target.getParent());
                String downloadUrl = RAW_BASE_URL + latestCommitSha + "/" + file.path + "?_t=" + System.currentTimeMillis();
                httpDownload(downloadUrl, target);

                String downloadedSha = computeGitBlobSha1(target);
                if (!downloadedSha.equalsIgnoreCase(file.sha)) {
                    throw new IOException("Falha de integridade: " + file.path + " (esperado " + file.sha + ", recebido " + downloadedSha + ")");
                }
            }

            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Atualização concluída!");
                detailLabel.setText("Iniciando o Launcher...");
            });

            Thread.sleep(700);
            launchLauncher();

        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Erro ao baixar arquivos!");
                detailLabel.setText(ex.getMessage());
            });
        }
    }

    private boolean checkLocalLauncherExists() {
        Path mainClassFile = launcherDir.resolve("out").resolve("br").resolve("com").resolve("simplelauncher").resolve("LauncherApp.class");
        if (Files.exists(mainClassFile)) {
            return true;
        }
        Path jarFile = launcherDir.resolve("TomateLauncher8.jar");
        return Files.exists(jarFile);
    }

    private void launchLauncher() {
        SwingUtilities.invokeLater(() -> {
            showProgressCard("Iniciando...", "Abrindo Tomate Launcher...", true);
        });

        new Thread(() -> {
            try {
                String javaExec = findJavaExecutable();
                String classpath = buildClasspath();

                List<String> command = new ArrayList<String>();
                command.add(javaExec);
                command.add("-cp");
                command.add(classpath);
                command.add("br.com.simplelauncher.LauncherApp");

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(launcherDir.toFile());
                pb.start();

                // Fecha o Starter após abrir o Launcher
                System.exit(0);

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    showProgressCard("Erro ao iniciar!", ex.getMessage(), false);
                });
            }
        }).start();
    }

    private String buildClasspath() {
        List<String> cpEntries = new ArrayList<String>();
        Path outDir = launcherDir.resolve("out");
        if (Files.exists(outDir)) {
            cpEntries.add(outDir.toAbsolutePath().toString());
        }

        // Se houver jars no Launcher, adiciona ao classpath
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(launcherDir, "*.jar")) {
            for (Path jar : stream) {
                cpEntries.add(jar.toAbsolutePath().toString());
            }
        } catch (IOException ignored) {}

        if (cpEntries.isEmpty()) {
            cpEntries.add(launcherDir.toAbsolutePath().toString());
        }

        StringBuilder cp = new StringBuilder();
        for (int i = 0; i < cpEntries.size(); i++) {
            if (i > 0) cp.append(File.pathSeparator);
            cp.append(cpEntries.get(i));
        }
        return cp.toString();
    }

    private String findJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            Path javaw = Paths.get(javaHome, "bin", "javaw.exe");
            if (Files.exists(javaw)) return javaw.toString();
            Path java = Paths.get(javaHome, "bin", "java.exe");
            if (Files.exists(java)) return java.toString();
            Path javawUnix = Paths.get(javaHome, "bin", "javaw");
            if (Files.exists(javawUnix)) return javawUnix.toString();
            Path javaUnix = Paths.get(javaHome, "bin", "java");
            if (Files.exists(javaUnix)) return javaUnix.toString();
        }
        return "javaw";
    }

    private void showProgressCard(String title, String detail, boolean indeterminate) {
        statusLabel.setText(title);
        detailLabel.setText(detail);
        progressBar.setIndeterminate(indeterminate);
        cardLayout.show(centerPanel, "PROGRESS");
    }

    private List<RemoteFile> fetchRemoteFileList() throws Exception {
        // 1. Obtém o commit SHA mais recente do branch main com anti-cache
        String commitUrl = COMMITS_API_URL + "?_t=" + System.currentTimeMillis();
        String commitJson = httpGetString(commitUrl);
        Map<String, Object> commitObj = Json.object(Json.parse(commitJson));
        String shaCommit = Json.string(commitObj, "sha");
        if (shaCommit != null && !shaCommit.trim().isEmpty()) {
            this.latestCommitSha = shaCommit.trim();
        } else {
            this.latestCommitSha = GITHUB_BRANCH;
        }

        // 2. Consulta a árvore de arquivos apontando diretamente para o commit SHA exato
        String treeUrl = "https://api.github.com/repos/" + GITHUB_REPO + "/git/trees/" + this.latestCommitSha + "?recursive=1&_t=" + System.currentTimeMillis();
        String jsonText = httpGetString(treeUrl);
        Map<String, Object> root = Json.object(Json.parse(jsonText));
        List<Object> tree = Json.array(root.get("tree"));

        List<RemoteFile> files = new ArrayList<RemoteFile>();
        for (int i = 0; i < tree.size(); i++) {
            Map<String, Object> node = Json.object(tree.get(i));
            String type = Json.string(node, "type");
            String path = Json.string(node, "path");
            String sha = Json.string(node, "sha");

            if (!"blob".equals(type)) continue;

            // Ignorar arquivos de controle git e executável do starter
            if (path.startsWith(".git") || path.equalsIgnoreCase("TomateStarter.jar") || path.startsWith("Starter/")) {
                continue;
            }

            files.add(new RemoteFile(path, sha));
        }
        return files;
    }

    private String computeGitBlobSha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        long length = Files.size(file);
        String header = "blob " + length + "\0";
        digest.update(header.getBytes(StandardCharsets.US_ASCII));

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }

        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) {
            result.append(String.format("%02x", b & 0xff));
        }
        return result.toString();
    }

    private static String httpGetString(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setUseCaches(false);
        connection.setDefaultUseCaches(false);
        connection.setRequestProperty("User-Agent", "TomateStarter-Java8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("Expires", "0");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);

        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IOException("HTTP " + code + " ao consultar " + url);
        }

        try (InputStream in = connection.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static void httpDownload(String url, Path target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url.replace(" ", "%20")).openConnection();
        connection.setUseCaches(false);
        connection.setDefaultUseCaches(false);
        connection.setRequestProperty("User-Agent", "TomateStarter-Java8");
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("Expires", "0");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(15000);

        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IOException("HTTP " + code + " ao baixar " + url);
        }

        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static final class RemoteFile {
        final String path;
        final String sha;

        RemoteFile(String path, String sha) {
            this.path = path;
            this.sha = sha;
        }
    }
}
