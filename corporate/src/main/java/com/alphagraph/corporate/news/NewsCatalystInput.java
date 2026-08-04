package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.NewsInstrumentLink;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record NewsCatalystInput(UUID instrumentId, String symbol, List<NewsInstrumentLink> links, LocalDate asOfDate) {
}
