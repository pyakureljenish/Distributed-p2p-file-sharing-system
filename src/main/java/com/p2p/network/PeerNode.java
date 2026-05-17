package com.p2p.network;

import java.io.*;
import java.net.*;
import java.nio.file.*;

/**
 * Peer Node - Serves and downloads file chunks in the P2P network.
 *
 * Supports files of ANY size by streaming in chunks instead of
 * loading the entire file into memory at once.
 *
 * Chunk folder structure: shared_chunks/<hash>/
 *   chunk_0       → raw file bytes
 *   filename.txt  → original filename with extension (e.g. "video.mp4")
 */
public class PeerNode {

    private final int port;

    private static final String BOOTSTRAP_IP   = "127.0.0.1";
    private static final int    BOOTSTRAP_PORT = 5000;

    private static final String DOWNLOAD_DIR =
            System.getProperty("user.home") + File.separator + "Downloads";
    private static final String SHARED_DIR = "shared_chunks";

    private static final int BUFFER_SIZE = 65536; // 64KB buffer for fast transfer

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public PeerNode(int port) {
        this.port = port;
        ensureDirectoriesExist();
    }

    public int getPort() {
        return port;
    }

    // -----------------------------------------------------------------------
    // Directory setup
    // -----------------------------------------------------------------------

