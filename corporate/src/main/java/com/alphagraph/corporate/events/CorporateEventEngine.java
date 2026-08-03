package com.alphagraph.corporate.events;

import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.EventType;
import com.alphagraph.corporate.api.RevenueImpact;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classifies one document's canonical knowledge (topics/facts/summary, produced by the shared
 * {@code corporate.knowledge.DocumentIntelligenceEngine}) into zero or more of the 13 named
 * corporate events - a deterministic rule engine, NOT an LLM caller. Retrofitted from the original
 * Module 2.3 design (which called Claude directly on raw document text) once a second engine
 * (Order Book, Module 2.4) needed to read the same documents: rather than every engine re-reading
 * the PDF text and potentially extracting inconsistent values for the same fact, one canonical
 * extraction pass runs per document and every downstream engine, including this one, reads its
 * stored output.
 *
 * <p>The semantic heavy lifting (recognizing that a document describes, say, a large order) still
 * happens via an LLM - just once, upstream, in the canonical extraction pass, which is prompted to
 * tag a document with the exact 13 category names as topics whenever they genuinely apply. This
 * engine's job is a deliberately simple, deterministic exact-match against those topics, plus
 * deriving the remaining output fields (category, expected duration, revenue impact, signal) from
 * static lookups and extracted facts rather than a bespoke per-document LLM synthesis - a real
 * quality trade-off against the original design's tailored classification, accepted in exchange
 * for one shared, consistent, cheaper extraction pass.
 *
 * <p>Deliberately does NOT implement {@code common.engine.Engine<I, O extends Score>} or
 * {@code common.rules.RuleSet} - that machinery is built for numeric-threshold rules against a
 * single target metric (docs/002_Engine_Architecture.md §5), which doesn't fit matching a document
 * against a set of topic tags.
 */
@Component
public class CorporateEventEngine {

    /** Bumped whenever this classification logic changes meaningfully - stored in the same {@code corporate_events.prompt_version} column the original LLM-based engine used, now meaning "rule logic version" rather than "prompt version". */
    static final int RULE_VERSION = 2;

    private static final Map<String, EventType> TOPIC_TO_EVENT_TYPE = buildTopicMapping();
    private static final Map<EventType, String> EVENT_TYPE_TO_CATEGORY = buildCategoryMapping();

    private final ObjectMapper objectMapper;

    public CorporateEventEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Returns zero or more events matched from this document's canonical topics/facts. */
    public List<ExtractedEvent> classify(KnowledgeContext context) {
        List<ExtractedEvent> events = new ArrayList<>();
        for (String topic : context.topics()) {
            EventType eventType = TOPIC_TO_EVENT_TYPE.get(normalizeTopic(topic));
            if (eventType != null) {
                events.add(buildEvent(eventType, topic, context));
            }
        }
        return events;
    }

    private ExtractedEvent buildEvent(EventType eventType, String matchedTopic, KnowledgeContext context) {
        return new ExtractedEvent(
            eventType,
            EVENT_TYPE_TO_CATEGORY.get(eventType),
            context.summary(),
            context.confidence(),
            deriveExpectedDuration(context),
            deriveRevenueImpact(context),
            EventSignal.valueOf(context.sentiment().name()),
            buildProvenance(eventType, matchedTopic, context)
        );
    }

    private String deriveExpectedDuration(KnowledgeContext context) {
        FactValue start = context.facts().get("executionstart");
        FactValue end = context.facts().get("executionend");
        if (start == null || end == null) {
            return "Not specified";
        }
        try {
            int startYear = Integer.parseInt(start.value().trim());
            int endYear = Integer.parseInt(end.value().trim());
            int years = endYear - startYear;
            return years > 0 ? years + " Years" : "Not specified";
        } catch (NumberFormatException e) {
            return "Not specified";
        }
    }

    private RevenueImpact deriveRevenueImpact(KnowledgeContext context) {
        FactValue orderValue = context.facts().get("ordervalue");
        if (orderValue == null) {
            return RevenueImpact.NONE;
        }

        double crore;
        try {
            double rawValue = Double.parseDouble(orderValue.value().trim());
            String unit = orderValue.unit() == null ? "" : orderValue.unit().toLowerCase();
            if (unit.contains("crore")) {
                crore = rawValue;
            } else if (unit.contains("lakh")) {
                crore = rawValue / 100.0;
            } else {
                crore = rawValue / 1.0e7; // ABSOLUTE rupees -> crore
            }
        } catch (NumberFormatException e) {
            return RevenueImpact.NONE;
        }

        if (crore >= 1000) {
            return RevenueImpact.HIGH;
        } else if (crore >= 100) {
            return RevenueImpact.MEDIUM;
        } else if (crore > 0) {
            return RevenueImpact.LOW;
        }
        return RevenueImpact.NONE;
    }

    private String buildProvenance(EventType eventType, String matchedTopic, KnowledgeContext context) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "eventType", eventType.name(),
                "matchedTopic", matchedTopic,
                "documentType", context.documentType() == null ? "" : context.documentType(),
                "ruleVersion", RULE_VERSION
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize event provenance", e);
        }
    }

    private static String normalizeTopic(String topic) {
        return topic.trim().toLowerCase();
    }

    private static Map<String, EventType> buildTopicMapping() {
        Map<String, EventType> mapping = new LinkedHashMap<>();
        mapping.put("large order", EventType.LARGE_ORDER);
        mapping.put("capacity expansion", EventType.CAPACITY_EXPANSION);
        mapping.put("new plant", EventType.NEW_PLANT);
        mapping.put("acquisition", EventType.ACQUISITION);
        mapping.put("merger", EventType.MERGER);
        mapping.put("joint venture", EventType.JOINT_VENTURE);
        mapping.put("pli approval", EventType.PLI_APPROVAL);
        mapping.put("patent", EventType.PATENT);
        mapping.put("export approval", EventType.EXPORT_APPROVAL);
        mapping.put("government contract", EventType.GOVERNMENT_CONTRACT);
        mapping.put("debt raising", EventType.DEBT_RAISING);
        mapping.put("promoter buying", EventType.PROMOTER_BUYING);
        mapping.put("promoter selling", EventType.PROMOTER_SELLING);
        return Map.copyOf(mapping);
    }

    private static Map<EventType, String> buildCategoryMapping() {
        Map<EventType, String> mapping = new LinkedHashMap<>();
        mapping.put(EventType.LARGE_ORDER, "Revenue Positive");
        mapping.put(EventType.CAPACITY_EXPANSION, "Capital Expansion");
        mapping.put(EventType.NEW_PLANT, "Capital Expansion");
        mapping.put(EventType.ACQUISITION, "Strategic");
        mapping.put(EventType.MERGER, "Strategic");
        mapping.put(EventType.JOINT_VENTURE, "Strategic");
        mapping.put(EventType.PLI_APPROVAL, "Regulatory Approval");
        mapping.put(EventType.PATENT, "Intellectual Property");
        mapping.put(EventType.EXPORT_APPROVAL, "Regulatory Approval");
        mapping.put(EventType.GOVERNMENT_CONTRACT, "Government Contract");
        mapping.put(EventType.DEBT_RAISING, "Financing");
        mapping.put(EventType.PROMOTER_BUYING, "Ownership Signal");
        mapping.put(EventType.PROMOTER_SELLING, "Ownership Signal");
        return Map.copyOf(mapping);
    }
}
