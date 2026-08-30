package com.alphagraph.ownership.interpretation;

import java.util.regex.Pattern;

/**
 * Pure, deterministic classification of a resolved participant's normalized name (see
 * {@code ownership.deals.DiscoveredDealWriter#normalizeClientName} - uppercase, trimmed,
 * punctuation-stripped, whitespace-collapsed) into a {@link ParticipantType} - ordered keyword/
 * pattern rules, first match wins, each with a fixed confidence disclosing how reliable that
 * particular signal actually is. No ML, no fuzzy matching - {@link ParticipantType#UNKNOWN} (0) is
 * a legitimate, expected outcome when nothing confidently matches, not a bug.
 *
 * <p>Short acronym tokens (AIF, FPI, FII, LTD, LLP, INC) are matched on real word boundaries
 * (regex {@code \b}) so they can't false-positive inside an unrelated longer word; longer
 * unambiguous phrases ("MUTUAL FUND", "INSURANCE") are matched by plain substring.
 */
final class ParticipantClassifier {

    private static final String SOURCE = "NAME_PATTERN_V1";

    private static final Pattern AIF_WORD = wordBoundary("AIF");
    private static final Pattern FPI_WORD = wordBoundary("FPI");
    private static final Pattern FII_WORD = wordBoundary("FII");
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
        "\\b(LIMITED|LTD|LLP|PVT|PRIVATE|INC|CORP|CORPORATION)\\b"
    );
    private static final Pattern FOREIGN_FUND_STRUCTURE = Pattern.compile(
        "\\b(PCC|VCC|SICAV)\\b|SUB[- ]?FUND"
    );
    private static final Pattern PERSONAL_NAME_SHAPE = Pattern.compile("^[A-Z]+(?: [A-Z]+){1,3}$");

    private ParticipantClassifier() {
    }

    private static Pattern wordBoundary(String token) {
        return Pattern.compile("\\b" + token + "\\b");
    }

    static ParticipantClassification classify(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return new ParticipantClassification(ParticipantType.UNKNOWN, 0, SOURCE);
        }

        if (normalizedName.contains("MUTUAL FUND")) {
            return new ParticipantClassification(ParticipantType.MUTUAL_FUND, 95, SOURCE);
        }
        if (normalizedName.contains("INSURANCE")) {
            return new ParticipantClassification(ParticipantType.INSURANCE, 95, SOURCE);
        }
        if (AIF_WORD.matcher(normalizedName).find() || normalizedName.contains("ALTERNATIVE INVESTMENT")) {
            return new ParticipantClassification(ParticipantType.AIF, 90, SOURCE);
        }
        if (normalizedName.contains("SOVEREIGN") || normalizedName.contains("PENSION")) {
            return new ParticipantClassification(ParticipantType.SOVEREIGN_PENSION_FUND, 60, SOURCE);
        }
        if (normalizedName.contains("QUANT") || normalizedName.contains("ALGO") || normalizedName.contains("HFT")) {
            return new ParticipantClassification(ParticipantType.QUANT_HFT, 70, SOURCE);
        }
        if (normalizedName.contains("PROP")) {
            return new ParticipantClassification(ParticipantType.PROP_DESK, 70, SOURCE);
        }
        if (normalizedName.contains("BROKING") || normalizedName.contains("BROKER") || normalizedName.contains("STOCK BROKERS")) {
            return new ParticipantClassification(ParticipantType.BROKER, 70, SOURCE);
        }
        if (FPI_WORD.matcher(normalizedName).find() || FII_WORD.matcher(normalizedName).find()
            || FOREIGN_FUND_STRUCTURE.matcher(normalizedName).find()) {
            return new ParticipantClassification(ParticipantType.FPI_FII, 60, SOURCE);
        }
        if (LEGAL_SUFFIX.matcher(normalizedName).find()) {
            return new ParticipantClassification(ParticipantType.CORPORATE, 80, SOURCE);
        }
        if (PERSONAL_NAME_SHAPE.matcher(normalizedName).matches()) {
            return new ParticipantClassification(ParticipantType.INDIVIDUAL, 70, SOURCE);
        }
        return new ParticipantClassification(ParticipantType.UNKNOWN, 0, SOURCE);
    }
}
