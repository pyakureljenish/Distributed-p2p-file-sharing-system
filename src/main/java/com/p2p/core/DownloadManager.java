package com.p2p.core;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadManager {
    // Thread pool to handle 4 parallel downloads at a time
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /**
     * Downloads a specific file chunk from a peer.
     */
    public void downloadChunk(String peerIp, int port, String chunkHash, String savePath) {
        executor.execute(() -> {
            try (Socket socket = new Socket(peerIp, port);
                 DataInputStream in = new DataInputStream(socket.getInputStream());
                 FileOutputStream fos = new FileOutputStream(savePath + "_" + chunkHash)) {

                System.out.println("Connecting to " + peerIp + " for chunk: " + chunkHash);

                // Request chunk (Logic to be finalized in protocol)
                // For now, we read the incoming bytes
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }

                System.out.println("Successfully downloaded chunk: " + chunkHash);

            } catch (IOException e) {
                System.err.println("Failed to download from " + peerIp + ": " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}