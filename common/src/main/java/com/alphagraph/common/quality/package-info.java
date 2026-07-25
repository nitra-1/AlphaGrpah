/**
 * Data Quality Engine: scores the batch a {@link com.alphagraph.common.etl.Pipeline} just
 * loaded on completeness, duplicates, missing required fields, and validation errors — see
 * docs/002_Engine_Architecture.md §3. Independent of any domain module and independent of the
 * ETL framework itself; the scheduler module (Module 0.8) is what wires the two together.
 */
package com.alphagraph.common.quality;
