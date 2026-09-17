package com.alphagraph.financial.transformation;

import com.alphagraph.common.etl.Parser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the combined per-symbol JSON produced by {@link HttpResultsComparisionCollector} - a flat
 * array of quarter objects, each with {@code symbol} injected by the collector (NSE's own
 * {@code resCmpData} elements don't carry it). Field names verified live against
 * {@code https://www.nseindia.com/api/results-comparision?symbol=X}: {@code re_to_dt} (quarter-end,
 * e.g. "31-DEC-2024"), {@code re_net_sale}/{@code re_net_profit}/{@code re_int_new}/
 * {@code re_oth_inc_new}/{@code re_pro_loss_bef_tax}, all as plain integer-string amounts in Rs
 * Lakh. {@code re_net_sale}/{@code re_int_new}/{@code re_oth_inc_new} only exist on non-bank
 * filers' real responses (a bank's own field set is completely different -
 * {@code re_int_expd}/{@code re_oper_exp}/no {@code re_net_sale} at all) - null here for a bank
 * row is a real, expected absence, not a parse failure.
 */
@Component
class ResultsComparisionParser implements Parser<String, RawResultsComparisionRow> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<RawResultsComparisionRow> parse(String rawJson) {
        var root = readTree(rawJson);

        List<RawResultsComparisionRow> rows = new ArrayList<>();
        for (var node : root) {
            rows.add(new RawResultsComparisionRow(
                textOrNull(node, "symbol"), textOrNull(node, "re_to_dt"), textOrNull(node, "re_net_sale"),
                textOrNull(node, "re_net_profit"), textOrNull(node, "re_int_new"), textOrNull(node, "re_oth_inc_new"),
                textOrNull(node, "re_pro_loss_bef_tax")
            ));
        }
        return rows;
    }

    private JsonNode readTree(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to parse results-comparision JSON", new IOException(e));
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
