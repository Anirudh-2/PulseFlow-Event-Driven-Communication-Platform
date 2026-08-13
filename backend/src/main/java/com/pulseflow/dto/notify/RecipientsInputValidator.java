package com.pulseflow.dto.notify;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RecipientsInputValidator implements ConstraintValidator<ValidRecipients, RecipientsInput> {
    @Override
    public boolean isValid(RecipientsInput value, ConstraintValidatorContext context) {
        if (value == null || value.mode() == null) {
            return true;
        }
        String mode = value.mode().trim().toUpperCase();
        if ("DIRECT".equals(mode) && isBlank(value.userId())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("recipients.userId is required when recipients.mode is DIRECT")
                    .addPropertyNode("userId")
                    .addConstraintViolation();
            return false;
        }
        if ("ROLE_BASED".equals(mode) && isBlank(value.roleName())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("recipients.roleName is required when recipients.mode is ROLE_BASED")
                    .addPropertyNode("roleName")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
