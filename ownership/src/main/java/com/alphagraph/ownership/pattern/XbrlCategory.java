package com.alphagraph.ownership.pattern;

/**
 * The real XBRL sub-categories this codebase currently extracts from a shareholding filing.
 * {@code PROMOTER_CROSSCHECK} is extracted but never persisted as its own column - it exists only
 * so {@link XbrlShareholdingWriter} can log a discrepancy against the daily summary JSON's own
 * promoter figure, never to overwrite it (see the writer's own doc comment).
 */
enum XbrlCategory {
    FPI_CATEGORY_1, FPI_CATEGORY_2, INSURANCE_COMPANIES, OTHER_FINANCIAL_INSTITUTIONS, MF, PROMOTER_CROSSCHECK
}
