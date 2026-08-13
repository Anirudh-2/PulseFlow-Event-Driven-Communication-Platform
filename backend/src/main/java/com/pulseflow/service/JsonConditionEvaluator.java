package com.pulseflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JsonConditionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(JsonConditionEvaluator.class);

    private final JsonLogic jsonLogic = new JsonLogic();
    private final ObjectMapper objectMapper;

    public JsonConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * When {@code jsonLogicRule} is null or empty, returns true. Otherwise evaluates JSON Logic against {@code data}.
     */
    public boolean matches(Map<String, Object> jsonLogicRule, Map<String, Object> data) {
        if (jsonLogicRule == null || jsonLogicRule.isEmpty()) {
            return true;
        }
        try {
            String ruleJson = objectMapper.writeValueAsString(jsonLogicRule);
            Object result = jsonLogic.apply(ruleJson, data);
            return JsonLogic.truthy(result);
        } catch (JsonProcessingException | JsonLogicException e) {
            log.warn("JSON Logic evaluation failed: {}", e.getMessage());
            return false;
        }
    }
}