    private void ensureDirectoriesExist() {
        try {
            Files.createDirectories(Paths.get(DOWNLOAD_DIR));
            Files.createDirectories(Paths.get(SHARED_DIR));
        } catch (IOException e) {
            System.err.println("[System] Directory setup failed: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Server (serves chunks to other peers)
    // -----------------------------------------------------------------------

    public void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.setReuseAddress(true); // prevents "Address already in use" on restart
                serverSocket.bind(new InetSocketAddress(port));
                System.out.println("[Server] Peer active and listening on port: " + port);
                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleUpload(clientSocket)).start();
                }
            } catch (IOException e) {
                System.err.println("[Server] Server error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Streams a file chunk to a requesting peer.
     *
     * Protocol:
     *   <- requestedHash (UTF)
     *   -> "CHUNK_TRANSFER_START" (UTF)
     *   -> originalFileName (UTF)
     *   -> fileSize in bytes (long)       ← long supports files up to 9 exabytes
     *   -> file bytes streamed in chunks
     *
     * Uses FileInputStream to stream — never loads full file into RAM.
     */
    private void handleUpload(Socket socket) {
        try (DataInputStream  in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            String requestedHash = in.readUTF();
            System.out.println("[Server] Peer requesting hash: " + requestedHash);

            File hashFolder = new File(SHARED_DIR + File.separator + requestedHash);
            File chunkFile  = new File(hashFolder, "chunk_0");
            File metaFile   = new File(hashFolder, "filename.txt");

            if (chunkFile.exists()) {
                out.writeUTF("CHUNK_TRANSFER_START");

                // Read original filename from metadata
                String originalFileName;
                if (metaFile.exists()) {
                    originalFileName = new String(Files.readAllBytes(metaFile.toPath())).trim();
                } else {
                    originalFileName = requestedHash; // fallback
                }

                out.writeUTF(originalFileName);

                // Send file size as long (supports files larger than 2GB)
                long fileSize = chunkFile.length();
                out.writeLong(fileSize);

                // Stream file bytes in chunks — no OutOfMemoryError for large files
                byte[] buffer = new byte[BUFFER_SIZE];
                long   totalSent = 0;
                try (FileInputStream fis = new FileInputStream(chunkFile);
                     BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE)) {

                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalSent += bytesRead;
                        // Progress log every 10MB
                        if (totalSent % (10 * 1024 * 1024) < BUFFER_SIZE) {
                            System.out.printf("[Server] Sent %.1f MB / %.1f MB%n",
                                    totalSent / 1048576.0, fileSize / 1048576.0);
                        }
                    }
                }

                out.flush();
                System.out.println("[Server] Transfer complete: " + originalFileName
                        + " (" + formatSize(fileSize) + ")");

            } else {
                out.writeUTF("FILE_NOT_FOUND");
                out.flush();
                System.err.println("[Server] No chunk for hash: " + requestedHash);
            }

        } catch (IOException e) {
            System.err.println("[Server] Transfer error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Client (downloads from another peer)
    // -----------------------------------------------------------------------

    /**
     * Downloads a file from a peer by streaming — no full-file RAM load.
     *
     * Protocol:
     *   -> requestedHash (UTF)
     *   <- "CHUNK_TRANSFER_START" (UTF)
     *   <- originalFileName (UTF)
     *   <- fileSize (long)
     *   <- file bytes streamed in chunks
     */
    public void downloadFromPeer(String targetIp, int targetPort, String fileHash) {
        System.out.println("[Client] Connecting to " + targetIp + ":" + targetPort);

        try (Socket           socket = new Socket(targetIp, targetPort);
             DataOutputStream out    = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream  in     = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {

            out.writeUTF(fileHash);
            out.flush();

            String status = in.readUTF();

            if ("CHUNK_TRANSFER_START".equals(status)) {
                String originalFileName = in.readUTF();
                long   fileSize         = in.readLong(); // long — supports any file size

                File downloadFile = new File(DOWNLOAD_DIR, originalFileName);

                System.out.println("[Client] Receiving: " + originalFileName
                        + " (" + formatSize(fileSize) + ")");

                byte[] buffer    = new byte[BUFFER_SIZE];
                long   totalRead = 0;

                try (FileOutputStream     fos = new FileOutputStream(downloadFile);
                     BufferedOutputStream  bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

                    int bytesRead;
                    while (totalRead < fileSize) {
                        int toRead = (int) Math.min(buffer.length, fileSize - totalRead);
                        bytesRead  = in.read(buffer, 0, toRead);
                        if (bytesRead == -1) break;
                        bos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        // Progress log every 10MB
                        if (totalRead % (10 * 1024 * 1024) < BUFFER_SIZE) {
                            System.out.printf("[Client] Downloaded %.1f MB / %.1f MB (%.0f%%)%n",
                                    totalRead / 1048576.0,
                                    fileSize  / 1048576.0,
                                    (totalRead * 100.0) / fileSize);
                        }
                    }
                    bos.flush();
                }

                if (totalRead == fileSize) {
                    System.out.println("[Client] DOWNLOAD SUCCESS!");
                    System.out.println("[Client] Saved: " + downloadFile.getAbsolutePath());
                } else {
                    System.err.println("[Client] WARNING: Expected " + fileSize
                            + " bytes but received " + totalRead);
                }

            } else {
                System.err.println("[Client] Server responded: " + status);
            }

        } catch (IOException e) {
            System.err.println("[Client] Connection error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Bootstrap communication
    // -----------------------------------------------------------------------

    public void registerWithBootstrap(String hash, int peerPort) {
        try (Socket           socket = new Socket(BOOTSTRAP_IP, BOOTSTRAP_PORT);
             DataOutputStream out    = new DataOutputStream(socket.getOutputStream());
             DataInputStream  in     = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("REGISTER");
            out.writeUTF(hash);
            out.writeInt(peerPort);
            out.flush();

            String ack = in.readUTF();
            if ("OK".equals(ack)) {
                System.out.println("[Network] Registered hash: " + hash);
            } else {
                System.err.println("[Network] Unexpected Bootstrap response: " + ack);
            }

        } catch (IOException e) {
            System.err.println("[Network] Bootstrap registration failed: " + e.getMessage());
        }
    }

    public String lookupFromBootstrap(String hash) {
        try (Socket           socket = new Socket(BOOTSTRAP_IP, BOOTSTRAP_PORT);
             DataOutputStream out    = new DataOutputStream(socket.getOutputStream());
             DataInputStream  in     = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("LOOKUP");
            out.writeUTF(hash);
            out.flush();

            String response = in.readUTF();
            if ("FOUND".equals(response)) {
                String ip   = in.readUTF();
                int    port = in.readInt();
                System.out.println("[Network] LOOKUP found: " + ip + ":" + port);
                return ip + ":" + port;
            } else {
                System.out.println("[Network] LOOKUP: hash not found.");
                return null;
            }

        } catch (IOException e) {
            System.err.println("[Network] Bootstrap lookup failed: " + e.getMessage());
            return null;
        }
    }

    public String[] listFromBootstrap() {
        try (Socket           socket = new Socket(BOOTSTRAP_IP, BOOTSTRAP_PORT);
             DataOutputStream out    = new DataOutputStream(socket.getOutputStream());
             DataInputStream  in     = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("LIST");
            out.flush();

            int      count   = in.readInt();
            String[] results = new String[count];
            for (int i = 0; i < count; i++) {
                String hash = in.readUTF();
                String ip   = in.readUTF();
                int    port = in.readInt();
                results[i]  = "Peer: " + ip + ":" + port + " | Hash: " + hash;
            }
            return results;

        } catch (IOException e) {
            System.err.println("[Network] Bootstrap list failed: " + e.getMessage());
            return new String[0];
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String formatSize(long bytes) {
        if (bytes < 1024)                  return bytes + " B";
        if (bytes < 1024 * 1024)           return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)    return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }

    // -----------------------------------------------------------------------
    // Standalone entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        java.util.Scanner sc = new java.util.Scanner(System.in);
        System.out.print("Enter Port for this Peer Node: ");
        int port = sc.nextInt();
        PeerNode node = new PeerNode(port);
        node.startServer();
    }
}