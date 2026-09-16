package com.alphagraph.ownership.pattern;

import java.util.Map;
import java.util.Optional;

/**
 * Maps a real SEBI/BSE shareholding-pattern XBRL filing's {@code contextRef} attribute to the
 * category it represents - confirmed live against a real RELIANCE filing
 * ({@code https://nsearchives.nseindia.com/corporate/xbrl/SHP_...xml}, namespace
 * {@code in-bse-shp}, values tagged {@code in-bse-shp:PercentageOfTotalVotingRights}).
 *
 * <p>A {@code contextRef} not in this map is logged and skipped by the caller, never guessed -
 * same "no confident match -> honest miss" convention as
 * {@code ownership.interpretation.ParticipantClassifier}. Other real filers' XBRL may use
 * different context IDs than RELIANCE's (different filing software, different taxonomy version) -
 * this map is expected to grow as more real filings are processed, not assumed complete from one
 * sample.
 */
final class XbrlContextMapping {

    private static final Map<String, XbrlCategory> CONTEXT_TO_CATEGORY = Map.of(
        "InstitutionsForeignPortfolioInvestorCategoryOne_ContextI", XbrlCategory.FPI_CATEGORY_1,
        "InstitutionsForeignPortfolioInvestorCategoryTwo_ContextI", XbrlCategory.FPI_CATEGORY_2,
        "InsuranceCompanies_ContextI", XbrlCategory.INSURANCE_COMPANIES,
        "OtherFinancialInstitutions_ContextI", XbrlCategory.OTHER_FINANCIAL_INSTITUTIONS,
        "MutualFundsOrUTI_ContextI", XbrlCategory.MF,
        "ShareholdingOfPromoterAndPromoterGroup_ContextI", XbrlCategory.PROMOTER_CROSSCHECK
    );

    private XbrlContextMapping() {
    }

    static Optional<XbrlCategory> categoryFor(String contextRef) {
        return Optional.ofNullable(CONTEXT_TO_CATEGORY.get(contextRef));
    }
}
