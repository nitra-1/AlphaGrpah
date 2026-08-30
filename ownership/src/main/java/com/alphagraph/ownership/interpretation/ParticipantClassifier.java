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
 *
 * <p><b>LENSKART investigation (2026-08-30) coverage fixes:</b> the original keyword set missed
 * several real institution shapes - sovereign/pension names that say "AUTHORITY"/"SUPERANNUATION"/
 * "RETIREMENT" rather than "SOVEREIGN"/"PENSION" (e.g. {@code KUWAIT INVESTMENT AUTHORITY}); banks
 * and securities arms that say "BANK"/"SECURITIES" rather than "BROKER"/"BROKING"; VC/PE-shaped
 * names ({@code VENTURES}/{@code CAPITAL}/{@code PARTNERS}/{@code FUND}) with no legal suffix and
 * no explicit FPI/FII marker, classified as {@link ParticipantType#FPI_FII} at a deliberately lower
 * confidence (55, below the existing PCC/VCC/SUB-FUND signal's 60) since a bare word like "FUND" is
 * a weaker signal than an explicit foreign-fund structure marker; and foreign corporate suffixes
 * ({@code SA}/{@code SE}/{@code PTE}/{@code PTY}/{@code PLC}/{@code NV}/{@code AG}) alongside the
 * existing Indian/US ones. Real production example: {@code ALPHA WAVE VENTURES} and
 * {@code ALPHA WAVE VENTURES II LP} previously diverged (INDIVIDUAL/70 vs UNKNOWN/0 respectively,
 * purely because the personal-name-shape fallback's token-count regex happened to match one and not
 * the other) despite being the same real fund family - both now resolve identically via the bare
 * {@code VENTURES} keyword, before either one ever reaches the personal-name fallback.
 *
 * <p>The personal-name-shape fallback was also narrowed in the same pass: it previously matched
 * any 2-4 all-caps-token name, which let two-token institution names with no keyword and no legal
 * suffix (e.g. {@code SOCIETE GENERALE}) masquerade as confident {@link ParticipantType#INDIVIDUAL}
 * guesses. Real individual names captured in production are consistently 3+ tokens (e.g.
 * {@code JAGID VANITABEN RAJENDRAPRASAD}), so the shape now requires a minimum of 3 tokens, and a
 * denylist of institution-shaped words ({@code INVESTMENT}, {@code TRUST}, {@code HOLDINGS}, etc.)
 * blocks the fallback even when a 3+ token name matches the shape. A name with neither a keyword,
 * a legal suffix, nor a denylisted word (e.g. {@code SOCIETE GENERALE}) still falls to
 * {@link ParticipantType#UNKNOWN} rather than a wrong guess - an accepted residual gap; a real
 * bank/institution name lookup table is out of scope for this fix.
 */
final class ParticipantClassifier {

    private static final String SOURCE = "NAME_PATTERN_V1";

    private static final Pattern AIF_WORD = wordBoundary("AIF");
    private static final Pattern FPI_WORD = wordBoundary("FPI");
    private static final Pattern FII_WORD = wordBoundary("FII");
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
        "\\b(LIMITED|LTD|LLP|PVT|PRIVATE|INC|CORP|CORPORATION|SA|SE|PTE|PTY|PLC|NV|AG)\\b"
    );
    private static final Pattern FOREIGN_FUND_STRUCTURE = Pattern.compile(
        "\\b(PCC|VCC|SICAV)\\b|SUB[- ]?FUND"
    );
    private static final Pattern BARE_FUND_SHAPED_WORD = Pattern.compile(
        "\\b(VENTURES|CAPITAL|PARTNERS|FUND)\\b"
    );
    private static final Pattern PERSONAL_NAME_SHAPE = Pattern.compile("^[A-Z]+(?: [A-Z]+){2,3}$");
    private static final Pattern PERSONAL_NAME_DENYLIST = Pattern.compile(
        "\\b(INVESTMENT|AUTHORITY|VENTURES|CAPITAL|PARTNERS|FUND|TRUST|BANK|SECURITIES|HOLDINGS|GROUP|ASSET|MANAGEMENT|ADVISORS|GLOBAL)\\b"
    );

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
        if (normalizedName.contains("SOVEREIGN") || normalizedName.contains("PENSION")
            || normalizedName.contains("AUTHORITY") || normalizedName.contains("SUPERANNUATION")
            || normalizedName.contains("RETIREMENT")) {
            return new ParticipantClassification(ParticipantType.SOVEREIGN_PENSION_FUND, 60, SOURCE);
        }
        if (normalizedName.contains("QUANT") || normalizedName.contains("ALGO") || normalizedName.contains("HFT")) {
            return new ParticipantClassification(ParticipantType.QUANT_HFT, 70, SOURCE);
        }
        if (normalizedName.contains("PROP")) {
            return new ParticipantClassification(ParticipantType.PROP_DESK, 70, SOURCE);
        }
        if (normalizedName.contains("BROKING") || normalizedName.contains("BROKER") || normalizedName.contains("STOCK BROKERS")
            || normalizedName.contains("BANK") || normalizedName.contains("SECURITIES")) {
            return new ParticipantClassification(ParticipantType.BROKER, 70, SOURCE);
        }
        if (FPI_WORD.matcher(normalizedName).find() || FII_WORD.matcher(normalizedName).find()
            || FOREIGN_FUND_STRUCTURE.matcher(normalizedName).find()) {
            return new ParticipantClassification(ParticipantType.FPI_FII, 60, SOURCE);
        }
        if (LEGAL_SUFFIX.matcher(normalizedName).find()) {
            return new ParticipantClassification(ParticipantType.CORPORATE, 80, SOURCE);
        }
        if (BARE_FUND_SHAPED_WORD.matcher(normalizedName).find()) {
            return new ParticipantClassification(ParticipantType.FPI_FII, 55, SOURCE);
        }
        if (PERSONAL_NAME_SHAPE.matcher(normalizedName).matches()
            && !PERSONAL_NAME_DENYLIST.matcher(normalizedName).find()) {
            return new ParticipantClassification(ParticipantType.INDIVIDUAL, 70, SOURCE);
        }
        return new ParticipantClassification(ParticipantType.UNKNOWN, 0, SOURCE);
    }
}
