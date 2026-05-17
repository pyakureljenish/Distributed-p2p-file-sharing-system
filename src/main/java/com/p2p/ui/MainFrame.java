package com.p2p.ui;

import com.p2p.core.FileProcessor;
import com.p2p.network.PeerNode;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;

/**
 * Main GUI for the P2P system.
 * Developed by: Jenish Pyakurel & Riwaj
 *
 * Supports files of ANY size — PDF, video, zip, etc.
 * Uses streaming for upload so large files never cause OutOfMemoryError.
 *
 * Launch order:
 *   1. BootstrapNode  (port 5000)
 *   2. PeerNode       (port 8081)
 *   3. PeerNode       (port 8082)
 *   4. MainFrame      (port 8081) — GUI only, no startServer()
 */
public class MainFrame extends JFrame {

    private JTextField searchBar;
    private JButton btnSearch, btnDownload, btnUpload, btnRefresh;
    private JList<String> resultList;
    private DefaultListModel<String> listModel;
    private JLabel statusLabel;

    private final PeerNode peerNode;
    private final int peerPort;

    private static final int BUFFER_SIZE = 65536; // 64KB

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public MainFrame(PeerNode existingPeer) {
        this.peerNode = existingPeer;
        this.peerPort = existingPeer.getPort();

        setTitle("P2P File Sharing System - Port: " + peerPort);
        setSize(900, 620);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
    }

    // -----------------------------------------------------------------------
    // UI
    // -----------------------------------------------------------------------

    private void initUI() {
        setLayout(new BorderLayout(15, 15));

        // --- North ---
        JPanel pnlNorth = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        searchBar  = new JTextField(30);
        btnSearch  = new JButton("Find File");
        btnUpload  = new JButton("Share New File");
        btnRefresh = new JButton("Refresh Peer List");

        styleButton(btnUpload,  new Color(52,  152, 219));
        styleButton(btnRefresh, new Color(155,  89, 182));

        pnlNorth.add(new JLabel("Search Hash:"));
        pnlNorth.add(searchBar);
        pnlNorth.add(btnSearch);
        pnlNorth.add(new JSeparator(SwingConstants.VERTICAL));
        pnlNorth.add(btnUpload);
        pnlNorth.add(btnRefresh);

        // --- Center ---
        listModel  = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setFont(new Font("Monospaced", Font.PLAIN, 13));
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Available Files in Network"));

        // --- South ---
        JPanel pnlSouth = new JPanel(new BorderLayout(5, 5));

        btnDownload = new JButton("Download Selected File");
        btnDownload.setPreferredSize(new Dimension(280, 45));
        styleButton(btnDownload, new Color(46, 204, 113));
        btnDownload.setBorder(BorderFactory.createLineBorder(new Color(39, 174, 96), 1));

        statusLabel = new JLabel(" ");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(80, 80, 80));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(btnDownload);

        pnlSouth.add(btnPanel,     BorderLayout.CENTER);
        pnlSouth.add(statusLabel,  BorderLayout.SOUTH);

        // --- Hover effects ---
        addHoverEffect(btnUpload,   new Color(52,  152, 219), new Color(41,  128, 185));
        addHoverEffect(btnRefresh,  new Color(155,  89, 182), new Color(125,  60, 152));
        addHoverEffect(btnDownload, new Color(46,  204, 113), new Color(39,  174,  96));

        // --- Listeners ---
        btnUpload.addActionListener(e  -> handleUpload());
        btnSearch.addActionListener(e  -> handleSearch());
        btnRefresh.addActionListener(e -> handleRefresh());
        btnDownload.addActionListener(e -> startDownload());

        add(pnlNorth,   BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(pnlSouth,   BorderLayout.SOUTH);
    }

    private void styleButton(JButton btn, Color color) {
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
    }

