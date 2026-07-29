/**
 * Reads reference.sectors/reference.instruments directly - reference is a shared kernel every
 * module already reads freely (docs/001_System_Architecture.md §4 Rule 2), not a restricted peer
 * domain module, so no intelligence-bridging is needed for this part (unlike market's price
 * history, which sector.engine cannot read directly - see intelligence.sector).
 */
package com.alphagraph.sector.mapping;
