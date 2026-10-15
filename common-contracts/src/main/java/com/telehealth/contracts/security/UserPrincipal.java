package com.telehealth.contracts.security;

import com.telehealth.contracts.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {
    private UUID userId;
    private String email;
    private String displayName;
    private Role role;
}
