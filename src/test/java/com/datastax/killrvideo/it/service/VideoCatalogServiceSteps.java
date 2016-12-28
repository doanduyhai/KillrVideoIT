package com.datastax.killrvideo.it.service;

import static com.datastax.killrvideo.it.configuration.KillrVideoITConfiguration.VIDEO_CATALOG_SERVICE_NAME;
import static com.datastax.killrvideo.it.service.UserManagementServiceSteps.USERS;
import static com.datastax.killrvideo.it.util.TypeConverter.dateToTimestamp;
import static com.datastax.killrvideo.it.util.TypeConverter.uuidToUuid;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Row;
import com.datastax.killrvideo.it.dao.CassandraDao;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import killrvideo.video_catalog.VideoCatalogServiceGrpc;
import killrvideo.video_catalog.VideoCatalogServiceGrpc.VideoCatalogServiceBlockingStub;
import killrvideo.video_catalog.VideoCatalogServiceOuterClass.*;

public class VideoCatalogServiceSteps extends AbstractSteps {

    private static Logger LOGGER = LoggerFactory.getLogger(VideoCatalogServiceSteps.class);
    private static AtomicReference<Boolean> SHOULD_CHECK_SERVICE= new AtomicReference<>(true);

    @Override
    protected String serviceName() {
        return VIDEO_CATALOG_SERVICE_NAME;
    }

    public static final Map<String, VideoNameById> VIDEOS = new HashMap<String, VideoNameById>() {
        {
            put("video1", new VideoNameById(UUID.randomUUID(), "b-wing-ucs.mp4"));
            put("video2", new VideoNameById(UUID.randomUUID(), "y-wing-ucs.mp4"));
            put("video3", new VideoNameById(UUID.randomUUID(), "x-wing-ucs.mp4"));
            put("video4", new VideoNameById(UUID.randomUUID(), "tie-fighter-ucs.mp4"));
            put("video5", new VideoNameById(UUID.randomUUID(), "mil-falcon-ucs.mp4"));
        }
    };

    public static final Map<UUID, String> VIDEOS_BY_ID = VIDEOS.entrySet()
            .stream()
            .collect(Collectors.toMap(x -> x.getValue().id, Map.Entry::getKey));

    private VideoCatalogServiceBlockingStub videoStub;

    @Before("@video_scenarios")
    public void init() {
        Optional.of(SHOULD_CHECK_SERVICE).map(AtomicReference::get).ifPresent(x -> {
            checkForService();
            SHOULD_CHECK_SERVICE.getAndSet(null);
        });

        videoStub = VideoCatalogServiceGrpc.newBlockingStub(managedChannel);
        LOGGER.info("Truncating users & videos tables BEFORE executing tests");
        cleanUpUserAndVideoTables(dao);
    }

    @After("@video_scenarios")
    public void cleanup() {
        LOGGER.info("Truncating users & videos tables AFTER executing tests");
        cleanUpUserAndVideoTables(dao);
    }

    @When("^(user\\d) submit Youtube videos:$")
    public void createVideos(String user, List<CucumberVideoDetails> videos) throws Exception {

        for (CucumberVideoDetails video : videos) {

            assertThat(video.tags)
                    .as("There should be at least one tag provided for %s", video.id)
                    .isNotEmpty();

            final SubmitYouTubeVideoRequest request = SubmitYouTubeVideoRequest
                    .newBuilder()
                    .setName(video.name)
                    .setVideoId(uuidToUuid(VIDEOS.get(video.id).id))
                    .setUserId(uuidToUuid(USERS.get(user)))
                    .setDescription(video.description)
                    .setYouTubeVideoId(video.url)
                    .addAllTags(Arrays.asList(video.tags.split(",")))
                    .build();

            final SubmitYouTubeVideoResponse response = videoStub.submitYouTubeVideo(request);

            assertThat(response).as("Cannot create %s for %s", video.id, user).isNotNull();
        }
    }

    @Then("I can retrieve (video\\d) by id")
    public void getVideoById(String video) {
        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKey(video);

        final VideoNameById videoNameById = VIDEOS.get(video);

        GetVideoRequest request = GetVideoRequest
                .newBuilder()
                .setVideoId(uuidToUuid(videoNameById.id))
                .build();

        final GetVideoResponse response = videoStub.getVideo(request);

        assertThat(response)
                .as("Cannot find %s", video)
                .isNotNull();

        assertThat(response.getName())
                .as("Cannot find %s", video)
                .isEqualTo(videoNameById.name);
    }

