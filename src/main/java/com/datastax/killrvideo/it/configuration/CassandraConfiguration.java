package com.datastax.killrvideo.it.configuration;

import static com.datastax.killrvideo.it.configuration.KillrVideoProperties.WAIT_TIME_IN_SECONDS;
import static java.lang.String.format;

import java.util.List;
import javax.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.killrvideo.it.util.HostAndPortSplitter;
import com.datastax.killrvideo.it.util.ServiceChecker;
import com.xqbase.etcd4j.EtcdClient;
import com.xqbase.etcd4j.EtcdNode;

@Configuration
public class CassandraConfiguration {

    private static final String CLUSTER_NAME = "killrvideo";
    private static final String KEYSPACE_NAME = "killrvideo";

    @Inject
    private EtcdClient etcdClient;

    @Bean(destroyMethod = "close")
    public Session getSession() throws Exception {
        final List<EtcdNode> etcdNodes = etcdClient.listDir("killrvideo/services/cassandra");
        if (CollectionUtils.isEmpty(etcdNodes)) {
            throw new IllegalStateException(format("Cannot find any Cassandra service in etcd. " +
                    "Please wait until Cassandra has successfully started"));
        } else {
            final EtcdNode cassandraAddress = etcdNodes.get(0);
            final String hostAndPort = cassandraAddress.value;
            assert hostAndPort != null;
            HostAndPortSplitter.ensureValidFormat(hostAndPort,
                    format("The %s is not a valid host:port format", hostAndPort));

            final String address = HostAndPortSplitter.extractAddress(hostAndPort);
            final int port = HostAndPortSplitter.extractPort(hostAndPort);

            ServiceChecker.waitForService("Cassandra", address, port, WAIT_TIME_IN_SECONDS);

            final Cluster cluster = Cluster
                    .builder()
                    .addContactPoint(address)
                    .withPort(port)
                    .withClusterName(CLUSTER_NAME)
                    .build();

            return cluster.connect();
        }

    }
}
