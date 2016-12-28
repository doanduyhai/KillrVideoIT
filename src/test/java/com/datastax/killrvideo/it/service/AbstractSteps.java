package com.datastax.killrvideo.it.service;

import static java.lang.String.format;

import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import com.datastax.killrvideo.it.configuration.KillrVideoProperties;
import com.datastax.killrvideo.it.dao.CassandraDao;
import com.datastax.killrvideo.it.util.ServiceChecker;
import com.xqbase.etcd4j.EtcdClient;

import cucumber.api.PendingException;
import io.grpc.ManagedChannel;

@ContextConfiguration
@SpringBootTest
public abstract class AbstractSteps {

    @Inject
    ManagedChannel managedChannel;

    @Inject
    CassandraDao dao;

    @Inject
    ExecutorService threadPool;

    @Inject
    KillrVideoProperties properties;

    @Inject
    EtcdClient etcdClient;

    protected abstract String serviceName();

    protected void checkForService() {
        final String grpcServiceUrl = "killrvideo/services/"
                + serviceName()
                + "/"
                + properties.applicationName
                + ":"
                + properties.applicationInstanceId;

        try {
            if (!ServiceChecker.isServicePresent(etcdClient, grpcServiceUrl)) {
                throw new PendingException(format("Please implement service %s on the KillrVideoServer",serviceName()));
            }

        } catch (Exception e) {
            throw new PendingException(format("Please implement service %s on the KillrVideoServer",serviceName()));
        }

    }
}
