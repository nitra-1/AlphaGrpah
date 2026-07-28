package com.alphagraph.corporate.actions;

import com.alphagraph.common.etl.Parser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Column order: SYMBOL, ACTION_TYPE, ANNOUNCEMENT_DATE, EX_DATE, RECORD_DATE, DIVIDEND_AMOUNT,
 * RATIO_NUMERATOR, RATIO_DENOMINATOR, PRICE.
 */
@Component
public class CorporateActionsParser implements Parser<List<String>, RawCorporateActionRow> {

    @Override
    public List<RawCorporateActionRow> parse(List<String> lines) {
        return lines.stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .map(line -> line.split(",", -1))
            .filter(fields -> fields.length >= 9)
            .map(fields -> new RawCorporateActionRow(
                fields[0].trim(), fields[1].trim(), blankToNull(fields[2]), fields[3].trim(),
                blankToNull(fields[4]), blankToNull(fields[5]), blankToNull(fields[6]),
                blankToNull(fields[7]), blankToNull(fields[8])
            ))
            .toList();
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
