package com.datastax.killrvideo.it.configuration;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

public class KillrVideoProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(KillrVideoProperties.class);


    public static final String APPLICATION_NAME = "killrvideo.application.name";
    public static final String APPLICATION_INSTANCE_ID = "killrvideo.application.instance.id";
    public static final String ETCD_PORT = "killrvideo.etcd.port";
    public static final String KILLRVIDEO_DOCKER_IP = "KILLRVIDEO_DOCKER_IP";
    public static final int WAIT_TIME_IN_SECONDS = 10;


    public final String applicationName;
    public final String applicationInstanceId;
    public final int etcdPort;
    public final String dockerIp;

    public KillrVideoProperties(Environment env) {
        this.applicationName = env.getProperty(APPLICATION_NAME, "KillrVideo");
        this.applicationInstanceId = env.getProperty(APPLICATION_INSTANCE_ID, "0");
        this.etcdPort = parseInt(env.getProperty(ETCD_PORT, "2379"));

        /**
         * Need to set env variable KILLRVIDEO_DOCKER_IP before launching application
         */
        final Optional<String> dockerIp = Optional.ofNullable(System.getenv(KILLRVIDEO_DOCKER_IP));
        if (!dockerIp.isPresent()) {

            final String errorMessage = format("Cannot find environment variable %s. " +
                    "Please set it before launching KillrVideoServer", KILLRVIDEO_DOCKER_IP);
            LOGGER.error(errorMessage);
            throw new IllegalStateException(errorMessage);

        } else {
            LOGGER.info("Setting docker ip to : " + dockerIp.get());
            this.dockerIp = dockerIp.get();
        }
    }
}
