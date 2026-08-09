package com.alphagraph.api.admin;

import jakarta.validation.constraints.NotBlank;

/** {@code symbol} must be picked from the security-master search results, never typed freely - see SecurityMasterSearchController. */
public record CreateInstrumentRequest(@NotBlank String symbol, @NotBlank String sectorName) {
}
