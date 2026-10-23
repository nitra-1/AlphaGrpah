package com.alphagraph.corporate.knowledge;

/** Gemini couldn't serve a call right now - quota, overload or outage, after the configured waits. Never a problem with the document itself. */
class GeminiUnavailableException extends ExtractionDeferredException {

    GeminiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
