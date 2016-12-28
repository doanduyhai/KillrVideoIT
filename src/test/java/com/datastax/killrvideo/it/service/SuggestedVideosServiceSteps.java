package com.datastax.killrvideo.it.service;

import static com.datastax.killrvideo.it.configuration.KillrVideoITConfiguration.SUGGESTED_VIDEOS_SERVICE_NAME;
import static com.datastax.killrvideo.it.service.VideoCatalogServiceSteps.VIDEOS;
import static com.datastax.killrvideo.it.service.VideoCatalogServiceSteps.VIDEOS_BY_ID;
import static com.datastax.killrvideo.it.service.VideoCatalogServiceSteps.cleanUpUserAndVideoTables;
import static com.datastax.killrvideo.it.util.TypeConverter.uuidToUuid;
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
import killrvideo.suggested_videos.SuggestedVideoServiceGrpc;
import killrvideo.suggested_videos.SuggestedVideoServiceGrpc.SuggestedVideoServiceBlockingStub;
import killrvideo.suggested_videos.SuggestedVideosService.GetRelatedVideosRequest;
import killrvideo.suggested_videos.SuggestedVideosService.GetRelatedVideosResponse;
import killrvideo.suggested_videos.SuggestedVideosService.SuggestedVideoPreview;

public class SuggestedVideosServiceSteps extends AbstractSteps {

    private static Logger LOGGER = LoggerFactory.getLogger(SuggestedVideosServiceSteps.class);
    private static AtomicReference<Boolean> SHOULD_CHECK_SERVICE= new AtomicReference<>(true);

    @Override
    protected String serviceName() {
        return SUGGESTED_VIDEOS_SERVICE_NAME;
    }

    private SuggestedVideoServiceBlockingStub suggestedStub;

    @Before("@suggested_videos_scenarios")
    public void init() {
        Optional.of(SHOULD_CHECK_SERVICE).map(AtomicReference::get).ifPresent(x -> {
            checkForService();
            SHOULD_CHECK_SERVICE.getAndSet(null);
        });

        suggestedStub = SuggestedVideoServiceGrpc.newBlockingStub(managedChannel);
        LOGGER.info("Truncating users & videos tables BEFORE executing tests");
        cleanUpUserAndVideoTables(dao);
    }

    @After("@suggested_videos_scenarios")
    public void cleanup() {
        LOGGER.info("Truncating users & videos tables AFTER executing tests");
        cleanUpUserAndVideoTables(dao);
    }

    @Then("user who likes (video\\d) should be suggested: (.*)")
    public void getRelatedVideos(String sourceVideo, List<String> expectedRelatedVideos) {

        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKey(sourceVideo);

        assertThat(expectedRelatedVideos)
                .as("Expected related videos should not be empty")
                .isNotEmpty();

        GetRelatedVideosRequest request = GetRelatedVideosRequest
                .newBuilder()
                .setPageSize(100)
                .setVideoId(uuidToUuid(VIDEOS.get(sourceVideo).id))
                .build();

        final GetRelatedVideosResponse response = suggestedStub.getRelatedVideos(request);

        assertThat(response)
                .as("Cannot find any related videos for source %s ", sourceVideo)
                .isNotNull();

        assertThat(response.getVideosList())
                .as("Related videos found for source %s do not match expected %s",
                        sourceVideo, String.join(", ", expectedRelatedVideos))
                .hasSize(expectedRelatedVideos.size());

        assertThat(response.getVideosList()
                .stream()
                .map(SuggestedVideoPreview::getVideoId)
                .map(x -> VIDEOS_BY_ID.get(UUID.fromString(x.getValue())))
                .collect(toList()))
                .as("Related videos found for source %s do not match expected %s",
                        sourceVideo, String.join(", ", expectedRelatedVideos))
                .containsAll(expectedRelatedVideos);
    }
}
