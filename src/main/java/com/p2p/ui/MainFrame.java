package com.p2p.ui;

import com.p2p.core.DownloadManager;
import javax.swing.*;
import java.awt.*;

/**
 * Main GUI for the P2P File Sharing System.
 * Handles search input and displays available file chunks for download.
 */
public class MainFrame extends JFrame {

    private JTextField searchBar;
    private JButton btnSearch, btnDownload;
    private JList<String> resultList;
    private DefaultListModel<String> listModel;
    private DownloadManager downloadManager;

    public MainFrame() {
        // Initialize the 'Engine' for the download logic
        this.downloadManager = new DownloadManager();

        // Window Settings
        setTitle("P2P File Sharing System - Distributed Systems Project");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Centers the window

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // 1. Search Panel (North)
        JPanel pnlNorth = new JPanel(new FlowLayout());
        searchBar = new JTextField(35);
        btnSearch = new JButton("Find File");

        pnlNorth.add(new JLabel("Search Hash:"));
        pnlNorth.add(searchBar);
        pnlNorth.add(btnSearch);

        // 2. Results Area (Center)
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Available Peers / File Chunks"));

        // 3. Action Panel (South)
        JPanel pnlSouth = new JPanel();
        btnDownload = new JButton("Download Selected Chunk");
        btnDownload.setBackground(new Color(46, 204, 113)); // Professional Green
        btnDownload.setForeground(Color.WHITE);
        pnlSouth.add(btnDownload);

        // Button Logic
        btnDownload.addActionListener(e -> startDownload());

        // Add panels to frame
        add(pnlNorth, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(pnlSouth, BorderLayout.SOUTH);
    }

    private void startDownload() {
        String selection = resultList.getSelectedValue();
        if (selection == null) {
            JOptionPane.showMessageDialog(this, "Please select a file from the list.");
            return;
        }

        // Call the Parallel Download Manager from com.p2p.core
        // Note: IP and Port would usually come from your DHT lookup logic
        downloadManager.downloadChunk("127.0.0.1", 5001, "FILE_HASH_XYZ", "./downloads/");
        JOptionPane.showMessageDialog(this, "Parallel Download Initiated!");
    }

    public static void main(String[] args) {
        // Run the UI on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });
    }
}