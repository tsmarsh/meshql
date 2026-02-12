package com.meshql.examples.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.legacy.CustomerTransformer.*;

public class PaymentTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(PaymentTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> METHOD_MAP = Map.of(
            "W", "web",
            "E", "electronic",
            "K", "check",
            "C", "cash",
            "P", "phone"
    );

    private static final Map<String, String> STATUS_MAP = Map.of(
            "S", "success",
            "F", "failed",
            "R", "refunded"
    );

    private final IdResolver idResolver;

    public PaymentTransformer(IdResolver idResolver) {
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

        // Resolve bill_id from legacy bill_id
        String legacyBillId = textOrNull(row, "bill_id");
        if (legacyBillId != null) {
            clean.put("legacy_bill_id", Integer.parseInt(legacyBillId));
            String billId = idResolver.resolveBillId(legacyBillId);
            if (billId != null) {
                clean.put("bill_id", billId);
            }
        }

        clean.put("payment_date", parseDate(textOrNull(row, "pymt_dt")));

        // Cents to dollars
        if (row.has("pymt_amt") && !row.get("pymt_amt").isNull()) {
            clean.put("amount", row.get("pymt_amt").asInt() / 100.0);
        }

        clean.put("method", METHOD_MAP.getOrDefault(textOrNull(row, "pymt_mthd"), "unknown"));
        putIfPresent(clean, "confirmation_number", textOrNull(row, "conf_num"));
        clean.put("status", STATUS_MAP.getOrDefault(textOrNull(row, "stat_cd"), "unknown"));

        logger.debug("Transformed payment: date={}, amount={}, method={}",
                clean.get("payment_date"), clean.get("amount"), clean.get("method"));

        return clean;
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isEmpty()) {
            node.put(field, value);
        }
    }
}
