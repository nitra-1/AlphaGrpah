package com.alphagraph.reference.securitymaster;

import com.alphagraph.common.etl.Parser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Parses NSE's real EQUITY_L.csv format (plain comma-separated, inconsistent spacing per field -
 * some header/data fields have a leading space after the comma, some don't, so every field is
 * trimmed rather than relying on a fixed delimiter pattern). Column order: SYMBOL, NAME OF
 * COMPANY, SERIES, DATE OF LISTING, PAID UP VALUE, MARKET LOT, ISIN NUMBER, FACE VALUE. Keeps
 * only SERIES = EQ - the same file also lists debt instruments, ETFs, and other non-equity
 * series under other SERIES values, none of which belong in an "add a trackable stock" lookup.
 */
@Component
public class SecurityMasterParser implements Parser<List<String>, RawSecurityMasterRow> {

    @Override
    public List<RawSecurityMasterRow> parse(List<String> lines) {
        return lines.stream()
            .skip(1)
            .map(line -> line.split(",", -1))
            .filter(fields -> fields.length >= 8 && "EQ".equals(fields[2].trim()))
            .map(fields -> new RawSecurityMasterRow(
                fields[0].trim(), fields[1].trim(), fields[2].trim(),
                fields[3].trim(), fields[7].trim(), fields[6].trim()
            ))
            .toList();
    }
}
