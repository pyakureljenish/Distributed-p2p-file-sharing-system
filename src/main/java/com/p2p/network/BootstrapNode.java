package com.p2p.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class BootstrapNode {
    private static final int PORT = 5000;
    private static final Set<String> activePeers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void main(String[] args) {
        System.out.println("Bootstrap Node starting on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Handle each peer connection in a separate thread
                new Thread(() -> handlePeer(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Error: Could not listen on port " + PORT);
            e.printStackTrace();
        }
    }

    private static void handlePeer(Socket socket) {
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String peerAddress = socket.getInetAddress().getHostAddress();
            activePeers.add(peerAddress);
            System.out.println("New peer joined: " + peerAddress);

            // Send the list of all active peers back to the new peer
            out.println("PEER_LIST:" + String.join(",", activePeers));

        } catch (IOException e) {
            System.err.println("Error handling peer: " + socket.getInetAddress());
            e.printStackTrace();
        }
    }
}