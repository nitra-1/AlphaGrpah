/**
 * Module 2.7: Knowledge Relationship Engine. Not a graph database - {@code knowledge.entity_master}
 * and {@code knowledge.relationship} are plain PostgreSQL tables (per explicit user direction: at
 * AlphaGraph's scale, the actual workload is 2-4 hop traversals, well within what indexed
 * relationship tables and recursive CTEs handle without a second persistence technology).
 *
 * <p>{@link com.alphagraph.corporate.relationships.EntityResolver} is the only thing that ever
 * turns free text into an {@code entity_id} - role-agnostic (a name resolves to the same entity
 * regardless of which {@code EntityType} the caller expected) and auto-creating (an unmatched
 * name becomes a new entity rather than being dropped, unlike
 * {@code corporate.news.NewsInstrumentMatcher}'s tracked-only, non-creating lookup, which this
 * package's {@link com.alphagraph.corporate.relationships.EntityNameNormalizer} now shares
 * matching logic with).
 *
 * <p>{@link com.alphagraph.corporate.relationships.RelationshipBuilder} is the only writer of
 * {@code knowledge.relationship} rows - per the user's explicit design, extractors
 * ({@code corporate.knowledge.OrderExtractor}/{@code ManagementExtractor}/{@code NewsExtractor})
 * only ever emit canonical facts (which entity, which relationship type, in their own Stage 2
 * output); this package resolves those facts into entity IDs and creates the edges. The graph
 * never reads free text directly.
 */
package com.alphagraph.corporate.relationships;
