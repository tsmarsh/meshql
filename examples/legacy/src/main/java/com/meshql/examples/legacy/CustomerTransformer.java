package com.meshql.examples.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class CustomerTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(CustomerTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> STATUS_MAP = Map.of(
            "A", "active",
            "S", "suspended",
            "C", "closed"
    );

    private static final Map<String, String> RATE_MAP = Map.of(
            "RES", "residential",
            "COM", "commercial",
            "IND", "industrial"
    );

    private static final Map<String, String> SERVICE_MAP = Map.of(
            "E", "electric",
            "G", "gas",
            "B", "both"
    );

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        // Preserve legacy acct_id for FK resolution after GraphQL population
        if (row.has("acct_id") && !row.get("acct_id").isNull()) {
            clean.put("legacy_acct_id", row.get("acct_id").asInt());
        }
        clean.put("account_number", textOrNull(row, "acct_num"));
        clean.put("first_name", titleCase(textOrNull(row, "cust_nm_first")));
        clean.put("last_name", titleCase(textOrNull(row, "cust_nm_last")));

        String addr1 = titleCase(textOrNull(row, "addr_ln1"));
        String addr2 = textOrNull(row, "addr_ln2");
        if (addr2 != null && !addr2.isEmpty()) {
            clean.put("address", addr1 + ", " + titleCase(addr2));
        } else if (addr1 != null) {
            clean.put("address", addr1);
        }

        putIfPresent(clean, "city", titleCase(textOrNull(row, "addr_city")));
        putIfPresent(clean, "state", textOrNull(row, "addr_st"));
        putIfPresent(clean, "zip", textOrNull(row, "addr_zip"));
        putIfPresent(clean, "phone", textOrNull(row, "phone_num"));
        putIfPresent(clean, "email", toLower(textOrNull(row, "email_addr")));

        clean.put("rate_class", RATE_MAP.getOrDefault(textOrNull(row, "rt_cd"), "unknown"));
        clean.put("service_type", SERVICE_MAP.getOrDefault(textOrNull(row, "svc_type"), "unknown"));
        clean.put("status", STATUS_MAP.getOrDefault(textOrNull(row, "stat_cd"), "unknown"));

        putIfPresent(clean, "connected_date", parseDate(textOrNull(row, "conn_dt")));
        putIfPresent(clean, "disconnected_date", parseDate(textOrNull(row, "disc_dt")));

        clean.put("budget_billing", "Y".equals(textOrNull(row, "budget_flg")));
        clean.put("paperless", "Y".equals(textOrNull(row, "paperless_flg")));

        logger.debug("Transformed customer: {} {} ({})",
                clean.get("first_name"), clean.get("last_name"), clean.get("account_number"));

        return clean;
    }

    static String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : s.toLowerCase().toCharArray()) {
            if (c == ' ' || c == '-' || c == '\'') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    static String parseDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) return null;
        return yyyymmdd.substring(0, 4) + "-" + yyyymmdd.substring(4, 6) + "-" + yyyymmdd.substring(6, 8);
    }

    static String textOrNull(JsonNode row, String field) {
        if (row == null || !row.has(field) || row.get(field).isNull()) return null;
        return row.get(field).asText();
    }

    private static String toLower(String s) {
        return s == null ? null : s.toLowerCase();
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isEmpty()) {
            node.put(field, value);
        }
    }
}
