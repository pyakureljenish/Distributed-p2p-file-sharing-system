package com.p2p.core;

import java.math.BigInteger;
import java.util.Arrays; // Added for sample data
import java.util.List;

/**
 * Manages Distributed Hash Table (DHT) operations using Kademlia metrics.
 */
public final class DHTManager {

    private static final int HEX_RADIX = 16;

    private DHTManager() {
        // Utility class - prevent instantiation
    }

    /**
     * Entry point to run and test the logic.
     */
    public static void main(String[] args) {
        // Sample data: Hexadecimal IDs
        String fileHash = "f0a1";
        List<String> activePeers = Arrays.asList("f0a2", "e1b2", "f0a0", "abcd");

        System.out.println("--- Kademlia DHT Test ---");
        System.out.println("Target File Hash: " + fileHash);

        String closest = findClosestPeer(fileHash, activePeers);

        System.out.println("Closest Peer ID:  " + closest);
        if (closest != null) {
            System.out.println("XOR Distance:    " + calculateDistance(fileHash, closest));
        }
        System.out.println("-------------------------");
    }

    /**
     * Calculates Kademlia distance: d(x, y) = x XOR y.
     */
    public static BigInteger calculateDistance(String id1, String id2) {
        BigInteger b1 = new BigInteger(id1, HEX_RADIX);
        BigInteger b2 = new BigInteger(id2, HEX_RADIX);
        return b1.xor(b2);
    }

    /**
     * Finds the peer ID with the shortest XOR distance to the target file hash.
     */
    public static String findClosestPeer(String targetHash, List<String> peerIds) {
        if (peerIds == null || peerIds.isEmpty()) {
            return null;
        }

        String closestPeer = null;
        BigInteger minDistance = null;

        for (String currentId : peerIds) {
            try {
                BigInteger currentDistance = calculateDistance(targetHash, currentId);

                if (minDistance == null || currentDistance.compareTo(minDistance) < 0) {
                    minDistance = currentDistance;
                    closestPeer = currentId;
                }
            } catch (NumberFormatException e) {
                System.err.println("Skipping invalid Hex ID: " + currentId);
            }
        }

        return closestPeer;
    }
}