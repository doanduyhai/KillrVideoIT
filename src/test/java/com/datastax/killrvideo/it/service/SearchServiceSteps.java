package com.datastax.killrvideo.it.service;

import static com.datastax.killrvideo.it.configuration.KillrVideoITConfiguration.SEARCH_SERVICE_NAME;
import static com.datastax.killrvideo.it.service.VideoCatalogServiceSteps.VIDEOS;
import static com.datastax.killrvideo.it.service.VideoCatalogServiceSteps.VIDEOS_BY_ID;
import static com.datastax.killrvideo.it.service.VideoCatalogServiceSteps.cleanUpUserAndVideoTables;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import killrvideo.search.SearchServiceGrpc;
import killrvideo.search.SearchServiceGrpc.SearchServiceBlockingStub;
import killrvideo.search.SearchServiceOuterClass.*;

public class SearchServiceSteps extends AbstractSteps {

    private static Logger LOGGER = LoggerFactory.getLogger(SearchServiceSteps.class);
    private static AtomicReference<Boolean> SHOULD_CHECK_SERVICE= new AtomicReference<>(true);

    @Override
    protected String serviceName() {
        return SEARCH_SERVICE_NAME;
    }

    private SearchServiceBlockingStub searchStub;

    @Before("@search_scenarios")
    public void init() {
        Optional.of(SHOULD_CHECK_SERVICE).map(AtomicReference::get).ifPresent(x -> {
            checkForService();
            SHOULD_CHECK_SERVICE.getAndSet(null);
        });

        searchStub = SearchServiceGrpc.newBlockingStub(managedChannel);
        LOGGER.info("Truncating users & videos BEFORE executing tests");
        cleanUpUserAndVideoTables(dao);
    }

    @After("@search_scenarios")
    public void cleanup() {
        LOGGER.info("Truncating users & videos tables AFTER executing tests");
        cleanUpUserAndVideoTables(dao);
    }

    @Then("searching videos with tag (.+) gives: (.*)")
    public void searchVideosByTag(String tag, List<String> expectedVideos) {

        final int expectedVideoCount = expectedVideos.size();

        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKeys(expectedVideos.toArray(new String[expectedVideoCount]));

        assertThat(tag)
                .as("A non-empty tag should be provided for video searching")
                .isNotEmpty();

        SearchVideosRequest request = SearchVideosRequest
                .newBuilder()
                .setQuery(tag)
                .setPageSize(100)
                .build();

        final SearchVideosResponse response = searchStub.searchVideos(request);

        assertThat(response)
                .as("Find 0 video with tag %s", tag)
                .isNotNull();


        assertThat(response.getVideosList())
                .as("There should be %s videos having tag %s", expectedVideoCount, tag)
                .hasSize(expectedVideoCount);

        assertThat(response.getVideosList()
                    .stream()
                    .map(SearchResultsVideoPreview::getVideoId)
                    .map(x -> VIDEOS_BY_ID.get(UUID.fromString(x.getValue())))
                    .collect(toList()))
                .as("Found videos with tag %s do not match %s", tag,
                        String.join(", ", expectedVideos))
                .containsAll(expectedVideos);
    }

    @Then("^I should be suggested tags (.*) for the word (.+)$")
    public void getTagsSuggestion(List<String> expectedTags, String word) {

        assertThat(expectedTags)
                .as("Please provide expected tags for word %s", word)
                .isNotEmpty();

        assertThat(word)
                .as("Cannot get tags suggestion for empty word")
                .isNotEmpty();

        GetQuerySuggestionsRequest request = GetQuerySuggestionsRequest
                .newBuilder()
                .setQuery(word)
                .setPageSize(100)
                .build();

        final GetQuerySuggestionsResponse response = searchStub.getQuerySuggestions(request);

        assertThat(response)
                .as("Cannot find tags suggestions for word %s", word)
                .isNotNull();

        final List<String> suggestionsList = response.getSuggestionsList();

        assertThat(suggestionsList)
                .as("Cannot find tags suggestions for word %s", word)
                .isNotEmpty();

        assertThat(suggestionsList)
                .as("The suggested tags %s do not match the expected tags %s",
                        String.join(", ", suggestionsList),
                        String.join(", ", expectedTags))
                .containsExactly(expectedTags.toArray(new String[expectedTags.size()]));
    }
}
