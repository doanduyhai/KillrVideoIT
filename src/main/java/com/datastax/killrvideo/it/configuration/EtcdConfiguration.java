package com.datastax.killrvideo.it.configuration;

import static com.datastax.killrvideo.it.configuration.KillrVideoProperties.WAIT_TIME_IN_SECONDS;

import java.net.URI;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.datastax.killrvideo.it.util.ServiceChecker;
import com.xqbase.etcd4j.EtcdClient;

@Configuration
public class EtcdConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdConfiguration.class);

    @Inject
    private Environment env;


    @Bean
    public KillrVideoProperties getKillrViddeoProperties() {
        return new KillrVideoProperties(env);
    }

    @Bean
    public EtcdClient connectToEtcd() throws Exception {

        final KillrVideoProperties properties = new KillrVideoProperties(env);

        final String etcdUrl = "http://" + properties.dockerIp + ":" + properties.etcdPort;

        ServiceChecker.waitForService("Etcd", properties.dockerIp, properties.etcdPort, WAIT_TIME_IN_SECONDS);

        LOGGER.info(String.format("Creating connection to etcd %s", etcdUrl));

        return new EtcdClient(URI.create(etcdUrl));
    }
}
