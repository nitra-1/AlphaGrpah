package com.alphagraph.corporate.actions;

import com.alphagraph.common.etl.Parser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses NSE's real corporate-actions JSON array (verified live against
 * {@code https://www.nseindia.com/api/corporates-corporateActions} - field names: symbol, subject,
 * exDate, recDate, caBroadcastDate; several date-ish fields NSE doesn't use for a given row come
 * back as the literal string {@code "-"} rather than JSON null, normalized to {@code null} here).
 * Uses {@link JsonNode} rather than a fully-typed POJO for the same reason
 * {@code corporate.documents.AnnouncementsParser} does - the real feed carries several more fields
 * (bcStartDate, bcEndDate, faceVal, ind, isin, series, ...) this system doesn't need.
 */
@Component
public class CorporateActionsParser implements Parser<String, RawCorporateActionRow> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<RawCorporateActionRow> parse(String rawJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to parse corporate actions JSON", new IOException(e));
        }

        List<RawCorporateActionRow> rows = new ArrayList<>();
        for (JsonNode node : root) {
            rows.add(new RawCorporateActionRow(
                textOrNull(node, "symbol"), textOrNull(node, "subject"), textOrNull(node, "exDate"),
                dashToNull(textOrNull(node, "recDate")), dashToNull(textOrNull(node, "caBroadcastDate"))
            ));
        }
        return rows;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String dashToNull(String value) {
        return value == null || value.equals("-") ? null : value;
    }
}
