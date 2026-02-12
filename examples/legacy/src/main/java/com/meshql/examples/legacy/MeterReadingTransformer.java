package com.meshql.examples.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.meshql.examples.legacy.CustomerTransformer.*;

public class MeterReadingTransformer implements LegacyTransformer {
    private static final Logger logger = LoggerFactory.getLogger(MeterReadingTransformer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> TYPE_MAP = Map.of(
            "A", "actual",
            "E", "estimated",
            "C", "customer"
    );

    private final IdResolver idResolver;

    public MeterReadingTransformer(IdResolver idResolver) {
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

        clean.put("meter_number", textOrNull(row, "mtr_num"));
        clean.put("reading_date", parseDate(textOrNull(row, "rdng_dt")));

        int readingVal = row.has("rdng_val") ? row.get("rdng_val").asInt() : 0;
        clean.put("reading_value", readingVal);

        if (row.has("prev_rdng_val") && !row.get("prev_rdng_val").isNull()) {
            int prevVal = row.get("prev_rdng_val").asInt();
            clean.put("previous_value", prevVal);
            clean.put("kwh_used", readingVal - prevVal);
        }

        clean.put("reading_type", TYPE_MAP.getOrDefault(textOrNull(row, "rdng_type"), "unknown"));
        clean.put("estimated", "Y".equals(textOrNull(row, "est_flg")));
        clean.put("billed", "Y".equals(textOrNull(row, "billed_flg")));

        logger.debug("Transformed meter reading: meter={}, date={}",
                clean.get("meter_number"), clean.get("reading_date"));

        return clean;
    }
}
