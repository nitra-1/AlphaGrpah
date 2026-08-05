package com.alphagraph.corporate.relationships;

import com.alphagraph.corporate.api.EntityType;
import com.alphagraph.corporate.api.RelationshipType;
import com.alphagraph.corporate.knowledge.ExtractedFact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RelationshipBuilderTest {

    private final EntityResolver entityResolver = mock(EntityResolver.class);
    private final RelationshipWriter relationshipWriter = mock(RelationshipWriter.class);
    private final CompetitorGroupExpander competitorGroupExpander = mock(CompetitorGroupExpander.class);
    private final RelationshipBuilder builder = new RelationshipBuilder(entityResolver, relationshipWriter, competitorGroupExpander);

    private final UUID documentId = UUID.randomUUID();
    private final UUID companyEntityId = UUID.randomUUID();
    private final UUID schemeEntityId = UUID.randomUUID();

    @Test
    void newsShapedGroupResolvesCompanyNameAsFromEntity() {
        when(entityResolver.resolve(EntityType.COMPANY, "Kaynes Technology")).thenReturn(companyEntityId);
        when(entityResolver.resolve(EntityType.GOVERNMENT_SCHEME, "Semiconductor PLI")).thenReturn(schemeEntityId);

        UUID group = UUID.randomUUID();
        List<ExtractedFact> facts = List.of(
            fact("companyname", "Kaynes Technology", group),
            fact("relatedentityname", "Semiconductor PLI", group),
            fact("relatedentitytype", "GOVERNMENT_SCHEME", group),
            fact("relationshiptype", "BENEFICIARY_OF", group)
        );

        builder.build(documentId, null, facts);

        verify(relationshipWriter).write(
            eq(companyEntityId), eq(RelationshipType.BENEFICIARY_OF), eq(schemeEntityId), eq(documentId), anyDouble(), any()
        );
    }

    @Test
    void managementShapedGroupFallsBackToDocumentSymbol() {
        when(entityResolver.resolve(EntityType.COMPANY, "KAYNES")).thenReturn(companyEntityId);
        when(entityResolver.resolve(EntityType.THEME, "Semiconductor")).thenReturn(schemeEntityId);

        UUID group = UUID.randomUUID();
        List<ExtractedFact> facts = List.of(
            fact("metrictype", "DEMAND", group),
            fact("relatedentityname", "Semiconductor", group),
            fact("relatedentitytype", "THEME", group),
            fact("relationshiptype", "PART_OF_THEME", group)
        );

        builder.build(documentId, "KAYNES", facts);

        verify(relationshipWriter).write(
            eq(companyEntityId), eq(RelationshipType.PART_OF_THEME), eq(schemeEntityId), eq(documentId), anyDouble(), any()
        );
    }

    @Test
    void managementShapedGroupWithNoDocumentSymbolProducesNoRelationship() {
        UUID group = UUID.randomUUID();
        List<ExtractedFact> facts = List.of(
            fact("metrictype", "DEMAND", group),
            fact("relatedentityname", "Semiconductor", group),
            fact("relatedentitytype", "THEME", group),
            fact("relationshiptype", "PART_OF_THEME", group)
        );

        builder.build(documentId, null, facts);

        verifyNoInteractions(relationshipWriter);
    }

    @Test
    void groupMissingRelatedEntityFieldsProducesNoRelationship() {
        UUID group = UUID.randomUUID();
        List<ExtractedFact> facts = List.of(fact("companyname", "Kaynes Technology", group), fact("direction", "POSITIVE", group));

        builder.build(documentId, null, facts);

        verifyNoInteractions(relationshipWriter);
    }

    @Test
    void unrecognizedEnumValuesAreSkippedWithoutThrowing() {
        UUID group = UUID.randomUUID();
        List<ExtractedFact> facts = List.of(
            fact("companyname", "Kaynes Technology", group),
            fact("relatedentityname", "Semiconductor PLI", group),
            fact("relatedentitytype", "NOT_A_REAL_TYPE", group),
            fact("relationshiptype", "BENEFICIARY_OF", group)
        );

        builder.build(documentId, null, facts);

        verifyNoInteractions(relationshipWriter);
    }

    @Test
    void orderFactsProduceExecutesForRelationship() {
        UUID customerEntityId = UUID.randomUUID();
        when(entityResolver.resolve(EntityType.COMPANY, "BEL")).thenReturn(companyEntityId);
        when(entityResolver.resolve(EntityType.CUSTOMER, "Ministry of Defence")).thenReturn(customerEntityId);

        List<ExtractedFact> facts = List.of(
            fact("orderlifecyclestage", "NEW_ORDER", null),
            fact("customer", "Ministry of Defence", null)
        );

        builder.build(documentId, "BEL", facts);

        verify(relationshipWriter).write(
            eq(companyEntityId), eq(RelationshipType.EXECUTES_FOR), eq(customerEntityId), eq(documentId), anyDouble(), any()
        );
    }

    @Test
    void orderFactsWithNoDocumentSymbolProduceNoRelationship() {
        List<ExtractedFact> facts = List.of(
            fact("orderlifecyclestage", "NEW_ORDER", null),
            fact("customer", "Ministry of Defence", null)
        );

        builder.build(documentId, null, facts);

        verifyNoInteractions(relationshipWriter);
    }

    @Test
    void orderFactsMissingCustomerProduceNoRelationship() {
        List<ExtractedFact> facts = List.of(fact("orderlifecyclestage", "NEW_ORDER", null));

        builder.build(documentId, "BEL", facts);

        verify(relationshipWriter, never()).write(any(), any(), any(), any(), anyDouble(), any());
    }

    @Test
    void expandCompetitorGroupsDelegatesToExpander() {
        builder.expandCompetitorGroups();

        verify(competitorGroupExpander, times(1)).expandAll();
    }

    private ExtractedFact fact(String type, String value, UUID group) {
        return new ExtractedFact(type, value, "", 85.0, null, group);
    }
}
