//package com.p2p.network;
//
//import java.io.*;
//import java.net.*;
//import java.util.*;
//import java.util.concurrent.*;
//
//public class BootstrapNode {
//    private static final int PORT = 5000;
//    private static final Set<String> activePeers = Collections.newSetFromMap(new ConcurrentHashMap<>());
//
//    public static void main(String[] args) {
//        System.out.println("Bootstrap Node starting on port " + PORT + "...");
//
//        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
//            while (true) {
//                Socket clientSocket = serverSocket.accept();
//                // Handle each peer connection in a separate thread
//                new Thread(() -> handlePeer(clientSocket)).start();
//            }
//        } catch (IOException e) {
//            System.err.println("Error: Could not listen on port " + PORT);
//            e.printStackTrace();
//        }
//    }
//
//    private static void handlePeer(Socket socket) {
//        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
//
//            String peerAddress = socket.getInetAddress().getHostAddress();
//            activePeers.add(peerAddress);
//            System.out.println("New peer joined: " + peerAddress);
//
//            // Send the list of all active peers back to the new peer
//            out.println("PEER_LIST:" + String.join(",", activePeers));
//
//        } catch (IOException e) {
//            System.err.println("Error handling peer: " + socket.getInetAddress());
//            e.printStackTrace();
//        }
//    }
//}

package com.p2p.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Bootstrap Node - Central registry for the P2P network.
 * Peers register their file hashes here, and other peers can look them up.
 *
 * Protocol (binary, DataInputStream/DataOutputStream):
 *   REGISTER  → hash (UTF), port (int)
 *   LOOKUP    → hash (UTF) → "FOUND" + port (int)  OR  "NOT_FOUND"
 *   LIST      → (none)    → count (int) + [hash (UTF) + port (int)] × count
 */
public class BootstrapNode {

    private static final int PORT = 5000;

    // Maps file hash → peer port  (one peer per hash for simplicity)
    private static final Map<String, Integer> hashToPeerPort = new ConcurrentHashMap<>();

    // Maps file hash → peer IP
    private static final Map<String, String> hashToPeerIp = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("[Bootstrap] Starting on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Bootstrap] Ready. Waiting for peers...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handlePeer(clientSocket)).start();
            }

        } catch (IOException e) {
            System.err.println("[Bootstrap] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handlePeer(Socket socket) {
        String peerIp = socket.getInetAddress().getHostAddress();

        try (DataInputStream in   = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String command = in.readUTF();

            switch (command) {

                case "REGISTER": {
                    String hash    = in.readUTF();
                    int    port    = in.readInt();
                    hashToPeerPort.put(hash, port);
                    hashToPeerIp.put(hash, peerIp);
                    System.out.println("[Bootstrap] REGISTER  hash=" + hash
                            + "  peer=" + peerIp + ":" + port);
                    // Acknowledge so the peer knows it succeeded
                    out.writeUTF("OK");
                    out.flush();
                    break;
                }

                case "LOOKUP": {
                    String  hash = in.readUTF();
                    Integer port = hashToPeerPort.get(hash);
                    String  ip   = hashToPeerIp.get(hash);

                    if (port != null && ip != null) {
                        System.out.println("[Bootstrap] LOOKUP    hash=" + hash
                                + "  -> " + ip + ":" + port);
                        out.writeUTF("FOUND");
                        out.writeUTF(ip);
                        out.writeInt(port);
                    } else {
                        System.out.println("[Bootstrap] LOOKUP    hash=" + hash
                                + "  -> NOT_FOUND");
                        out.writeUTF("NOT_FOUND");
                    }
                    out.flush();
                    break;
                }

                case "LIST": {
                    // Returns every registered hash with its peer address
                    out.writeInt(hashToPeerPort.size());
                    for (Map.Entry<String, Integer> entry : hashToPeerPort.entrySet()) {
                        out.writeUTF(entry.getKey());
                        out.writeUTF(hashToPeerIp.getOrDefault(entry.getKey(), "unknown"));
                        out.writeInt(entry.getValue());
                    }
                    out.flush();
                    System.out.println("[Bootstrap] LIST      sent " + hashToPeerPort.size() + " entries");
                    break;
                }

                default:
                    System.err.println("[Bootstrap] Unknown command: " + command);
            }

        } catch (IOException e) {
            System.err.println("[Bootstrap] Error handling " + peerIp + ": " + e.getMessage());
        }
    }
}