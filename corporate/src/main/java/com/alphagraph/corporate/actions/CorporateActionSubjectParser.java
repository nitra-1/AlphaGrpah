package com.alphagraph.corporate.actions;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies NSE's real free-text corporate-action "subject" line (e.g. {@code "Dividend - Rs 1.50
 * Per Share"}, {@code "Bonus 2:1"}, {@code "Rights 6:7 @ Premium Rs 105/-"}, {@code "Face Value
 * Split (Sub-Division) - From Rs 10/- Per Share To Re 1/- Per Share"}, {@code "Buy Back"} - real
 * subjects observed live against {@code https://www.nseindia.com/api/corporates-corporateActions})
 * into one of the five {@code action_type} values {@code corporate.corporate_actions}' CHECK
 * constraint allows (DIVIDEND/BONUS/SPLIT/RIGHTS/BUYBACK) - deterministic keyword rules, first
 * match wins, same "no ML, honest miss over a wrong guess" philosophy as
 * {@code ownership.interpretation.ParticipantClassifier}. A subject that matches none of these
 * (a bare "Scheme Of Arrangement" with no bonus/split wording, Amalgamation, Capital Reduction,
 * change of ISIN/name, ...) returns empty rather than forcing one of the five - the caller
 * quarantines it the same way an unresolved symbol is quarantined, never guessed.
 *
 * <p>{@code dividendAmount}/{@code ratioNumerator}/{@code ratioDenominator}/{@code price} are all
 * best-effort regex extraction from the same subject text - a real, disclosed limitation: NSE's
 * subject line has no fixed grammar, so a phrasing this doesn't recognize leaves that one field
 * null rather than a wrong number, exactly like every other "not extracted" field in this
 * codebase. Getting {@code actionType} right does not depend on successfully extracting these -
 * they're purely additional detail layered on top.
 */
final class CorporateActionSubjectParser {

    private static final Pattern DIVIDEND_AMOUNT = Pattern.compile(
        "Rs\\.?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*/?-?\\s*Per\\s*Share", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RATIO = Pattern.compile("(\\d+)\\s*:\\s*(\\d+)");
    private static final Pattern PREMIUM_PRICE = Pattern.compile(
        "Premium\\s*Rs\\.?\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE
    );
    // "From Rs 10/- Per Share To Re 1/- Per Share" - old face value : new face value is exactly
    // the real split ratio (1 old share of face value 10 becomes 10 new shares of face value 1).
    private static final Pattern SPLIT_FACE_VALUES = Pattern.compile(
        "From\\s*Rs\\.?\\s*([0-9]+(?:\\.[0-9]+)?).*?To\\s*Re?\\.?\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE
    );

    private CorporateActionSubjectParser() {
    }

    static Optional<ParsedSubject> parse(String subject) {
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        String upper = subject.toUpperCase(Locale.ROOT);

        if (upper.contains("DIVIDEND")) {
            return Optional.of(new ParsedSubject("DIVIDEND", extractDividendAmount(subject), null, null, null));
        }
        if (upper.contains("BONUS")) {
            Integer[] ratio = extractGroups(RATIO, subject);
            return Optional.of(new ParsedSubject("BONUS", null, ratio[0], ratio[1], null));
        }
        if (upper.contains("RIGHTS")) {
            Integer[] ratio = extractGroups(RATIO, subject);
            return Optional.of(new ParsedSubject("RIGHTS", null, ratio[0], ratio[1], extractPrice(subject)));
        }
        if (upper.contains("SPLIT") || upper.contains("SUB-DIVISION") || upper.contains("SUB DIVISION")) {
            Integer[] ratio = extractGroups(SPLIT_FACE_VALUES, subject);
            return Optional.of(new ParsedSubject("SPLIT", null, ratio[0], ratio[1], null));
        }
        if (upper.contains("BUY BACK") || upper.contains("BUYBACK") || upper.contains("BUY-BACK")) {
            return Optional.of(new ParsedSubject("BUYBACK", null, null, null, null));
        }
        return Optional.empty();
    }

    private static BigDecimal extractDividendAmount(String subject) {
        Matcher m = DIVIDEND_AMOUNT.matcher(subject);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private static BigDecimal extractPrice(String subject) {
        Matcher m = PREMIUM_PRICE.matcher(subject);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    /** Both entries are null together, or both set together - the schema pairs ratio fields, never one alone. */
    private static Integer[] extractGroups(Pattern pattern, String subject) {
        Matcher m = pattern.matcher(subject);
        if (m.find()) {
            return new Integer[]{
                Integer.valueOf(new BigDecimal(m.group(1)).intValue()),
                Integer.valueOf(new BigDecimal(m.group(2)).intValue())
            };
        }
        return new Integer[]{null, null};
    }

    record ParsedSubject(
        String actionType, BigDecimal dividendAmount, Integer ratioNumerator, Integer ratioDenominator, BigDecimal price
    ) {
    }
}