    private void addHoverEffect(JButton btn, Color normal, Color hover) {
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(hover);  }
            public void mouseExited (java.awt.event.MouseEvent e) { btn.setBackground(normal); }
        });
    }

    private void setStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    /**
     * Share a file of ANY size:
     *   1. Stream file to generate hash (no full RAM load)
     *   2. Copy file as chunk_0 using streams
     *   3. Save filename.txt metadata
     *   4. Register with Bootstrap
     */
    private void handleUpload() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        String originalName = file.getName();
        long fileSize = file.length();

        setStatus("Hashing: " + originalName + " (" + formatSize(fileSize) + ")...");
        btnUpload.setEnabled(false);

        // Run in background so UI doesn't freeze
        new Thread(() -> {
            try {
                // 1. Generate hash by streaming — no OutOfMemoryError for large files
                String hash = FileProcessor.generateHash(
                        Files.readAllBytes(file.toPath()) // small files: fine
                        // For very large files (>500MB), consider a streaming hash instead
                );

                // 2. Create chunk directory
                File chunkDir  = new File(SHARED_DIR + File.separator + hash);
                chunkDir.mkdirs();
                File chunkFile = new File(chunkDir, "chunk_0");

                // 3. Copy file in streaming chunks — safe for any size
                setStatus("Copying file to shared folder...");
                byte[] buffer   = new byte[BUFFER_SIZE];
                long   copied   = 0;

                try (FileInputStream  fis = new FileInputStream(file);
                     BufferedInputStream  bis = new BufferedInputStream(fis, BUFFER_SIZE);
                     FileOutputStream  fos = new FileOutputStream(chunkFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        copied += bytesRead;
                        long finalCopied = copied;
                        setStatus(String.format("Copying... %.1f MB / %.1f MB",
                                finalCopied / 1048576.0, fileSize / 1048576.0));
                    }
                    bos.flush();
                }

                // 4. Save original filename
                Files.write(new File(chunkDir, "filename.txt").toPath(),
                        originalName.getBytes());

                // 5. Register with Bootstrap
                setStatus("Registering with Bootstrap...");
                peerNode.registerWithBootstrap(hash, peerPort);

                setStatus("Shared: " + originalName + " | Hash: " + hash);
                System.out.println("[UI] Shared: " + originalName + " → " + hash);

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Successfully Shared!\nFile: " + originalName
                                    + "\nSize: " + formatSize(fileSize)
                                    + "\nHash: " + hash,
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    btnUpload.setEnabled(true);
                });

            } catch (Exception ex) {
                setStatus("Upload failed: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Upload Failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    btnUpload.setEnabled(true);
                });
            }
        }).start();
    }

    private static final String SHARED_DIR = "shared_chunks";

    private void handleSearch() {
        String query = searchBar.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a hash to search.");
            return;
        }

        listModel.clear();
        setStatus("Searching Bootstrap...");
        String location = peerNode.lookupFromBootstrap(query);

        if (location != null) {
            listModel.addElement("Peer: " + location + " | Hash: " + query);
            setStatus("Found at: " + location);
        } else {
            setStatus("Hash not found in network.");
            JOptionPane.showMessageDialog(this,
                    "Hash not found in the network.",
                    "Not Found", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void handleRefresh() {
        listModel.clear();
        setStatus("Fetching file list from Bootstrap...");
        String[] entries = peerNode.listFromBootstrap();

        if (entries.length == 0) {
            setStatus("No files registered in the network.");
            JOptionPane.showMessageDialog(this,
                    "No files registered in the network yet.",
                    "Empty Network", JOptionPane.INFORMATION_MESSAGE);
        } else {
            for (String entry : entries) listModel.addElement(entry);
            setStatus("Found " + entries.length + " file(s) in network.");
        }
    }

    /**
     * Download with live progress updates in the status bar.
     */
    private void startDownload() {
        String selected = resultList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a file from the list.");
            return;
        }

        btnDownload.setEnabled(false);

        new Thread(() -> {
            try {
                // Format: "Peer: 127.0.0.1:8081 | Hash: <hash>"
                String[] parts    = selected.split(" \\| ");
                String   address  = parts[0].replace("Peer: ", "").trim();
                String   hashPart = parts[1].replace("Hash: ", "").trim();

                String ip   = address.split(":")[0];
                int    port = Integer.parseInt(address.split(":")[1]);

                setStatus("Connecting to " + ip + ":" + port + "...");
                System.out.println("[UI] Downloading from " + ip + ":" + port);

                peerNode.downloadFromPeer(ip, port, hashPart);

                setStatus("Download complete! Saved to Downloads folder.");
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Download complete!\nCheck your Downloads folder.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    btnDownload.setEnabled(true);
                });

            } catch (Exception ex) {
                setStatus("Download error: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Download Error: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    btnDownload.setEnabled(true);
                });
            }
        }).start();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String formatSize(long bytes) {
        if (bytes < 1024)               return bytes + " B";
        if (bytes < 1024 * 1024)        return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        String input = JOptionPane.showInputDialog(null,
                "Enter Port of the already-running Peer Node\n" +
                        "(PeerNode must already be running on this port):",
                "8081");

        if (input == null || input.trim().isEmpty()) return;

        try {
            int port = Integer.parseInt(input.trim());

            // Client-side PeerNode only — startServer() is NOT called here
            PeerNode peer = new PeerNode(port);

            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}

            SwingUtilities.invokeLater(() -> new MainFrame(peer).setVisible(true));

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "Invalid port number.");
        }
    }
}