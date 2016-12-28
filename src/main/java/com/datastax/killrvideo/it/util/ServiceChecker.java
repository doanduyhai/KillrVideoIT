package com.datastax.killrvideo.it.util;

import static java.lang.String.format;

import java.io.IOException;
import java.net.*;

import com.xqbase.etcd4j.EtcdClient;

public class ServiceChecker {

    public static void waitForService(String service, String address, int port, int waitTimeInSec) throws Exception {

        System.out.println(format("Attempting to connect to service %s on %s:%s", service, address, port));

        while (true) {
            if (isServiceAccessible(service, address, port)) {
                return;
            } else {
                System.out.println(format("Waiting %s secs for %s to start", waitTimeInSec, service));
                Thread.sleep(waitTimeInSec * 1000);
            }
        }
    }

    public static boolean isServicePresent(EtcdClient etcdClient, String grpcServiceName) throws Exception {
        final String hostAndPort = etcdClient.get(grpcServiceName);
        if (hostAndPort != null) {
            HostAndPortSplitter.ensureValidFormat(hostAndPort,
                    format("The %s is not a valid host:port format", hostAndPort));

            final String address = HostAndPortSplitter.extractAddress(hostAndPort);
            final int port = HostAndPortSplitter.extractPort(hostAndPort);
            return isServiceAccessible(grpcServiceName, address, port);
        } else {
            return false;
        }
    }

    private static boolean isServiceAccessible(String service, String address, int port) throws Exception {
        Socket s = null;
        try {
            s = new Socket(address, port);
            s.setReuseAddress(true);
            System.out.println(format("Connection to %s:%s is working for service %s", address, port, service));
            try {
                s.close();
            } catch (IOException e) {

            }

            return true;
        } catch (Exception e) {
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ex) {
                }
            }
            return false;
        }
    }
}
