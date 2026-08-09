package com.alphagraph.reference.securitymaster;

import com.alphagraph.common.etl.Normalizer;
import com.alphagraph.reference.api.SecurityMasterEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.UUID;

/**
 * NSE's listing dates are upper-case ("23-AUG-1995"), unlike bhavdata's title-case dates
 * ("24-Jul-2026") - {@code parseCaseInsensitive()} handles both without needing two formatters.
 */
@Component
public class SecurityMasterNormalizer implements Normalizer<RawSecurityMasterRow, SecurityMasterEntry> {

    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd-MMM-yyyy")
        .toFormatter(Locale.ENGLISH);

    @Override
    public SecurityMasterEntry normalize(RawSecurityMasterRow raw) {
        LocalDate listingDate = raw.listingDate() == null || raw.listingDate().isBlank()
            ? null
            : LocalDate.parse(raw.listingDate(), DATE_FORMAT);

        BigDecimal faceValue = raw.faceValue() == null || raw.faceValue().isBlank()
            ? null
            : new BigDecimal(raw.faceValue());

        return new SecurityMasterEntry(UUID.randomUUID(), raw.symbol(), raw.companyName(), raw.isin(), listingDate, faceValue);
    }
}
