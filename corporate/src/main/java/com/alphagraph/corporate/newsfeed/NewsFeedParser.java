package com.alphagraph.corporate.newsfeed;

import com.alphagraph.common.etl.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses each fetched RSS 2.0 feed's raw XML into {@link RawNewsItem}s via the JDK's built-in
 * {@link DocumentBuilder} (no new dependency needed for well-formed RSS - it's just XML). DOM's
 * {@code getTextContent()} already unwraps CDATA-wrapped fields (LiveMint wraps every field in
 * CDATA; Economic Times only wraps title/description) transparently, so no separate CDATA
 * handling is needed.
 */
@Component
public class NewsFeedParser implements Parser<List<RawNewsFeedResponse>, RawNewsItem> {

    private static final Logger log = LoggerFactory.getLogger(NewsFeedParser.class);

    @Override
    public List<RawNewsItem> parse(List<RawNewsFeedResponse> feeds) {
        List<RawNewsItem> items = new ArrayList<>();
        for (RawNewsFeedResponse feed : feeds) {
            try {
                items.addAll(parseOneFeed(feed));
            } catch (Exception e) {
                log.warn("Failed to parse {} feed XML: {}", feed.outlet(), e.getMessage());
            }
        }
        return items;
    }

    private List<RawNewsItem> parseOneFeed(RawNewsFeedResponse feed) throws Exception {
        // Untrusted external XML (public internet feeds) - disallow DOCTYPE entirely (RSS never
        // legitimately needs one) to rule out XXE, rather than trying to selectively disable
        // external entity/DTD resolution.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document xmlDocument = builder.parse(new ByteArrayInputStream(feed.xml().getBytes(StandardCharsets.UTF_8)));

        NodeList itemNodes = xmlDocument.getElementsByTagName("item");
        List<RawNewsItem> items = new ArrayList<>();
        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element item = (Element) itemNodes.item(i);
            String title = childText(item, "title");
            String link = childText(item, "link");
            if (title.isBlank() || link.isBlank()) {
                continue;
            }
            items.add(new RawNewsItem(feed.outlet(), title, link, childText(item, "description"), childText(item, "pubDate")));
        }
        return items;
    }

    private static String childText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? "" : text.trim();
    }
}
