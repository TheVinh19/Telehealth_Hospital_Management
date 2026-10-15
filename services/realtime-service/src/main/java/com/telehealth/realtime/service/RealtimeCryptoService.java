package com.telehealth.realtime.service;

import com.telehealth.contracts.crypto.AesGcmCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
public class RealtimeCryptoService {

    private final SecretKey secretKey;

    public RealtimeCryptoService(
            @Value("${realtime.encryption-key:telehealth_realtime_aes_gcm_secret_key_32_bytes!}") String encryptionKey) {
        this.secretKey = AesGcmCrypto.deriveKey(encryptionKey);
    }

    public AesGcmCrypto.EncryptedData encryptText(String plaintext) {
        return AesGcmCrypto.encryptString(plaintext, secretKey);
    }

    public String decryptText(byte[] ciphertext, byte[] nonce) {
        return AesGcmCrypto.decryptString(ciphertext, nonce, secretKey);
    }
}
