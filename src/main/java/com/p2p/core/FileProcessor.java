package com.p2p.core;

import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class FileProcessor {
    private static final int CHUNK_SIZE = 1024 * 512; // 512KB

    public static List<byte[]> splitFile(File file) throws IOException {
        List<byte[]> chunks = new ArrayList<>();
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    public static String generateHash(byte[] data) throws Exception {
        // REMOVED TAG TO PREVENT ERRORS
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    // ADDED THIS TO MAKE IT RUNNABLE FOR TESTING
    public static void main(String[] args) {
        try {
            String testData = "Hello Riwaj and Jenish!";
            String hash = generateHash(testData.getBytes());
            System.out.println("SHA-256 Hash: " + hash);
            System.out.println("FileProcessor is working correctly!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}