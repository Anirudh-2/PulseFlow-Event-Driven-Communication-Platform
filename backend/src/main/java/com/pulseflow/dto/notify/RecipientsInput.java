package com.pulseflow.dto.notify;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

@ValidRecipients
public record RecipientsInput(
        @NotBlank(message = "recipients.mode must not be blank")
        @Pattern(
                regexp = "DIRECT|ROLE_BASED|BROADCAST",
                message = "recipients.mode must be one of: DIRECT, ROLE_BASED, BROADCAST")
        String mode,
        String userId,
        String roleName,
        String email,
        String aadObjectId,
        @Valid List<UserRecipientInput> users) {}
