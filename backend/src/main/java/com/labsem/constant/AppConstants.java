package com.labsem.constant;

public class AppConstants {

    private AppConstants() {
    }

    /** Maximum total email attachment size: 45 MB */
    public static final long MAX_EMAIL_ATTACHMENT_SIZE = 45 * 1024 * 1024;

    /** Title similarity threshold for duplicate detection (0.0 - 1.0) */
    public static final double TITLE_SIMILARITY_THRESHOLD = 0.9;
}
