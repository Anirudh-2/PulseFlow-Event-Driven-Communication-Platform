package com.pulseflow.dto.notify;

import jakarta.validation.constraints.NotBlank;

public record UserRecipientInput(
        @NotBlank String userId,
        @NotBlank String email,
        String aadObjectId) {}
