package com.p2p.network;

import java.io.*;
import java.net.*;

public class PeerNode {
    private final int port;

    public PeerNode(int port) {
        this.port = port;
    }

    /**
     * Entry point to simulate a Peer.
     */
    public static void main(String[] args) throws InterruptedException {
        // 1. Start a server on port 8080
        PeerNode myNode = new PeerNode(8080);
        myNode.startServer();

        System.out.println("Peer server started on port 8080...");

        // 2. Small delay to ensure server is up, then simulate a client connection to itself
        Thread.sleep(1000);
        System.out.println("Attempting a test connection to localhost:8080...");
        myNode.connectToPeer("localhost", 8080);
    }

    /**
     * Starts a background thread to listen for incoming file requests.
     */
    public void startServer() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                while (!Thread.currentThread().isInterrupted()) {
                    Socket client = server.accept();
                    System.out.println("\n[Server] New connection from: " + client.getInetAddress());
                    new Thread(() -> handleUpload(client)).start();
                }
            } catch (IOException e) {
                System.err.println("[Server] Error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Simulates sending a file chunk to a requesting peer.
     */
    private void handleUpload(Socket socket) {
        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            // Simulated handshake
            out.writeUTF("CHUNK_TRANSFER_START");
            out.flush();
            System.out.println("[Server] Sent handshake to peer.");
        } catch (IOException e) {
            System.err.println("[Server] Upload failed: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * A helper method to test the connection (Client side).
     */
    public void connectToPeer(String host, int targetPort) {
        try (Socket socket = new Socket(host, targetPort);
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            String response = in.readUTF();
            System.out.println("[Client] Received from server: " + response);

        } catch (IOException e) {
            System.err.println("[Client] Could not connect: " + e.getMessage());
        }
    }
}