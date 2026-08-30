package com.alphagraph.ownership.interpretation;

/**
 * Ordinal wrapper around Sprint 2's plain-String {@code deal_materiality.materiality_level} (LOW/
 * MEDIUM/HIGH/VERY_HIGH) - Sprint 2 deliberately doesn't use an enum for it (see
 * {@code ownership.deals.DealMaterialityEngine}), but Sprint 3's decision ladders need to compare
 * levels ordinally ("at least MEDIUM"), so this parses the string at the Sprint 2/3 boundary
 * rather than scattering string comparisons through the new engines.
 */
enum MaterialityLevel {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH;

    boolean atLeast(MaterialityLevel other) {
        return this.ordinal() >= other.ordinal();
    }

    static MaterialityLevel fromString(String value) {
        if (value == null) {
            return LOW;
        }
        try {
            return MaterialityLevel.valueOf(value);
        } catch (IllegalArgumentException e) {
            return LOW;
        }
    }
}
