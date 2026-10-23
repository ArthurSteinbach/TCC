package br.com.unisinos.despensa.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class Tokens {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Tokens() {
    }

    public static String novo() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        return HexFormat.of().formatHex(sha256(token == null ? "" : token));
    }

    public static boolean iguais(String esperado, String recebido) {
        if (esperado == null || esperado.isBlank() || recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(esperado), sha256(recebido));
    }

    private static byte[] sha256(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
