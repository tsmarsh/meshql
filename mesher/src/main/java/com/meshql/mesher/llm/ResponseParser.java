package com.meshql.mesher.llm;

/**
 * Extracts JSON from LLM responses that may include markdown code blocks
 * or explanatory text. Pattern matches veriforged/src/llm/response.rs.
 */
public final class ResponseParser {
    private ResponseParser() {}

    public static String extractJson(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Response is empty");
        }

        // Try to find json code block first
        int jsonBlockStart = response.indexOf("```json");
        if (jsonBlockStart >= 0) {
            int contentStart = jsonBlockStart + 7;
            int end = response.indexOf("```", contentStart);
            if (end > contentStart) {
                return response.substring(contentStart, end).trim();
            }
        }

        // Try generic code block
        int blockStart = response.indexOf("```");
        if (blockStart >= 0) {
            int afterStart = blockStart + 3;
            // Skip the language identifier line if present
            int newline = response.indexOf('\n', afterStart);
            int contentStart = (newline >= 0) ? newline + 1 : afterStart;
            int end = response.indexOf("```", contentStart);
            if (end > contentStart) {
                return response.substring(contentStart, end).trim();
            }
        }

        // Try to find raw JSON (starts with { and ends with })
        int braceStart = response.indexOf('{');
        int braceEnd = response.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return response.substring(braceStart, braceEnd + 1).trim();
        }

        return response.trim();
    }
}
