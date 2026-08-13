package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Parser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Parses NSE's ind_close_all format (Outcome Evidence Enrichment: the market-benchmark price
 * source for {@code learning.outcomes.BenchmarkReturnCalculator}) - plain comma-separated, no
 * space after the comma, unlike the equity bhavdata file's ", " delimiter. Column order: Index
 * Name, Index Date, Open Index Value, High Index Value, Low Index Value, Closing Index Value,
 * Points Change, Change(%), Volume, Turnover (Rs. Cr.), P/E, P/B, Div Yield.
 *
 * <p>Filtered to "Nifty 50" only - the file lists ~148 indices, but only the one configured
 * market benchmark is tracked; every other row is simply not this collector's concern.
 */
@Component
public class IndexBhavdataParser implements Parser<List<String>, RawIndexRow> {

    private static final String TRACKED_INDEX_NAME = "Nifty 50";

    @Override
    public List<RawIndexRow> parse(List<String> lines) {
        return lines.stream()
            .skip(1)
            .map(line -> line.split(",", -1))
            .filter(fields -> fields.length >= 9 && TRACKED_INDEX_NAME.equals(fields[0].trim()))
            .map(fields -> new RawIndexRow(
                fields[0].trim(), fields[1].trim(), fields[2].trim(), fields[3].trim(), fields[4].trim(),
                fields[5].trim(), fields[8].trim()
            ))
            .toList();
    }
}
