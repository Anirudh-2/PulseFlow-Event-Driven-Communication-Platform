package com.pulseflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Applies a simple field map: targetKey → dot-path into the source tree (maps only).
 */
@Service
public class PayloadMappingService {

    @SuppressWarnings("unchecked")
    public Map<String, Object> applyMapping(Map<String, Object> rawPayload, Map<String, Object> mappingSpec) {
        if (mappingSpec == null || mappingSpec.isEmpty()) {
            return rawPayload == null ? Map.of() : new LinkedHashMap<>(rawPayload);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : mappingSpec.entrySet()) {
            String targetKey = e.getKey();
            Object spec = e.getValue();
            if (spec == null) {
                continue;
            }
            if ("$payload".equals(spec) || "payload".equals(spec)) {
                out.put(targetKey, rawPayload);
                continue;
            }
            if (spec instanceof String path) {
                out.put(targetKey, readPath(rawPayload, path));
            } else {
                out.put(targetKey, spec);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object readPath(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.split("\\.");
        Object cur = root;
        for (String p : parts) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(p);
        }
        return cur;
    }
}
