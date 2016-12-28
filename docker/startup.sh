#!/bin/bash

cd KillrVideoIT  
/usr/bin/mvn clean compile
/usr/bin/mvn dependency:copy-dependencies
/usr/bin/mvn dependency:resolve-plugins
/home/zeppelin/bin/zeppelin-daemon.sh start &
cd /tmp/cucumber-report && python -m SimpleHTTPServer 8123
