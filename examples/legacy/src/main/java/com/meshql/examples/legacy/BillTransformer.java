package com.meshql.examples.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.legacy.CustomerTransformer.*;

public class BillTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(BillTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> STATUS_MAP = Map.of(
            "O", "open",
            "P", "paid",
            "D", "delinquent",
            "X", "cancelled"
    );

    private final IdResolver idResolver;

    public BillTransformer(IdResolver idResolver) {
        this.idResolver = idResolver;
    }

    @Override
    public ObjectNode transform(JsonNode row) {
        ObjectNode clean = mapper.createObjectNode();

        // Resolve customer_id from legacy acct_id
        String acctId = textOrNull(row, "acct_id");
        String customerId = idResolver.resolveCustomerId(acctId);
        if (customerId != null) {
            clean.put("customer_id", customerId);
        }

        // Keep legacy bill_id for payment FK resolution
        if (row.has("bill_id") && !row.get("bill_id").isNull()) {
            clean.put("legacy_bill_id", row.get("bill_id").asInt());
        }

        clean.put("bill_date", parseDate(textOrNull(row, "bill_dt")));
        clean.put("due_date", parseDate(textOrNull(row, "due_dt")));
        clean.put("period_from", parseDate(textOrNull(row, "bill_prd_from")));
        clean.put("period_to", parseDate(textOrNull(row, "bill_prd_to")));

        // Cents to dollars
        if (row.has("tot_amt") && !row.get("tot_amt").isNull()) {
            clean.put("total_amount", row.get("tot_amt").asInt() / 100.0);
        }

        if (row.has("kwh_used") && !row.get("kwh_used").isNull()) {
            clean.put("kwh_used", row.get("kwh_used").asInt());
        }

        clean.put("status", STATUS_MAP.getOrDefault(textOrNull(row, "stat_cd"), "unknown"));
        clean.put("late", "Y".equals(textOrNull(row, "late_flg")));

        logger.debug("Transformed bill: date={}, amount={}",
                clean.get("bill_date"), clean.get("total_amount"));

        return clean;
    }
}
