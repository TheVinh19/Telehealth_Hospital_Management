package com.telehealth.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class EmailRecipientResolver {
    private static final Set<UUID> DEMO_PATIENTS = Set.of(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            UUID.fromString("44444444-4444-4444-4444-444444444444"));
    private final String demoRecipient;

    public EmailRecipientResolver(@Value("${notifications.demo-recipient:}") String demoRecipient) {
        this.demoRecipient = demoRecipient.strip();
    }

    public String resolve(UUID patientId, String accountEmail) {
        // Match the two dedicated demo identities, never all @demo.com accounts.
        if (patientId != null && DEMO_PATIENTS.contains(patientId) && !demoRecipient.isBlank()) {
            return demoRecipient;
        }
        return accountEmail;
    }

    public String getDemoRecipient() {
        return demoRecipient;
    }
}
