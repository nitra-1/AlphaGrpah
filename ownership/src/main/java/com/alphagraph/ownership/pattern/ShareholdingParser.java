package com.alphagraph.ownership.pattern;

import com.alphagraph.common.etl.Parser;
import org.springframework.stereotype.Component;

import java.util.List;

/** Column order: SYMBOL, PERIOD_END, PROMOTER_PCT, FII_PCT, DII_PCT, MF_PCT, PUBLIC_PCT. */
@Component
public class ShareholdingParser implements Parser<List<String>, RawShareholdingRow> {

    @Override
    public List<RawShareholdingRow> parse(List<String> lines) {
        return lines.stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .map(line -> line.split(",", -1))
            .filter(fields -> fields.length >= 7)
            .map(fields -> new RawShareholdingRow(
                fields[0].trim(), fields[1].trim(), fields[2].trim(), fields[3].trim(),
                fields[4].trim(), blankToNull(fields[5]), blankToNull(fields[6])
            ))
            .toList();
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
