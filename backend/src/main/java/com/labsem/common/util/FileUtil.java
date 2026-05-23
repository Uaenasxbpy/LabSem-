package com.labsem.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileUtil {

    private FileUtil() {
    }

    /**
     * Compute SHA-256 hex digest of the given bytes.
     */
    public static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Slugify a filename by replacing illegal characters.
     * Replaces \/:*?"<>| with _, replaces whitespace with _, falls back to "unknown".
     */
    public static String slugify(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        String result = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        result = result.replaceAll("\\s+", "_");
        return result.isEmpty() ? "unknown" : result;
    }

    /**
     * Rename a file for storage based on its type.
     * PDF → paper_N.ext, PPT → report_slides.ext, others → keep original.
     */
    public static String renameForStorage(String fileType, int index, String originalName) {
        if (fileType == null || originalName == null) {
            return originalName;
        }
        String ext = getExtension(originalName);
        return switch (fileType.toUpperCase()) {
            case "PDF" -> "paper_" + index + "." + ext;
            case "PPT", "PPTX" -> "report_slides." + ext;
            default -> originalName;
        };
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
