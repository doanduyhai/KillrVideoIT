FROM openjdk:8-jdk

ARG MAVEN_VERSION=3.5.0
ARG USER_HOME_DIR="/root"
ARG SHA=beb91419245395bd69a4a6edad5ca3ec1a8b64e41457672dc687c173a495f034
ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-$MAVEN_VERSION-bin.tar.gz \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha256sum -c - \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn \
  && apt-get update \
  && apt-get install -y  tar  curl  git  \
  && rm -rf /var/lib/apt/lists/* 

ENV MAVEN_HOME=/usr/share/maven

ENV MAVEN_CONFIG=/root/.m2

#To be configured at runtime when launching the container with "--env KILLRVIDEO_DOCKER_IP=..."
ENV KILLRVIDEO_DOCKER_IP=99.99.99.99

WORKDIR /home

RUN chmod -R 755 /home  \
	&& mkdir -p /tmp/cucumber-report  \
	&& chmod -R 755 /tmp/cucumber-report \
        && mkdir /home/zeppelin \
	&& chmod -R 755 /home/zeppelin


RUN cd /home \ 
	&& git clone https://github.com/doanduyhai/KillrVideoIT.git 

COPY zeppelin /home/zeppelin
ADD startup.sh /home

RUN chmod 755 /home/startup.sh

EXPOSE 8080/tcp
EXPOSE 8123/tcp

CMD ["/bin/bash","-c","/home/startup.sh"]

#CMD ["/bin/bash"]

