package com.telehealth.clinical.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class ClinicalStorageService {

    private final Path storageDirectory;

    public ClinicalStorageService(@Value("${clinical.storage.directory:storage/clinical-reports}") String dirPath) {
        this.storageDirectory = Paths.get(dirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory", e);
        }
    }

    public void saveFile(String storageKey, byte[] content) {
        try {
            Path target = storageDirectory.resolve(storageKey).normalize();
            if (!target.startsWith(storageDirectory)) {
                throw new SecurityException("Invalid storage key path");
            }
            Files.write(target, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save report file", e);
        }
    }

    public byte[] readFile(String storageKey) {
        try {
            Path target = storageDirectory.resolve(storageKey).normalize();
            if (!target.startsWith(storageDirectory) || !Files.exists(target)) {
                throw new IllegalArgumentException("File không tồn tại trên bộ lưu trữ");
            }
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read report file", e);
        }
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }
}
