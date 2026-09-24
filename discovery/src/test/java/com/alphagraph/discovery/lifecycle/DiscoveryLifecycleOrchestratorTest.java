package com.alphagraph.discovery.lifecycle;

import com.alphagraph.reference.instrument.InstrumentReader;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Mirrors the "backfill exists but is refused" convention already established for Stage 4's own {@code DiscoveryConvergenceOrchestrator}. */
class DiscoveryLifecycleOrchestratorTest {

    private final InstrumentReader instrumentReader = mock(InstrumentReader.class);
    private final ConvergenceHistoryReader convergenceHistoryReader = mock(ConvergenceHistoryReader.class);
    private final LifecycleSnapshotReader lifecycleSnapshotReader = mock(LifecycleSnapshotReader.class);
    private final DiscoveryLifecycleRuleSetLoader ruleSetLoader = mock(DiscoveryLifecycleRuleSetLoader.class);
    private final DiscoveryLifecycleEngine engine = mock(DiscoveryLifecycleEngine.class);
    private final DiscoveryLifecycleWriter writer = mock(DiscoveryLifecycleWriter.class);

    private final DiscoveryLifecycleOrchestrator orchestrator = new DiscoveryLifecycleOrchestrator(
        instrumentReader, convergenceHistoryReader, lifecycleSnapshotReader, ruleSetLoader, engine, writer
    );

    @Test
    void backfillIsRefusedRatherThanSilentlyUnsafe() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 9, 24);

        assertThatThrownBy(() -> orchestrator.backfill(from, to))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("STAGE4_HISTORY_NOT_POINT_IN_TIME_SAFE");

        verifyNoInteractions(instrumentReader, convergenceHistoryReader, lifecycleSnapshotReader, ruleSetLoader, engine, writer);
    }
}
