package com.p2p.ui;

import com.p2p.core.DownloadManager;
import javax.swing.*;
import java.awt.*;

public class Dashboard extends JFrame {
    private JTextField searchField;
    private DefaultListModel<String> fileListModel;
    private DownloadManager downloadManager;

    public Dashboard() {
        this.downloadManager = new DownloadManager();
        setupUI();
    }

    private void setupUI() {
        setTitle("P2P Distributed File Sharing - VII Sem Project");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top Panel: Search
        JPanel topPanel = new JPanel(new FlowLayout());
        searchField = new JTextField(30);
        JButton searchButton = new JButton("Search File");
        topPanel.add(searchField);
        topPanel.add(searchButton);

        // Center Panel: File List
        fileListModel = new DefaultListModel<>();
        JList<String> fileList = new JList<>(fileListModel);
        add(new JScrollPane(fileList), BorderLayout.CENTER);

        // Bottom Panel: Download Button
        JButton downloadButton = new JButton("Download Selected");
        downloadButton.addActionListener(e -> {
            String selected = fileList.getSelectedValue();
            if (selected != null) {
                // Triggering parallel download logic
                downloadManager.downloadChunk("127.0.0.1", 5001, "example_hash", "./downloads/");
                JOptionPane.showMessageDialog(this, "Download Started for: " + selected);
            }
        });

        add(topPanel, BorderLayout.NORTH);
        add(downloadButton, BorderLayout.SOUTH);

        setVisible(true);
    }

    public static void main(String[] args) {
        // Run UI on the Event Dispatch Thread
        SwingUtilities.invokeLater(Dashboard::new);
    }
}