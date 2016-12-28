package com.datastax.killrvideo.it.service;

import static com.datastax.killrvideo.it.configuration.KillrVideoITConfiguration.USER_SERVICE_NAME;
import static com.datastax.killrvideo.it.util.TypeConverter.uuidToUuid;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Row;
import com.datastax.killrvideo.it.util.TypeConverter;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import killrvideo.user_management.UserManagementServiceGrpc;
import killrvideo.user_management.UserManagementServiceGrpc.UserManagementServiceBlockingStub;
import killrvideo.user_management.UserManagementServiceOuterClass.*;

public class UserManagementServiceSteps extends AbstractSteps {

    private static Logger LOGGER = LoggerFactory.getLogger(UserManagementServiceSteps.class);
    private static AtomicReference<Boolean> SHOULD_CHECK_SERVICE= new AtomicReference<>(true);

    @Override
    protected String serviceName() {
        return USER_SERVICE_NAME;
    }

    private static final Map<String, UserProfile> PROFILES = new HashMap<>();
    private static final Map<String, String> ERRORS = new ConcurrentHashMap<>();
    public static Map<String, UUID> USERS = new HashMap<String, UUID>() {
        {
            put("user1", UUID.randomUUID());
            put("user2", UUID.randomUUID());
            put("user3", UUID.randomUUID());
            put("user4", UUID.randomUUID());
        }
    };

    private UserManagementServiceBlockingStub userStub;


    @Before("@user_scenarios")
    public void init() {
        Optional.of(SHOULD_CHECK_SERVICE).map(AtomicReference::get).ifPresent(x -> {
            checkForService();
            SHOULD_CHECK_SERVICE.getAndSet(null);
        });

        userStub = UserManagementServiceGrpc.newBlockingStub(managedChannel);
        LOGGER.info("Truncating users/user_credentials tables BEFORE executing tests");
        dao.truncate("user_credentials");
        dao.truncate("users");
    }

    @After("@user_scenarios")
    public void cleanup() {
        LOGGER.info("Truncating users/user_credentials tables AFTER executing tests");
        dao.truncate("user_credentials");
        dao.truncate("users");
    }

    @Given("those users already exist: (.*)")
    public void createUserWithId(List<String> users) throws Exception {
        for (String user : users) {
            assertThat(USERS)
                    .as("%s is unknown, please specify userXXX where XXX is a digit")
                    .containsKey(user);

            final CreateUserRequest request = CreateUserRequest.newBuilder()
                    .setUserId(uuidToUuid(USERS.get(user)))
                    .setEmail(RandomStringUtils.randomAlphabetic(10) + "@gmail.com")
                    .setFirstName(RandomStringUtils.randomAlphabetic(5))
                    .setLastName(RandomStringUtils.randomAlphabetic(5))
                    .setPassword(RandomStringUtils.randomAlphabetic(10))
                    .build();

            final CreateUserResponse response = userStub.createUser(request);

            assertThat(response).as("Cannot create %s", user).isNotNull();
        }

    }

    @Given("^user with email (.+) does not exist$")
    public void ensureUserDoesNotExist(String email) {
        final BoundStatement bs = dao.findUserByEmailPs.bind(email);
        final Row foundUserByEmail = dao.getOne(bs);
        assertThat(foundUserByEmail)
                .as("User with email %s should not already exist", email)
                .isNull();
    }

    @When("I create (\\d) users with email (.+) and password (.+)")
    public void createUserWithEmail(int userCount, String email, String password) throws Exception {
        List<CreateUserRequest> requests = new ArrayList();

        for (int i=1; i<= userCount; i++) {
            requests.add(CreateUserRequest.newBuilder()
                    .setUserId(TypeConverter.uuidToUuid(UUID.randomUUID()))
                    .setEmail(email)
                    .setFirstName(RandomStringUtils.randomAlphabetic(5))
                    .setLastName(RandomStringUtils.randomAlphabetic(5))
                    .setPassword(password)
                    .build());
        }

        final CountDownLatch startLatch = new CountDownLatch(userCount);
        requests.forEach(x -> threadPool.submit(createThreadForUserCreation(startLatch, userStub, x)));
        startLatch.await();
    }

    @Given("user with credentials ([^/]+)/(.+) already exists")
    public void ensureUserAlreadyExists(String email, String password) {
        final CreateUserRequest userRequest = CreateUserRequest.newBuilder()
                .setUserId(TypeConverter.uuidToUuid(UUID.randomUUID()))
                .setEmail(email)
                .setFirstName(RandomStringUtils.randomAlphabetic(5))
                .setLastName(RandomStringUtils.randomAlphabetic(5))
                .setPassword(password)
                .build();
        final CreateUserResponse createUserResponse = userStub.createUser(userRequest);
        assertThat(createUserResponse)
                .as("User with email %s and password %s has been created", email, password)
                .isNotNull();
    }

    @Then("I should be able to login with ([^/]+)/(.+)")
    public void loginWithCredentials(String email, String password) {
        final VerifyCredentialsRequest verifyCredentialsRequest = VerifyCredentialsRequest.newBuilder()
                .setEmail(email)
                .setPassword(password)
                .build();

        final VerifyCredentialsResponse verifyCredentialsResponse = userStub.verifyCredentials(verifyCredentialsRequest);

        assertThat(verifyCredentialsResponse.hasUserId())
                .as("Login with email %s and password %s is successful", email, password)
                .isTrue();
    }

    @Then("I receive the '(.+)' error message for (.+) account")
    public void checkErrorsForAccount(String errorMessage, String email) {
        assertThat(ERRORS)
                .as("Cannot find error message %s for %s account", errorMessage, email)
                .containsKey(email);
        assertThat(ERRORS.get(email))
                .as("Cannot find error message %s for %s account", errorMessage, email)
                .contains(errorMessage);

    }

    @When("I get profile of (.+)")
    public void getProfile(String email) {
        final BoundStatement bs = dao.findUserByEmailPs.bind(email);
        final Row foundUserByEmail = dao.getOne(bs);

        assertThat(foundUserByEmail).as("Cannot find user with email %s", email).isNotNull();

        final UUID userid = foundUserByEmail.getUUID("userid");

        assertThat(userid).as("User with email %s does not have a non-null userid", email).isNotNull();

        GetUserProfileRequest request = GetUserProfileRequest
                .newBuilder()
                .addUserIds(TypeConverter.uuidToUuid(userid))
                .build();

        final GetUserProfileResponse response = userStub.getUserProfile(request);

        assertThat(response).as("Cannot find user with email %s", email).isNotNull();
        assertThat(response.getProfilesList()).as("Cannot find user with email %s", email).hasSize(1);

        PROFILES.put(email, response.getProfiles(0));
    }

    @Then("the profile (.+) exists")
    public void ensureProfileDoesExist(String email) {
        assertThat(PROFILES)
                .as("Cannot find profile %s", email)
                .containsKey(email);

        final UserProfile userProfile = PROFILES.get(email);
        assertThat(userProfile.getEmail())
                .as("Cannot find profile %s", email)
                .isEqualTo(email);
    }

    private Runnable createThreadForUserCreation(
            final CountDownLatch startLatch,
            final UserManagementServiceBlockingStub stub,
            final CreateUserRequest request) {
        return () -> {
            try {
                stub.createUser(request);
            } catch(Exception ex) {
                ERRORS.putIfAbsent(request.getEmail(), ex.getMessage());
            } finally {
                startLatch.countDown();
            }
        };
    }
}