    @Then("I can get preview of: (.*)")
    public void getVideosPreview(List<String> expectedVideos) {
        final GetVideoPreviewsRequest.Builder builder = GetVideoPreviewsRequest.newBuilder();
        for (String video : expectedVideos) {
            assertThat(VIDEOS)
                    .as("%s is unknown, please specify videoXXX where XXX is a digit")
                    .containsKey(video);
            builder.addVideoIds(uuidToUuid(VIDEOS.get(video).id));
        }

        final GetVideoPreviewsResponse response = videoStub.getVideoPreviews(builder.build());

        assertThat(response)
                .as("Cannot get previews for %s", String.join(", ", expectedVideos))
                .isNotNull();

        final int expectedVideoCount = expectedVideos.size();

        assertThat(response.getVideoPreviewsList())
                .as("Cannot get previews for %s", String.join(", ", expectedVideos))
                .hasSize(expectedVideoCount);

        assertThat(response.getVideoPreviewsList().stream().map(VideoPreview::getName).collect(toList()))
                .as("Cannot get previews for %s", String.join(", ", expectedVideos))
                .containsExactly(expectedVideos.stream().map(x -> VIDEOS.get(x).name).collect(toList()).toArray(new String[expectedVideoCount]));
    }

    @Then("latest videos preview contains: (.*)")
    public void getLatestVideosPreview(List<String> expectedVideos) {
        final int expectedVideoCount = expectedVideos.size();
        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKeys(expectedVideos.toArray(new String[expectedVideoCount]));

        GetLatestVideoPreviewsRequest request = GetLatestVideoPreviewsRequest
                .newBuilder()
                .setPageSize(100)
                .build();

        final GetLatestVideoPreviewsResponse response = videoStub.getLatestVideoPreviews(request);

        assertThat(response)
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .isNotNull();

        assertThat(response.getVideoPreviewsList())
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .hasSize(expectedVideoCount);

        assertThat(response.getVideoPreviewsList().stream().map(VideoPreview::getName).collect(toList()))
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .containsExactly(expectedVideos.stream().map(x -> VIDEOS.get(x).name).collect(toList()).toArray(new String[expectedVideoCount]));
    }

    @Then("(user\\d) videos preview contains: (.*)")
    public void getUserVideosPreview(String user, List<String> expectedVideos) {

        final int expectedVideoCount = expectedVideos.size();

        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKeys(expectedVideos.toArray(new String[expectedVideoCount]));

        assertThat(USERS)
                .as("%s is unknown, please specify userXXX where XXX is a digit")
                .containsKey(user);

        GetUserVideoPreviewsRequest request = GetUserVideoPreviewsRequest
                .newBuilder()
                .setUserId(uuidToUuid(USERS.get(user)))
                .setPageSize(100)
                .build();

        final GetUserVideoPreviewsResponse response = videoStub.getUserVideoPreviews(request);

        assertThat(response)
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .isNotNull();

        assertThat(response.getVideoPreviewsList())
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .hasSize(expectedVideoCount);

        assertThat(response.getVideoPreviewsList().stream().map(VideoPreview::getName).collect(toList()))
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .containsExactly(expectedVideos.stream().map(x -> VIDEOS.get(x).name).collect(toList()).toArray(new String[expectedVideoCount]));
    }

