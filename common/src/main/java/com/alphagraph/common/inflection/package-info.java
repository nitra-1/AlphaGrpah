/**
 * Shared Stage 2 ("inflection detection") vocabulary - concepts every domain module's own Stage 2
 * engine reuses, starting with {@link com.alphagraph.common.inflection.VelocityBand}. Lives in
 * {@code common} (no dependency restriction, every module already depends on it) rather than any
 * one domain module, since Ownership/Market/Sector's own Stage 2 retrofits all need the same bands.
 * See docs/007_Stage2_Inflection_Specification.md.
 */
package com.alphagraph.common.inflection;
