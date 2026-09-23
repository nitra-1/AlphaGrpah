package com.alphagraph.corporate.transformation;

import com.alphagraph.corporate.api.CorporateAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CapitalAllocationEngineTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE";

    private final CapitalAllocationEngine engine = new CapitalAllocationEngine();

    @Test
    void instrumentWithNoActionsOfAnyTypeProducesNoEvidence() {
        var evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, List.of(), LocalDate.of(2026, 1, 1));

        assertThat(evidence).isEmpty();
    }

    @Test
    void metricIsSkippedWhenThatActionTypeNeverOccurred() {
        var actions = List.of(action("DIVIDEND", LocalDate.of(2025, 6, 1)));

        var evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, actions, LocalDate.of(2026, 1, 1));

        assertThat(evidence).isEmpty();
    }

    @Test
    void buybackWithinWindowCountsAsOne() {
        var actions = List.of(action("BUYBACK", LocalDate.of(2025, 11, 1)));
        LocalDate asOfDate = LocalDate.of(2026, 1, 1); // 61 days after, within the 180-day window

        var evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, actions, asOfDate);

        var buyback = evidence.stream().filter(o -> o.metric() == CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D).findFirst().orElseThrow();
        assertThat(buyback.value()).isEqualTo(1);
        assertThat(buyback.priorValue()).isEqualTo(1);
        assertThat(buyback.change()).isEqualTo(0);
    }

    @Test
    void buybackEnteringTheWindowTodayShowsAPositiveChange() {
        var actions = List.of(action("BUYBACK", LocalDate.of(2026, 1, 1)));
        LocalDate asOfDate = LocalDate.of(2026, 1, 1); // ex-date is today - entered the window today, not yesterday

        var evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, actions, asOfDate);

        var buyback = evidence.stream().filter(o -> o.metric() == CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D).findFirst().orElseThrow();
        assertThat(buyback.value()).isEqualTo(1);
        assertThat(buyback.priorValue()).isEqualTo(0);
        assertThat(buyback.change()).isEqualTo(1);
        assertThat(buyback.enteredEventCount()).isEqualTo(1);
        assertThat(buyback.exitedEventCount()).isEqualTo(0);
        assertThat(buyback.persistenceDays()).isEqualTo(0);
    }

    // ---- gross entered/exited counts (the fix: change alone can hide a same-day cancellation) ----

    @Test
    void changeAlwaysEqualsEnteredMinusExitedAcrossAWindowOfDatesIncludingAnExit() {
        var actions = List.of(action("BUYBACK", LocalDate.of(2025, 1, 1)));

        for (int offset = -2; offset <= 182; offset++) {
            LocalDate asOfDate = LocalDate.of(2025, 1, 1).plusDays(offset);
            var evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, actions, asOfDate);
            var buyback = evidence.stream().filter(o -> o.metric() == CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D).findFirst().orElseThrow();
            assertThat(buyback.change()).as("asOfDate=%s", asOfDate).isEqualTo(buyback.enteredEventCount() - buyback.exitedEventCount());
        }
    }

    @Test
    void exitingTheWindowExactlyOneEightyDaysAfterExDateShowsAGrossExit() {
        var actions = List.of(action("BUYBACK", LocalDate.of(2025, 1, 1)));
        LocalDate asOfDate = LocalDate.of(2025, 1, 1).plusDays(180); // window is [exDate, exDate+179] - the event exits exactly on day 180

        var evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, actions, asOfDate);

        var buyback = evidence.stream().filter(o -> o.metric() == CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D).findFirst().orElseThrow();
        assertThat(buyback.value()).isEqualTo(0);
        assertThat(buyback.change()).isEqualTo(-1);
        assertThat(buyback.enteredEventCount()).isEqualTo(0);
        assertThat(buyback.exitedEventCount()).isEqualTo(1);
    }

    @Test
    void enteringAndExitingOnTheSameDayCancelInNetChangeButNotInTheGrossCounts() {
        // A second real buyback's exDate lands exactly 180 days after the first's - the first
        // exits the window the same day the second enters, so change nets to 0 even though a real
        // new event genuinely occurred that day. This is the exact case CapitalAllocationTransformationSequenceEngine
        // used to miss when it read Math.max(change, 0) instead of the persisted gross count.
        var actions = List.of(
            action("BUYBACK", LocalDate.of(2025, 1, 1)),
            action("BUYBACK", LocalDate.of(2025, 1, 1).plusDays(180))
        );
        LocalDate asOfDate = LocalDate.of(2025, 1, 1).plusDays(180);

        var evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, actions, asOfDate);

        var buyback = evidence.stream().filter(o -> o.metric() == CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D).findFirst().orElseThrow();
        assertThat(buyback.change()).isEqualTo(0); // net change hides the second event
        assertThat(buyback.enteredEventCount()).isEqualTo(1); // gross count still sees it
        assertThat(buyback.exitedEventCount()).isEqualTo(1);
    }

    @Test
    void buybackOutsideTheWindowCountsAsZeroButStillProducesEvidence() {
        // BUYBACK occurred once, ever - so the metric is still evaluated (not skipped), it's just
        // a real zero count now that the one real event has aged out of the 180-day window.
        var actions = List.of(action("BUYBACK", LocalDate.of(2025, 1, 1)));
        LocalDate asOfDate = LocalDate.of(2026, 1, 1); // 365 days later, well outside 180 days

        var evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, actions, asOfDate);

        var buyback = evidence.stream().filter(o -> o.metric() == CapitalAllocationMetric.BUYBACK_EVENT_COUNT_180D).findFirst().orElseThrow();
        assertThat(buyback.value()).isEqualTo(0);
        assertThat(buyback.priorValue()).isEqualTo(0);
        assertThat(buyback.change()).isEqualTo(0);
    }

    @Test
    void rightsIssueIsCountedUnderEquityRaiseNotBuyback() {
        var actions = List.of(action("RIGHTS", LocalDate.of(2025, 12, 1)));

        var evidence = engine.calculate(INSTRUMENT_ID, SYMBOL, actions, LocalDate.of(2026, 1, 1));

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).metric()).isEqualTo(CapitalAllocationMetric.EQUITY_RAISE_EVENT_COUNT_180D);
    }

    private static CorporateAction action(String actionType, LocalDate exDate) {
        return new CorporateAction(INSTRUMENT_ID, SYMBOL, actionType, exDate, null, null, null, null, null, null, Instant.now());
    }
}