    @Then("latest videos preview starting from (video\\d) contains: (.*)")
    public void getLatestVideosPreviewWithStartVideoId(String startVideo, List<String> videos) {

        final int expectedVideoCount = videos.size();

        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKeys(videos.toArray(new String[expectedVideoCount]));

        final UUID startVideoId = VIDEOS.get(startVideo).id;
        final Row row = dao.session.execute(dao.findVideoByIdPs.bind(startVideoId)).one();

        assertThat(row)
                .as("Cannot load %s info", startVideo)
                .isNotNull();

        final Date startVideoAddedDate = row.getTimestamp("added_date");
        assertThat(startVideoAddedDate)
                .as("Cannot find added_date for %s", startVideo)
                .isNotNull();


        GetLatestVideoPreviewsRequest request = GetLatestVideoPreviewsRequest
                .newBuilder()
                .setStartingVideoId(uuidToUuid(startVideoId))
                .setStartingAddedDate(dateToTimestamp(startVideoAddedDate))
                .setPageSize(2)
                .build();

        final GetLatestVideoPreviewsResponse response = videoStub.getLatestVideoPreviews(request);

        assertThat(response)
                .as("Cannot get latest videos preview for %s", String.join(" ,", videos))
                .isNotNull();

        assertThat(response.getVideoPreviewsList())
                .as("Cannot get latest videos preview for %s", String.join(" ,", videos))
                .hasSize(expectedVideoCount);

        assertThat(response.getVideoPreviewsList().stream().map(VideoPreview::getName).collect(toList()))
                .as("Cannot get latest videos preview for %s", String.join(" ,", videos))
                .containsExactly(videos.stream().map(x -> VIDEOS.get(x).name).collect(toList()).toArray(new String[expectedVideoCount]));
    }

    @Then("latest videos preview at page (\\d) contains: (.*)")
    public void getLatestVideosPreviewWithPaging(int pageNumber, List<String> expectedVideos) {
        final int expectedVideoCount = expectedVideos.size();
        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKeys(expectedVideos.toArray(new String[expectedVideoCount]));

        Optional<String> pagingState = Optional.empty();
        for (int i=1; i<pageNumber; i++) {
            pagingState = fetchLatestVideosPages(pagingState);
        }

        GetLatestVideoPreviewsRequest request = GetLatestVideoPreviewsRequest
                .newBuilder()
                .setPagingState(pagingState.get())
                .setPageSize(3)
                .build();

        final GetLatestVideoPreviewsResponse response = videoStub.getLatestVideoPreviews(request);

        assertThat(response)
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .isNotNull();

        assertThat(response.getVideoPreviewsList())
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .hasSize(expectedVideoCount);

        assertThat(response.getVideoPreviewsList().stream().map(VideoPreview::getName).collect(toList()))
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .containsExactly(expectedVideos.stream().map(x -> VIDEOS.get(x).name).collect(toList()).toArray(new String[expectedVideoCount]));
    }

    @Then("(user\\d) videos preview starting from (video\\d) contains: (.*)")
    public void getUserVideosPreviewWithStartVideoId(String user, String startVideo, List<String> expectedVideos) {

        final int expectedVideoCount = expectedVideos.size();

        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKeys(expectedVideos.toArray(new String[expectedVideoCount]));

        assertThat(USERS)
                .as("%s is unknown, please specify userXXX where XXX is a digit")
                .containsKey(user);


        final UUID startVideoId = VIDEOS.get(startVideo).id;
        final Row row = dao.session.execute(dao.findVideoByIdPs.bind(startVideoId)).one();

        assertThat(row)
                .as("Cannot load %s info", startVideo)
                .isNotNull();

        final Date startVideoAddedDate = row.getTimestamp("added_date");
        assertThat(startVideoAddedDate)
                .as("Cannot find added_date for %s", startVideo)
                .isNotNull();

        GetUserVideoPreviewsRequest request = GetUserVideoPreviewsRequest
                .newBuilder()
                .setUserId(uuidToUuid(USERS.get(user)))
                .setStartingVideoId(uuidToUuid(startVideoId))
                .setStartingAddedDate(dateToTimestamp(startVideoAddedDate))
                .setPageSize(2)
                .build();

        final GetUserVideoPreviewsResponse response = videoStub.getUserVideoPreviews(request);

        assertThat(response)
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .isNotNull();

        assertThat(response.getVideoPreviewsList())
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .hasSize(expectedVideoCount);

        assertThat(response.getVideoPreviewsList().stream().map(VideoPreview::getName).collect(toList()))
                .as("Cannot get latest videos preview for %s", String.join(" ,", expectedVideos))
                .containsExactly(expectedVideos.stream().map(x -> VIDEOS.get(x).name).collect(toList()).toArray(new String[expectedVideoCount]));
    }

