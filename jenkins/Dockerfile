FROM jenkins/jenkins:lts-jdk17

USER root

# Install Docker CLI, kubectl, curl, jq, and git
RUN apt-get update \
  && apt-get install -y --no-install-recommends \
     ca-certificates curl gnupg lsb-release jq git \
  && mkdir -p /etc/apt/keyrings \
  && curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
  && echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \
    $(. /etc/os-release && echo $VERSION_CODENAME) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null \
  && curl -fsSLo /usr/share/keyrings/kubernetes-archive-keyring.gpg https://packages.cloud.google.com/apt/doc/apt-key.gpg \
  && echo "deb [signed-by=/usr/share/keyrings/kubernetes-archive-keyring.gpg] https://apt.kubernetes.io/ kubernetes-xenial main" | tee /etc/apt/sources.list.d/kubernetes.list \
  && apt-get update \
  && apt-get install -y --no-install-recommends docker-ce-cli kubectl \
  && rm -rf /var/lib/apt/lists/*

# Preinstall Jenkins plugins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# JCasC
COPY casc /usr/share/jenkins/ref/casc

USER jenkins

ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc/jenkins.yaml

