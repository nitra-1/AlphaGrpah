package com.alphagraph.corporate.newsfeed;

/** One feed's raw fetched XML body, tagged with which outlet it came from. */
record RawNewsFeedResponse(String outlet, String xml) {
}
