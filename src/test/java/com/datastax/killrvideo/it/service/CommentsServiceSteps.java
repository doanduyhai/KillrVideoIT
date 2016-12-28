package com.datastax.killrvideo.it.service;

import static com.datastax.killrvideo.it.configuration.KillrVideoITConfiguration.COMMENTS_SERVICE_NAME;
import static com.datastax.killrvideo.it.service.UserManagementServiceSteps.USERS;
import static com.datastax.killrvideo.it.service.VideoCatalogServiceSteps.VIDEOS;
import static com.datastax.killrvideo.it.service.VideoCatalogServiceSteps.cleanUpUserAndVideoTables;
import static com.datastax.killrvideo.it.util.TypeConverter.uuidToTimeUuid;
import static com.datastax.killrvideo.it.util.TypeConverter.uuidToUuid;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.utils.UUIDs;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import killrvideo.comments.CommentsServiceGrpc;
import killrvideo.comments.CommentsServiceGrpc.CommentsServiceBlockingStub;
import killrvideo.comments.CommentsServiceOuterClass.*;

import killrvideo.common.CommonTypes.TimeUuid;

public class CommentsServiceSteps extends AbstractSteps  {

    private static Logger LOGGER = LoggerFactory.getLogger(CommentsServiceSteps.class);
    private static AtomicReference<Boolean> SHOULD_CHECK_SERVICE= new AtomicReference<>(true);

    @Override
    protected String serviceName() {
        return COMMENTS_SERVICE_NAME;
    }

    private CommentsServiceBlockingStub commentStub;

    @Before("@comments_scenarios")
    public void init() {
        Optional.of(SHOULD_CHECK_SERVICE).map(AtomicReference::get).ifPresent(x -> {
            checkForService();
            SHOULD_CHECK_SERVICE.getAndSet(null);
        });

        commentStub = CommentsServiceGrpc.newBlockingStub(managedChannel);
        LOGGER.info("Truncating users, videos & comments tables BEFORE executing tests");
        cleanUpUserAndVideoTables(dao);
        dao.truncate("comments_by_video");
        dao.truncate("comments_by_user");
    }

    @After("@comments_scenarios")
    public void cleanup() {
        LOGGER.info("Truncating users, videos & comments tables AFTER executing tests");
        cleanUpUserAndVideoTables(dao);
        dao.truncate("comments_by_video");
        dao.truncate("comments_by_user");
    }


    @When("we have the following comments:")
    public void createCommentsOnVideo(List<CucumberVideoComment> comments) {

        for (CucumberVideoComment comment : comments) {
            CommentOnVideoRequest request = CommentOnVideoRequest
                    .newBuilder()
                    .setCommentId(uuidToTimeUuid(UUIDs.timeBased()))
                    .setComment(comment.getComment())
                    .setUserId(uuidToUuid(USERS.get(comment.getUser())))
                    .setVideoId(uuidToUuid(VIDEOS.get(comment.getVideo()).id))
                    .build();

            final CommentOnVideoResponse response = commentStub.commentOnVideo(request);

            assertThat(response)
                    .as("Cannot create comment '%s' on %s for %s",
                            comment.getComment(),
                            comment.getVideo(),
                            comment.getUser())
                    .isNotNull();
        }

    }

    @Then("^I can see the comment '(.+)' on (video\\d)$")
    public void checkForCommentOnVideo(String expectedComment, String sourceVideo) {
        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKey(sourceVideo);

        GetVideoCommentsRequest request = GetVideoCommentsRequest
                .newBuilder()
                .setVideoId(uuidToUuid(VIDEOS.get(sourceVideo).id))
                .setPageSize(100)
                .build();

        final GetVideoCommentsResponse response = commentStub.getVideoComments(request);

        assertThat(response)
                .as("Cannot see comment '%s' on %s", expectedComment, sourceVideo)
                .isNotNull();

        assertThat(response.getCommentsList().stream().map(VideoComment::getComment).collect(toList()))
                .as("Cannot see comment '%s' on %s", expectedComment, sourceVideo)
                .contains(expectedComment);
    }

    @Then("^I can see the comment '(.+)' on (video\\d) at page (\\d)$")
    public void checkForCommentOnVideoWithPaging(String expectedComment, String sourceVideo, int pageNumber) {
        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKey(sourceVideo);

        Optional<TimeUuid> startCommentId = Optional.empty();
        for(int i=1; i<pageNumber; i++) {
            startCommentId = commentOnVideoWithPaging(sourceVideo, startCommentId);
        }

        final GetVideoCommentsRequest request = GetVideoCommentsRequest
                .newBuilder()
                .setVideoId(uuidToUuid(VIDEOS.get(sourceVideo).id))
                .setStartingCommentId(startCommentId.get())
                .setPageSize(1)
                .build();

        final GetVideoCommentsResponse response = commentStub.getVideoComments(request);
        assertThat(response)
                .as("Cannot see comment on %s", sourceVideo)
                .isNotNull();

        assertThat(response.getCommentsList())
                .as("Cannot see comment on %s", sourceVideo)
                .hasSize(1);

        assertThat(response.getCommentsList().stream().map(VideoComment::getComment).collect(toList()))
                .as("Cannot see comment '%s' on %s", expectedComment, sourceVideo)
                .contains(expectedComment);
    }

