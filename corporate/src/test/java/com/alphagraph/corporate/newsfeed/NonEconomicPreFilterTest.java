package com.alphagraph.corporate.newsfeed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NonEconomicPreFilterTest {

    private final NonEconomicPreFilter filter = new NonEconomicPreFilter();

    @Test
    void genuineEconomicArticleIsPlausible() {
        String text = "The government announced a new Semiconductor PLI scheme worth ten thousand "
            + "crore rupees, boosting investor sentiment across electronics manufacturing stocks.";

        assertThat(filter.isPlausiblyEconomic(text)).isTrue();
    }

    @Test
    void sportsArticleDominatedByBlocklistVocabularyIsRejected() {
        String text = "India won the cricket match against Australia in the final over of the "
            + "tournament, with the wicket-keeper hailed as the athlete of the championship at the stadium.";

        assertThat(filter.isPlausiblyEconomic(text)).isFalse();
    }

    @Test
    void entertainmentArticleIsRejected() {
        String text = "The Bollywood actor and actress celebrated their wedding with a lavish "
            + "concert, releasing a new song and album for their upcoming film.";

        assertThat(filter.isPlausiblyEconomic(text)).isFalse();
    }

    @Test
    void economicArticleWithAPassingSportsMentionIsNotRejected() {
        // A single blocklist word among many real-economy words must not sink a genuine story -
        // inclusion-biased by design (the real relevance call is NewsExtractor's own LLM field).
        String text = "The Reserve Bank of India kept the repo rate unchanged at six percent, "
            + "citing stable inflation and steady growth in manufacturing and services output, even "
            + "as a cricket sponsorship deal was announced separately by a leading bank this week "
            + "for the upcoming tournament season alongside broader capital market reforms.";

        assertThat(filter.isPlausiblyEconomic(text)).isTrue();
    }

    @Test
    void blankTextIsNotPlausible() {
        assertThat(filter.isPlausiblyEconomic("")).isFalse();
        assertThat(filter.isPlausiblyEconomic(null)).isFalse();
        assertThat(filter.isPlausiblyEconomic("   ")).isFalse();
    }
}