    @Then("(user\\d) videos preview at page (\\d) contains: (.*)")
    public void getUserVideosPreviewWithPagingState(String user, int pageNumber, List<String> expectedVideos) {
        final int expectedVideoCount = expectedVideos.size();

        assertThat(VIDEOS)
                .as("%s is unknown, please specify videoXXX where XXX is a digit")
                .containsKeys(expectedVideos.toArray(new String[expectedVideoCount]));

        assertThat(USERS)
                .as("%s is unknown, please specify userXXX where XXX is a digit")
                .containsKey(user);

        Optional<String> pagingState = Optional.empty();
        for (int i=1; i<pageNumber; i++) {
            pagingState = fetchUserVideosPages(user, pagingState);
        }

        GetUserVideoPreviewsRequest request = GetUserVideoPreviewsRequest
                .newBuilder()
                .setUserId(uuidToUuid(USERS.get(user)))
                .setPagingState(pagingState.get())
                .setPageSize(2)
                .build();

        final GetUserVideoPreviewsResponse response = videoStub.getUserVideoPreviews(request);

        assertThat(response)
                .as("Cannot get %s videos preview for %s", user, String.join(" ,", expectedVideos))
                .isNotNull();

        assertThat(response.getVideoPreviewsList())
                .as("Cannot get %s videos preview for %s", user, String.join(" ,", expectedVideos))
                .hasSize(expectedVideoCount);

        assertThat(response.getVideoPreviewsList().stream().map(VideoPreview::getName).collect(toList()))
                .as("Cannot get %s videos preview for %s", user, String.join(" ,", expectedVideos))
                .containsExactly(expectedVideos.stream().map(x -> VIDEOS.get(x).name).collect(toList()).toArray(new String[expectedVideoCount]));
    }

    private Optional<String> fetchLatestVideosPages(Optional<String> pagingState) {
        GetLatestVideoPreviewsRequest request;
        if (pagingState.isPresent()) {
            request = GetLatestVideoPreviewsRequest
                    .newBuilder()
                    .setPagingState(pagingState.get())
                    .setPageSize(3)
                    .build();

        } else {
            request = GetLatestVideoPreviewsRequest
                    .newBuilder()
                    .setPageSize(3)
                    .build();
        }

        final GetLatestVideoPreviewsResponse response = videoStub.getLatestVideoPreviews(request);

        assertThat(response)
                .as("Cannot fetch latest videos with fetch size == 3")
                .isNotNull();

        assertThat(response.getVideoPreviewsList())
                .as("Cannot fetch latest videos with fetch size == 3")
                .hasSize(3);

        assertThat(response.getPagingState())
                .as("There is no latest videos remaining for next page")
                .isNotEmpty();

        return Optional.of(response.getPagingState());
    }

    private Optional<String> fetchUserVideosPages(String user, Optional<String> pagingState) {
        GetUserVideoPreviewsRequest request;
        if (pagingState.isPresent()) {
            request = GetUserVideoPreviewsRequest
                    .newBuilder()
                    .setUserId(uuidToUuid(USERS.get(user)))
                    .setPagingState(pagingState.get())
                    .setPageSize(2)
                    .build();

        } else {
            request = GetUserVideoPreviewsRequest
                    .newBuilder()
                    .setUserId(uuidToUuid(USERS.get(user)))
                    .setPageSize(2)
                    .build();
        }

        final GetUserVideoPreviewsResponse response = videoStub.getUserVideoPreviews(request);

        assertThat(response)
                .as("Cannot fetch %s videos with fetch size == 2", user)
                .isNotNull();

        assertThat(response.getVideoPreviewsList())
                .as("Cannot fetch %s videos with fetch size == 2", user)
                .hasSize(2);

        assertThat(response.getPagingState())
                .as("There is no %s videos remaining for next page", user)
                .isNotEmpty();

        return Optional.of(response.getPagingState());
    }

    public static class VideoNameById {

        public final UUID id;
        public final String name;
        public VideoNameById(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

    }

    public static class CucumberVideoDetails {

        public final String id;
        public final String name;
        public final String description;
        public final String tags;
        public final String url;

        public CucumberVideoDetails(String id, String name, String description, String tags, String url) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.tags = tags;

            this.url = url;
        }
    }

    public static void cleanUpUserAndVideoTables(CassandraDao dao) {
        dao.truncate("user_credentials");
        dao.truncate("users");
        dao.truncate("videos");
        dao.truncate("user_videos");
        dao.truncate("latest_videos");
        dao.truncate("videos_by_tag");
        dao.truncate("tags_by_letter");
    }
}
