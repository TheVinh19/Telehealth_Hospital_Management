package com.telehealth.clinical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telehealth.clinical.dto.IntakePayload;
import com.telehealth.contracts.crypto.AesGcmCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
public class ClinicalCryptoService {

    private final SecretKey secretKey;
    private final ObjectMapper objectMapper;

    public ClinicalCryptoService(
            @Value("${clinical.encryption-key:telehealth_clinical_aes_gcm_secret_key_32_bytes!}") String encryptionKey,
            ObjectMapper objectMapper) {
        this.secretKey = AesGcmCrypto.deriveKey(encryptionKey);
        this.objectMapper = objectMapper;
    }

    public AesGcmCrypto.EncryptedData encryptPayload(IntakePayload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return AesGcmCrypto.encryptString(json, secretKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt intake payload", e);
        }
    }

    public IntakePayload decryptPayload(byte[] ciphertext, byte[] nonce) {
        try {
            String json = AesGcmCrypto.decryptString(ciphertext, nonce, secretKey);
            return objectMapper.readValue(json, IntakePayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt intake payload", e);
        }
    }

    public AesGcmCrypto.EncryptedData encryptBytes(byte[] rawBytes) {
        return AesGcmCrypto.encrypt(rawBytes, secretKey);
    }

    public byte[] decryptBytes(byte[] ciphertext, byte[] nonce) {
        return AesGcmCrypto.decrypt(ciphertext, nonce, secretKey);
    }
}
