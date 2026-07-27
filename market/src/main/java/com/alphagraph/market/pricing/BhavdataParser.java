package com.alphagraph.market.pricing;

import com.alphagraph.common.etl.Parser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Parses NSE's sec_bhavdata_full format (fields separated by ", " — comma then space) and keeps
 * only SERIES = EQ rows; bonds/gold-bonds/T-bills etc. in the same file aren't equity market
 * data. Column order: SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE,
 * LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY,
 * DELIV_PER.
 */
@Component
public class BhavdataParser implements Parser<List<String>, RawDeliveryRow> {

    @Override
    public List<RawDeliveryRow> parse(List<String> lines) {
        return lines.stream()
            .skip(1)
            .map(line -> line.split(",\\s*", -1))
            .filter(fields -> fields.length >= 15 && "EQ".equals(fields[1].trim()))
            .map(fields -> new RawDeliveryRow(
                fields[0].trim(), fields[1].trim(), fields[2].trim(),
                fields[4].trim(), fields[5].trim(), fields[6].trim(), fields[8].trim(),
                fields[10].trim(), fields[14].trim()
            ))
            .toList();
    }
}
