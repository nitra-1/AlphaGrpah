package com.alphagraph.api.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAnIdWhenTheClientSendsNone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(mdcDuringChain.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(mdcDuringChain.get());
    }

    @Test
    void echoesBackAClientSuppliedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "client-supplied-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(mdcDuringChain.get()).isEqualTo("client-supplied-id-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("client-supplied-id-123");
    }

    @Test
    void truncatesAnUnreasonablyLongClientSuppliedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "x".repeat(500));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).hasSize(100);
    }

    @Test
    void mdcIsClearedAfterTheRequestCompletesEvenIfTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new RuntimeException("downstream failure");
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {
            // expected - we only care that MDC gets cleaned up regardless
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
