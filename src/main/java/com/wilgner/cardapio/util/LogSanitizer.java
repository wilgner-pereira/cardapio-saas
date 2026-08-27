package com.wilgner.cardapio.util;

public class LogSanitizer {

    private LogSanitizer() {
        // Prevent instantiation
    }

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[\r\n]", "_");
    }
}
