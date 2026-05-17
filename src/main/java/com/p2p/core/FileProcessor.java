package com.p2p.core;

import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles file splitting (chunking) and SHA-256 hashing for the P2P system.
 */
public class FileProcessor {
    // Standard 512KB chunk size for P2P transfers
    private static final int CHUNK_SIZE = 1024 * 512;

    /**
     * Processes a file: Splits it into chunks and returns the overall file hash.
     */
    public String processFile(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found at: " + filePath);
        }

        // 1. Generate a unique hash for the entire file (The File ID)
        byte[] fileData = readSmallFile(file);
        String fileHash = generateHash(fileData);

        // 2. Split into chunks for distributed transfer
        List<byte[]> chunks = splitFile(file);

        // 3. Save chunks to a local directory so PeerNodes can upload them
        saveChunksLocally(fileHash, chunks);

        System.out.println("[FileProcessor] Processed: " + file.getName());
        System.out.println("[FileProcessor] Hash ID: " + fileHash);
        System.out.println("[FileProcessor] Total Chunks: " + chunks.size());

        return fileHash;
    }

    private byte[] readSmallFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    private List<byte[]> splitFile(File file) throws IOException {
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
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    private void saveChunksLocally(String fileHash, List<byte[]> chunks) throws IOException {
        // Create a directory specifically for this file's chunks
        File chunkDir = new File("shared_chunks/" + fileHash);
        if (!chunkDir.exists()) {
            chunkDir.mkdirs();
        }

        for (int i = 0; i < chunks.size(); i++) {
            File chunkFile = new File(chunkDir, "chunk_" + i);
            try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
                fos.write(chunks.get(i));
            }
        }
    }

    /**
     * TEST MAIN METHOD:
     * Adding this allows you to run this file directly in IntelliJ to test hashing.
     */
    public static void main(String[] args) {
        FileProcessor processor = new FileProcessor();
        // Change this path to a real file on your Desktop for a quick test
        String testPath = "test_file.txt";

        try {
            File testFile = new File(testPath);
            if (!testFile.exists()) {
                testFile.createNewFile();
                try (FileWriter writer = new FileWriter(testFile)) {
                    writer.write("Sample data for P2P testing.");
                }
            }

            String resultHash = processor.processFile(testFile.getAbsolutePath());
            System.out.println("TEST SUCCESSFUL. Hash generated: " + resultHash);
        } catch (Exception e) {
            System.err.println("TEST FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}