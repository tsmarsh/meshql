package com.meshql.mesher.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseParserTest {

    @Test
    void extractJsonFromCodeBlock() {
        String response = """
                Here is the domain model:

                ```json
                {"projectName": "test"}
                ```

                This defines the model.
                """;

        String json = ResponseParser.extractJson(response);
        assertEquals("{\"projectName\": \"test\"}", json);
    }

    @Test
    void extractJsonFromGenericCodeBlock() {
        String response = """
                ```
                {"projectName": "test"}
                ```
                """;

        String json = ResponseParser.extractJson(response);
        assertEquals("{\"projectName\": \"test\"}", json);
    }

    @Test
    void extractJsonFromRawResponse() {
        String response = "{\"projectName\": \"test\"}";
        String json = ResponseParser.extractJson(response);
        assertEquals("{\"projectName\": \"test\"}", json);
    }

    @Test
    void extractJsonWithSurroundingText() {
        String response = "Sure, here is the JSON: {\"projectName\": \"test\"} Hope that helps!";
        String json = ResponseParser.extractJson(response);
        assertEquals("{\"projectName\": \"test\"}", json);
    }

    @Test
    void emptyResponseThrows() {
        assertThrows(IllegalArgumentException.class, () -> ResponseParser.extractJson(""));
        assertThrows(IllegalArgumentException.class, () -> ResponseParser.extractJson(null));
    }
}
