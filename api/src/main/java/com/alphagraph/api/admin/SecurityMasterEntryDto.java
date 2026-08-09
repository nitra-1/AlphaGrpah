package com.alphagraph.api.admin;

import com.alphagraph.reference.api.SecurityMasterEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SecurityMasterEntryDto(
    UUID id, String symbol, String companyName, String isin, LocalDate listingDate, BigDecimal faceValue
) {

    public static SecurityMasterEntryDto from(SecurityMasterEntry entry) {
        return new SecurityMasterEntryDto(
            entry.id(), entry.symbol(), entry.companyName(), entry.isin(), entry.listingDate(), entry.faceValue()
        );
    }
}
