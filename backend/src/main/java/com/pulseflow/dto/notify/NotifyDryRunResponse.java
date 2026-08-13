package com.pulseflow.dto.notify;

import java.util.List;
import java.util.Map;

public record NotifyDryRunResponse(
        String status,
        List<Map<String, Object>> matchedRules,
        Map<String, Map<String, TemplatePreview>> rendered) {
    public record TemplatePreview(String subject, String body) {}
}