    private Optional<TimeUuid> commentOnVideoWithPaging(String video, Optional<TimeUuid> startCommentId) {
        GetVideoCommentsRequest request;
        if (startCommentId.isPresent()) {
            request = GetVideoCommentsRequest
                    .newBuilder()
                    .setVideoId(uuidToUuid(VIDEOS.get(video).id))
                    .setStartingCommentId(startCommentId.get())
                    .setPageSize(1)
                    .build();
        } else {
            request = GetVideoCommentsRequest
                    .newBuilder()
                    .setVideoId(uuidToUuid(VIDEOS.get(video).id))
                    .setPageSize(1)
                    .build();
        }

        final GetVideoCommentsResponse response = commentStub.getVideoComments(request);


        assertThat(response)
                .as("Cannot see comments on %s", video)
                .isNotNull();

        assertThat(response.getCommentsList())
                .as("Cannot see comments on %s", video)
                .hasSize(1);


        return Optional.ofNullable(response.getCommentsList().get(0).getCommentId());
    }

    @And("^(user\\d) can see the comment '(.+)' on his own comments$")
    public void checkForCommentOfUser(String user, String expectedComment) {
        assertThat(USERS)
                .as("%s is unknown, please specify userXXX where XXX is a digit")
                .containsKey(user);

        GetUserCommentsRequest request = GetUserCommentsRequest
                .newBuilder()
                .setUserId(uuidToUuid(USERS.get(user)))
                .setPageSize(100)
                .build();

        final GetUserCommentsResponse response = commentStub.getUserComments(request);

        assertThat(response)
                .as("Cannot see comment '%s' for %s", expectedComment, user)
                .isNotNull();

        assertThat(response.getCommentsList().stream().map(UserComment::getComment).collect(toList()))
                .as("Cannot see comment '%s' for %s", expectedComment, user)
                .contains(expectedComment);
    }

    @And("^(user\\d) can see the comment '(.+)' on his own comments at page (\\d)$")
    public void checkForCommentOfUserWithPaging(String user, String expectedComment, int pageNumber) {

        assertThat(USERS)
                .as("%s is unknown, please specify userXXX where XXX is a digit")
                .containsKey(user);

        Optional<TimeUuid> startCommentId = Optional.empty();
        for(int i=1; i<pageNumber; i++) {
            startCommentId = commentOfUserWithPaging(user, startCommentId);
        }

        GetUserCommentsRequest request = GetUserCommentsRequest
                .newBuilder()
                .setUserId(uuidToUuid(USERS.get(user)))
                .setStartingCommentId(startCommentId.get())
                .setPageSize(1)
                .build();

        final GetUserCommentsResponse response = commentStub.getUserComments(request);

        assertThat(response)
                .as("Cannot see comments for %s", user)
                .isNotNull();

        assertThat(response.getCommentsList())
                .as("Cannot see comments for %s", user)
                .hasSize(1);

        assertThat(response.getCommentsList().stream().map(UserComment::getComment).collect(toList()))
                .as("Cannot see comment %s for %s", expectedComment, user)
                .contains(expectedComment);
    }

    private Optional<TimeUuid> commentOfUserWithPaging(String user, Optional<TimeUuid> startCommentId) {
        GetUserCommentsRequest request;
        if (startCommentId.isPresent()) {
            request = GetUserCommentsRequest
                    .newBuilder()
                    .setUserId(uuidToUuid(USERS.get(user)))
                    .setStartingCommentId(startCommentId.get())
                    .setPageSize(1)
                    .build();
        } else {
            request = GetUserCommentsRequest
                    .newBuilder()
                    .setUserId(uuidToUuid(USERS.get(user)))
                    .setPageSize(1)
                    .build();
        }

        final GetUserCommentsResponse response = commentStub.getUserComments(request);

        assertThat(response)
                .as("Cannot see comments for %s", user)
                .isNotNull();

        assertThat(response.getCommentsList())
                .as("Cannot see comments for %s", user)
                .isNotNull();

        assertThat(response.getCommentsList())
                .as("Cannot get paging state for comments of %s", user)
                .hasSize(1);

        return Optional.ofNullable(response.getCommentsList().get(0).getCommentId());
    }

    public static class CucumberVideoComment {
        private final String user;
        private final String video;
        private final String comment;

        public CucumberVideoComment(String user, String video, String comment) {
            this.user = user;
            this.video = video;
            this.comment = comment;
        }

        public String getUser() {
            return user;
        }

        public String getVideo() {
            return video;
        }

        public String getComment() {
            return comment;
        }
    }
}
