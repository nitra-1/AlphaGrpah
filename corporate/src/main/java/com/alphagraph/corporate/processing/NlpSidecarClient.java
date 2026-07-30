package com.alphagraph.corporate.processing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the Python NLP sidecar's {@code POST /documents/process} endpoint - the stateless
 * OCR/Parse -> Extract -> Chunk -> Entity Extraction -> Embeddings stages of the Document
 * Pipeline, per docs/001_System_Architecture.md §5 ("corporate ... module[s]" call the sidecar
 * directly over REST; no intelligence-bridging needed since this is an external service call, not
 * a cross-domain-module dependency).
 *
 * {@link MultipartBodyBuilder} is required here, not a hand-built {@code LinkedMultiValueMap} -
 * without it the part's filename/content-type never reach the server correctly and FastAPI
 * reports the file field as entirely missing (confirmed live: a raw {@code MultiValueMap<String,
 * Object>} produced a 422 "field required" even though the exact same bytes worked fine via curl).
 *
 * <p>The request factory is forced to {@link SimpleClientHttpRequestFactory} rather than
 * whichever default Spring Boot autoconfigures - the JDK's {@code java.net.http.HttpClient}
 * (Spring Boot's default when present) attempts an HTTP/2 cleartext ("h2c") upgrade on every
 * request, which uvicorn's plain HTTP/1.1 server doesn't understand. Confirmed live: uvicorn
 * logged "Unsupported upgrade request" and "Invalid HTTP request received" repeatedly, and every
 * call failed with a bodyless 422 - the connection was corrupted by the rejected upgrade attempt,
 * not by anything wrong with the multipart body itself.
 */
@Component
public class NlpSidecarClient {

    private final RestClient restClient;
    private final String baseUrl;

    @Autowired
    public NlpSidecarClient(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.nlp-sidecar.base-url:http://localhost:8000}") String baseUrl
    ) {
        this.restClient = restClientBuilder.requestFactory(new SimpleClientHttpRequestFactory()).build();
        this.baseUrl = baseUrl;
    }

    SidecarProcessedDocumentResponse process(byte[] pdfBytes, String filename) {
        ByteArrayResource fileResource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultipartBodyBuilder multipartBuilder = new MultipartBodyBuilder();
        multipartBuilder.part("file", fileResource).filename(filename).contentType(MediaType.APPLICATION_PDF);
        MultiValueMap<String, HttpEntity<?>> body = multipartBuilder.build();

        try {
            return restClient.post()
                .uri(baseUrl + "/documents/process")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(SidecarProcessedDocumentResponse.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("NLP sidecar call failed for " + filename + ": " + e.getMessage(), e);
        }
    }
}
