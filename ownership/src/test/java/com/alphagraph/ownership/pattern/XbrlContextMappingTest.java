package com.alphagraph.ownership.pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XbrlContextMappingTest {

    @Test
    void mapsEveryConfirmedRealContextRef() {
        assertThat(XbrlContextMapping.categoryFor("InstitutionsForeignPortfolioInvestorCategoryOne_ContextI"))
            .contains(XbrlCategory.FPI_CATEGORY_1);
        assertThat(XbrlContextMapping.categoryFor("InstitutionsForeignPortfolioInvestorCategoryTwo_ContextI"))
            .contains(XbrlCategory.FPI_CATEGORY_2);
        assertThat(XbrlContextMapping.categoryFor("InsuranceCompanies_ContextI"))
            .contains(XbrlCategory.INSURANCE_COMPANIES);
        assertThat(XbrlContextMapping.categoryFor("OtherFinancialInstitutions_ContextI"))
            .contains(XbrlCategory.OTHER_FINANCIAL_INSTITUTIONS);
        assertThat(XbrlContextMapping.categoryFor("MutualFundsOrUTI_ContextI"))
            .contains(XbrlCategory.MF);
        assertThat(XbrlContextMapping.categoryFor("ShareholdingOfPromoterAndPromoterGroup_ContextI"))
            .contains(XbrlCategory.PROMOTER_CROSSCHECK);
    }

    @Test
    void unmappedContextRefReturnsEmptyRatherThanGuessing() {
        assertThat(XbrlContextMapping.categoryFor("SomeFutureCategory_ContextI")).isEmpty();
        assertThat(XbrlContextMapping.categoryFor("NonInstitutions_ContextI")).isEmpty();
    }
}
