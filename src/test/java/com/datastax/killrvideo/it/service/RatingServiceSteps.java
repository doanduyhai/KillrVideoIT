package com.datastax.killrvideo.it.service;

import static com.datastax.killrvideo.it.configuration.KillrVideoITConfiguration.RATINGS_SERVICE_NAME;
import static com.datastax.killrvideo.it.service.UserManagementServiceSteps.USERS;
import static com.datastax.killrvideo.it.service.VideoCatalogServiceSteps.VIDEOS;
import static com.datastax.killrvideo.it.service.VideoCatalogServiceSteps.cleanUpUserAndVideoTables;
import static com.datastax.killrvideo.it.util.TypeConverter.uuidToUuid;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import killrvideo.ratings.RatingsServiceGrpc;
import killrvideo.ratings.RatingsServiceGrpc.RatingsServiceBlockingStub;
import killrvideo.ratings.RatingsServiceOuterClass.*;

public class RatingServiceSteps extends AbstractSteps {

    private static Logger LOGGER = LoggerFactory.getLogger(RatingServiceSteps.class);
    private static AtomicReference<Boolean> SHOULD_CHECK_SERVICE= new AtomicReference<>(true);

    @Override
    protected String serviceName() {
        return RATINGS_SERVICE_NAME;
    }

    private RatingsServiceBlockingStub ratingStub;

    @Before("@ratings_scenarios")
    public void init() {
        Optional.of(SHOULD_CHECK_SERVICE).map(AtomicReference::get).ifPresent(x -> {
            checkForService();
            SHOULD_CHECK_SERVICE.getAndSet(null);
        });

        ratingStub = RatingsServiceGrpc.newBlockingStub(managedChannel);
        LOGGER.info("Truncating users, videos & ratings tables BEFORE executing tests");
        cleanUpUserAndVideoTables(dao);
        dao.truncate("video_ratings");
        dao.truncate("video_ratings_by_user");
    }

    @After("@ratings_scenarios")
    public void cleanup() {
        LOGGER.info("Truncating users, videos & ratings tables AFTER executing tests");
        cleanUpUserAndVideoTables(dao);
        dao.truncate("video_ratings");
        dao.truncate("video_ratings_by_user");
    }

    @When("(user\\d) rates (video\\d) (\\d) stars")
    public void rateVideo(String user, String video, int starNumber) {

        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKeys(video);

        assertThat(USERS)
                .as("%s is unknown, please specify userXXX where XXX is a digit")
                .containsKey(user);

        assertThat(starNumber)
                .as("Rating star number should be between 1 and 5 included")
                .isBetween(1, 5);

        RateVideoRequest request = RateVideoRequest
                .newBuilder()
                .setRating(starNumber)
                .setUserId(uuidToUuid(USERS.get(user)))
                .setVideoId(uuidToUuid(VIDEOS.get(video).id))
                .build();

        final RateVideoResponse response = ratingStub.rateVideo(request);

        assertThat(response)
                .as("Cannot rate %s for %s with %s stars", video, user, starNumber)
                .isNotNull();
    }

    @Then("(video\\d) has (\\d+) ratings and total (\\d+) stars")
    public void getRatings(String video, long expectedRatingCount, long expectedTotalStars) {

        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKeys(video);

        assertThat(expectedRatingCount)
                .as("Rating count %s for %s should be positive", expectedRatingCount, video)
                .isGreaterThanOrEqualTo(0L);

        assertThat(expectedTotalStars)
                .as("Total stars count %s for %s should be positive", expectedTotalStars, video)
                .isGreaterThanOrEqualTo(0L);

        GetRatingRequest request = GetRatingRequest
                .newBuilder()
                .setVideoId(uuidToUuid(VIDEOS.get(video).id))
                .build();

        final GetRatingResponse response = ratingStub.getRating(request);

        assertThat(response)
                .as("Cannot find rating for %s", video)
                .isNotNull();

        assertThat(response.getRatingsCount())
                .as("Rating count for %s should be %s", video, expectedRatingCount)
                .isEqualTo(expectedRatingCount);

        assertThat(response.getRatingsTotal())
                .as("Total stars for %s should be %s", video, expectedTotalStars)
                .isEqualTo(expectedTotalStars);
    }

    @Then("(user\\d) rating for (video\\d) has (\\d+) stars")
    public void getUserRatings(String user, String targetVideo, int expectedTotalStars) {

        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKeys(targetVideo);


        assertThat(USERS)
                .as("%s is unknown, please specify userXXX where XXX is a digit")
                .containsKey(user);

        assertThat(expectedTotalStars)
                .as("Total stars count %s for %s should be positive", expectedTotalStars, targetVideo)
                .isGreaterThanOrEqualTo(0);

        GetUserRatingRequest request = GetUserRatingRequest
                .newBuilder()
                .setUserId(uuidToUuid(USERS.get(user)))
                .setVideoId(uuidToUuid(VIDEOS.get(targetVideo).id))
                .build();

        final GetUserRatingResponse response = ratingStub.getUserRating(request);

        assertThat(response)
                .as("Cannot find rating for %s and %s", targetVideo, user)
                .isNotNull();

        assertThat(response.getRating())
                .as("Total stars for %s by %s should be %s", targetVideo, user, expectedTotalStars)
                .isEqualTo(expectedTotalStars);
    }
}
