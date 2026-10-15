package com.telehealth.contracts.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class AesGcmCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int NONCE_LENGTH_BYTES = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCrypto() {
    }

    public record EncryptedData(byte[] ciphertext, byte[] nonce) {}

    public static SecretKey deriveKey(String secretKeyString) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(secretKeyString.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES-256 key", e);
        }
    }

    public static EncryptedData encrypt(byte[] plaintext, SecretKey key) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] ciphertext = cipher.doFinal(plaintext);
            return new EncryptedData(ciphertext, nonce);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static byte[] decrypt(byte[] ciphertext, byte[] nonce, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public static EncryptedData encryptString(String text, SecretKey key) {
        return encrypt(text.getBytes(StandardCharsets.UTF_8), key);
    }

    public static String decryptString(byte[] ciphertext, byte[] nonce, SecretKey key) {
        return new String(decrypt(ciphertext, nonce, key), StandardCharsets.UTF_8);
    }
}
