package com.labsem.common.util;

import java.text.Normalizer;

public class TitleNormalizer {

    private TitleNormalizer() {
    }

    /**
     * Normalize a paper title for comparison purposes.
     * Steps: NFKC normalize → lowercase → trim → remove all whitespace/punctuation/special chars.
     */
    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        // NFKC normalization
        String result = Normalizer.normalize(title, Normalizer.Form.NFKC);
        // Lowercase
        result = result.toLowerCase();
        // Trim
        result = result.trim();
        // Remove all whitespace, punctuation, and special characters
        result = result.replaceAll("[\\s\\p{Punct}]", "");
        return result;
    }
}
