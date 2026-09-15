package br.com.simplelauncher;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;

final class GameLogWindow {
    private final JFrame frame = new JFrame("Tomate Launcher - Minecraft Log");
    private final JTextArea textArea = new JTextArea();

    GameLogWindow() {
        textArea.setEditable(false);
        textArea.setLineWrap(false);

        JButton clearButton = new JButton("Limpar");
        clearButton.addActionListener(event -> textArea.setText(""));

        JPanel buttons = new JPanel();
        buttons.add(clearButton);

        frame.setLayout(new BorderLayout(8, 8));
        frame.add(new JScrollPane(textArea), BorderLayout.CENTER);
        frame.add(buttons, BorderLayout.SOUTH);
        frame.setMinimumSize(new Dimension(900, 520));
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    void show() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    void append(String line) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(line);
            textArea.append(System.lineSeparator());
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }
}
