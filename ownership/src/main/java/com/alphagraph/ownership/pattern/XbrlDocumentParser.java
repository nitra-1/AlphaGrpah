package com.alphagraph.ownership.pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Extracts the institutional sub-category breakdown from one real SEBI/BSE shareholding-pattern
 * XBRL filing. Plain JDK {@link DocumentBuilderFactory} - no new Gradle dependency needed. Matches
 * elements by local name {@code PercentageOfTotalVotingRights} (ignoring the {@code in-bse-shp:}
 * namespace prefix, since different filers/filing software may declare it differently), reads each
 * match's {@code contextRef} attribute and text content.
 *
 * <p>Every value is a decimal fraction in the real filing (0.4962 = 49.62%) but every column this
 * codebase persists percentages in stores percentage points (49.62) - this class multiplies by 100
 * before returning, the single place that conversion happens.
 *
 * <p>Real, live-discovered gap: older real filings (a different XBRL taxonomy version than the
 * one this parser was built and verified against - {@code in-bse-shp-2025-10-31}, RELIANCE's
 * 30-Jun-2026 filing) can carry {@code PercentageOfTotalVotingRights} values already expressed as
 * a percentage rather than a fraction, or otherwise out of range once multiplied by 100 - caught
 * live as a real {@code numeric field overflow} on several real 2025-era filings across several
 * real symbols. A value outside a sane 0-100 percentage-point range after conversion is logged and
 * dropped, never persisted - the same "honest miss over wrong guess" convention as an unmapped
 * contextRef, now also covering a mapped one whose value doesn't make sense.
 */
@Component
class XbrlDocumentParser {

    private static final Logger log = LoggerFactory.getLogger(XbrlDocumentParser.class);
    private static final String PERCENTAGE_ELEMENT_LOCAL_NAME = "PercentageOfTotalVotingRights";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_SANE_PERCENTAGE = BigDecimal.valueOf(100);
    private static final BigDecimal MIN_SANE_PERCENTAGE = BigDecimal.ZERO;

    Map<XbrlCategory, BigDecimal> parse(String xmlContent) {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE hardening - this parses XML fetched from the network; no legitimate reason for
            // a shareholding-pattern filing to reference an external entity or DTD.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse XBRL document: " + e.getMessage(), e);
        }

        Map<XbrlCategory, BigDecimal> byCategory = new EnumMap<>(XbrlCategory.class);
        Set<String> unmappedContextRefs = new HashSet<>();

        NodeList allElements = document.getElementsByTagNameNS("*", PERCENTAGE_ELEMENT_LOCAL_NAME);
        IntStream.range(0, allElements.getLength())
            .mapToObj(allElements::item)
            .filter(Element.class::isInstance)
            .map(Element.class::cast)
            .forEach(element -> {
                String contextRef = element.getAttribute("contextRef");
                if (contextRef.isBlank()) {
                    return;
                }
                var category = XbrlContextMapping.categoryFor(contextRef);
                if (category.isEmpty()) {
                    unmappedContextRefs.add(contextRef);
                    return;
                }
                String text = element.getTextContent();
                if (text == null || text.isBlank()) {
                    return;
                }
                try {
                    BigDecimal fraction = new BigDecimal(text.trim());
                    BigDecimal percentagePoints = fraction.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
                    if (percentagePoints.compareTo(MIN_SANE_PERCENTAGE) < 0 || percentagePoints.compareTo(MAX_SANE_PERCENTAGE) > 0) {
                        log.warn(
                            "Out-of-range XBRL value for contextRef {}: raw={} -> {}pp (outside 0-100, likely a different taxonomy version) - dropped, not persisted",
                            contextRef, text, percentagePoints
                        );
                        return;
                    }
                    byCategory.put(category.get(), percentagePoints);
                } catch (NumberFormatException e) {
                    log.warn("Non-numeric XBRL value for contextRef {}: {}", contextRef, text);
                }
            });

        if (!unmappedContextRefs.isEmpty()) {
            log.debug("Unmapped XBRL contextRefs (not extracted, honest miss not a guess): {}", unmappedContextRefs);
        }

        return byCategory;
    }
}
