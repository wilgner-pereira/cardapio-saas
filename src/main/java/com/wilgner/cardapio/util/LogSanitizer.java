package com.wilgner.cardapio.util;

public class LogSanitizer {

    public static String route(String path) {
        if (path != null) {
            for (String group : new String[]{"auth", "painel", "plataforma", "public", "setup", "actuator"}) {
                if (path.startsWith("/" + group + "/")) return "/" + group + "/**";
            }
        }
        return "other";
    }

    public static String method(String method) {
        return java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
                .contains(method == null ? "" : method) ? method : "OTHER";
    }

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
