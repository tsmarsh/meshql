package com.meshql.api.restlette;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SwaggerUIHandler {
    private final String apiPath;

    public SwaggerUIHandler(String apiPath) {
        this.apiPath = apiPath;
    }

    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);

        String html = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <title>" + apiPath + " API Documentation</title>\n" +
                "  <link rel=\"stylesheet\" type=\"text/css\" href=\"https://unpkg.com/swagger-ui-dist@5.1.0/swagger-ui.css\">\n"
                +
                "</head>\n" +
                "<body>\n" +
                "  <div id=\"swagger-ui\"></div>\n" +
                "  <script src=\"https://unpkg.com/swagger-ui-dist@5.1.0/swagger-ui-bundle.js\"></script>\n" +
                "  <script>\n" +
                "    window.onload = function() {\n" +
                "      const ui = SwaggerUIBundle({\n" +
                "        url: \"" + apiPath + "/api-docs/swagger.json\",\n" +
                "        dom_id: '#swagger-ui',\n" +
                "        deepLinking: true,\n" +
                "        presets: [\n" +
                "          SwaggerUIBundle.presets.apis\n" +
                "        ],\n" +
                "      });\n" +
                "      window.ui = ui;\n" +
                "    };\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";

        response.getWriter().write(html);
    }
}
