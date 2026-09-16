package com.alphagraph.ownership.transformation;

import java.util.List;

/** {@link OwnershipTransformationEngine}'s full output for one instrument - the per-metric evidence observations plus the banded result, kept together so the orchestrator writes both from one calculation. */
record TransformationCalculation(List<EvidenceObservation> evidence, OwnershipTransformationResult result) {
}
