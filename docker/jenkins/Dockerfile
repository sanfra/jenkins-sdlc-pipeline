FROM jenkins/jenkins:lts-jdk17

USER root

RUN apt-get update && \
    apt-get install -y wget curl nodejs npm && \
    wget -q https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz && \
    tar -xzf apache-maven-3.9.6-bin.tar.gz -C /opt && \
    ln -s /opt/apache-maven-3.9.6/bin/mvn /usr/local/bin/mvn && \
    rm apache-maven-3.9.6-bin.tar.gz && \
    rm -rf /var/lib/apt/lists/*

ENV M2_HOME=/opt/apache-maven-3.9.6
ENV CASC_JENKINS_CONFIG=/opt/jenkins/casc.yml
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY casc.yml /opt/jenkins/casc.yml

USER jenkins
RUN git config --global --add safe.directory /workspace && \
    git config --global --add safe.directory /workspace-sanfra
