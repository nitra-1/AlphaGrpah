package com.alphagraph.technical.indicators;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class SmaTest {

    @Test
    void averagesTheMostRecentWindow() {
        OptionalDouble result = Sma.of(List.of(1.0, 2.0, 3.0, 10.0, 20.0, 30.0), 3);

        assertThat(result).hasValue(20.0);
    }

    @Test
    void insufficientDataIsEmpty() {
        assertThat(Sma.of(List.of(1.0, 2.0), 3)).isEmpty();
    }
}
