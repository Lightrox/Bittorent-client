import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class AppGUI {

    private static final Color BG_DARK = new Color(30, 30, 46);     // Dark Slate Base
    private static final Color BG_PANEL = new Color(45, 45, 68);    // Lighter Slate Panel
    private static final Color FG_TEXT = new Color(205, 214, 244);   // Pastel White text
    private static final Color FG_ACCENT = new Color(137, 180, 250); // Pastel Blue Accent
    private static final Color FG_GREEN = new Color(166, 227, 161);  // Terminal Green Accent
    private static final Color BG_INPUT = new Color(24, 24, 37);     // Input fields background

    private static JFrame frame;
    private static JTextField fileField;
    private static JTextField portField;
    private static JComboBox<String> modeCombo;
    private static JButton startButton;
    private static JProgressBar progressBar;
    private static JTextArea logArea;
    private static JLabel nameLabel;
    private static JLabel sizeLabel;
    private static JLabel piecesLabel;
    private static JLabel statusLabel;
    private static JLabel peersLabel;

    public static void launch() {
        // Set system look and feel or default
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        frame = new JFrame("BitTorrent Client — AntiGravity GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(850, 650);
        frame.setLocationRelativeTo(null);

        // Custom main panel with dark background
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(BG_DARK);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        frame.setContentPane(mainPanel);

        // HEADER
        JLabel titleLabel = new JLabel("BitTorrent Client", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titleLabel.setForeground(FG_ACCENT);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // CENTER CONTAINER
        JPanel centerPanel = new JPanel(new BorderLayout(15, 15));
        centerPanel.setOpaque(false);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // FORM PANEL (File browsing, port, mode)
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(BG_PANEL);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BG_PANEL.brighter(), 1, true),
                new EmptyBorder(15, 15, 15, 15)
        ));
        centerPanel.add(formPanel, BorderLayout.NORTH);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);

        // Row 1: Torrent File selection
        JLabel fileLabel = new JLabel("Torrent File:");
        fileLabel.setForeground(FG_TEXT);
        fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(fileLabel, gbc);

        fileField = new JTextField();
        fileField.setEditable(false);
        fileField.setBackground(BG_INPUT);
        fileField.setForeground(FG_TEXT);
        fileField.setCaretColor(FG_TEXT);
        fileField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BG_PANEL.brighter(), 1),
                new EmptyBorder(5, 5, 5, 5)
        ));
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.weightx = 1.0;
        formPanel.add(fileField, gbc);

        JButton browseButton = createStyledButton("Browse");
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser(new File("torrents"));
            fileChooser.setDialogTitle("Select a .torrent File");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Torrent Files", "torrent"));
            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                fileField.setText(fileChooser.getSelectedFile().getAbsolutePath());
                // Load metadata preview synchronously
                try {
                    TorrentMetadata metadata = TorrentMetadata.fromFile(fileChooser.getSelectedFile().getAbsolutePath());
                    nameLabel.setText("File: " + metadata.getFileName());
                    sizeLabel.setText("Size: " + formatSize(metadata.getFileSize()));
                    piecesLabel.setText("Pieces: " + metadata.getTotalPieces());
                } catch (Exception ex) {
                    System.err.println("Error reading metadata: " + ex.getMessage());
                }
            }
        });
        gbc.gridx = 2; gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(browseButton, gbc);

        // Row 2: Port & Mode
        JPanel row2Panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        row2Panel.setOpaque(false);

        JLabel portLabel = new JLabel("Port:");
        portLabel.setForeground(FG_TEXT);
        row2Panel.add(portLabel);

        portField = new JTextField("6881", 6);
        portField.setBackground(BG_INPUT);
        portField.setForeground(FG_TEXT);
        portField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BG_PANEL.brighter(), 1),
                new EmptyBorder(5, 5, 5, 5)
        ));
        row2Panel.add(portField);

        JLabel modeLabel = new JLabel("Mode:");
        modeLabel.setForeground(FG_TEXT);
        row2Panel.add(modeLabel);

        modeCombo = new JComboBox<>(new String[]{"leecher", "seeder"});
        modeCombo.setBackground(BG_INPUT);
        modeCombo.setForeground(FG_TEXT);
        row2Panel.add(modeCombo);

        gbc.gridx = 1; gbc.gridy = 1;
        gbc.weightx = 1.0;
        formPanel.add(row2Panel, gbc);

        // DETAILS PANEL (Displays metadata and status information)
        JPanel detailsPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        detailsPanel.setOpaque(false);
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.weightx = 1.0;
        formPanel.add(detailsPanel, gbc);

        nameLabel = new JLabel("File: Select a torrent...");
        nameLabel.setForeground(FG_TEXT);
        nameLabel.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        detailsPanel.add(nameLabel);

        sizeLabel = new JLabel("Size: —");
        sizeLabel.setForeground(FG_TEXT);
        sizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        detailsPanel.add(sizeLabel);

        piecesLabel = new JLabel("Pieces: —");
        piecesLabel.setForeground(FG_TEXT);
        piecesLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        detailsPanel.add(piecesLabel);

        peersLabel = new JLabel("Peers: —");
        peersLabel.setForeground(FG_TEXT);
        peersLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        detailsPanel.add(peersLabel);

        // LOG AREA (Redirect console output here)
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(BG_INPUT);
        logArea.setForeground(FG_GREEN);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(new LineBorder(BG_PANEL.brighter(), 1, true));
        centerPanel.add(logScrollPane, BorderLayout.CENTER);

        // Redirection Stream
        PrintStream printStream = new PrintStream(new CustomOutputStream(logArea), true, StandardCharsets.UTF_8);
        System.setOut(printStream);
        System.setErr(printStream);

        // SOUTH PANEL (Progress bar, status message, download button)
        JPanel southPanel = new JPanel(new BorderLayout(10, 10));
        southPanel.setOpaque(false);
        mainPanel.add(southPanel, BorderLayout.SOUTH);

        // Progress bar container
        JPanel progressContainer = new JPanel(new BorderLayout(10, 5));
        progressContainer.setOpaque(false);
        southPanel.add(progressContainer, BorderLayout.CENTER);

        statusLabel = new JLabel("Status: Ready");
        statusLabel.setForeground(FG_ACCENT);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        progressContainer.add(statusLabel, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setBackground(BG_INPUT);
        progressBar.setForeground(FG_GREEN);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressBar.setBorder(new LineBorder(BG_PANEL.brighter(), 1));
        progressContainer.add(progressBar, BorderLayout.CENTER);

        startButton = createStyledButton("Start Download");
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        startButton.setPreferredSize(new Dimension(200, 50));
        startButton.addActionListener(e -> {
            String path = fileField.getText();
            if (path.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please browse and select a torrent file first.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int port = 6881;
            try {
                port = Integer.parseInt(portField.getText());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Invalid port number. Defaulting to 6881.", "Warning", JOptionPane.WARNING_MESSAGE);
                portField.setText("6881");
            }
            String mode = (String) modeCombo.getSelectedItem();

            // Reset UI for download
            logArea.setText("");
            progressBar.setValue(0);
            progressBar.setString("0% (initializing)");
            startButton.setEnabled(false);

            final int downloadPort = port;
            new Thread(() -> {
                try {
                    App.runClient(path, downloadPort, mode);
                } catch (Exception ex) {
                    System.err.println("\nExecution failed: " + ex.getMessage());
                    ex.printStackTrace();
                } finally {
                    SwingUtilities.invokeLater(() -> startButton.setEnabled(true));
                }
            }).start();
        });
        southPanel.add(startButton, BorderLayout.EAST);

        // GUI polling timer to update values periodically
        Timer guiTimer = new Timer(200, e -> {
            statusLabel.setText("Status: " + App.activeStatus);
            peersLabel.setText("Connected Peers: " + App.activeConnectedPeers);

            if (App.activePieceManager != null) {
                int completed = App.activePieceManager.getCompletedCount();
                int total = App.activePieceManager.getTotalPieces();
                progressBar.setMaximum(total);
                progressBar.setValue(completed);
                progressBar.setString(completed + " / " + total + " pieces (" + (completed * 100 / total) + "%)");
            }
        });
        guiTimer.start();

        frame.setVisible(true);
    }

    private static JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(BG_PANEL);
        button.setForeground(FG_TEXT);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(FG_ACCENT, 1, true),
                new EmptyBorder(8, 15, 8, 15)
        ));
        button.setContentAreaFilled(false);
        button.setOpaque(true);

        // Hover effects
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(BG_PANEL.brighter());
                button.setForeground(Color.WHITE);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(BG_PANEL);
                button.setForeground(FG_TEXT);
            }
        });
        return button;
    }

    private static String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private static class CustomOutputStream extends OutputStream {
        private final JTextArea textArea;

        public CustomOutputStream(JTextArea textArea) {
            this.textArea = textArea;
        }

        @Override
        public void write(int b) {
            SwingUtilities.invokeLater(() -> {
                textArea.append(String.valueOf((char) b));
                textArea.setCaretPosition(textArea.getDocument().getLength());
            });
        }

        @Override
        public void write(byte[] b, int off, int len) {
            String str = new String(b, off, len, StandardCharsets.UTF_8);
            SwingUtilities.invokeLater(() -> {
                textArea.append(str);
                textArea.setCaretPosition(textArea.getDocument().getLength());
            });
        }
    }
}
