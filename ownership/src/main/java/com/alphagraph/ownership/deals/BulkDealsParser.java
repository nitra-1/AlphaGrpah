package com.alphagraph.ownership.deals;

import com.alphagraph.common.etl.Parser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Column order (after {@link BulkDealsCollector}/{@link HttpBulkDealsCollector} tag and combine
 * the two real feeds): DEAL_TYPE, DATE, SYMBOL, SECURITY_NAME, CLIENT_NAME, BUY_SELL, QUANTITY,
 * PRICE, REMARKS. REMARKS only exists in the real bulk deals file, not block deals, so rows may
 * have 8 or 9 fields.
 */
@Component
public class BulkDealsParser implements Parser<List<String>, RawDealRow> {

    @Override
    public List<RawDealRow> parse(List<String> lines) {
        return lines.stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .map(line -> line.split(",", -1))
            .filter(fields -> fields.length >= 8)
            .map(fields -> new RawDealRow(
                fields[0].trim(), fields[2].trim(), fields[3].trim(), fields[1].trim(), fields[4].trim(),
                fields[5].trim(), fields[6].trim(), fields[7].trim()
            ))
            .toList();
    }
}
