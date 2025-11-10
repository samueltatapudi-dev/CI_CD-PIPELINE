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
  && apt-get update \
  && apt-get install -y --no-install-recommends docker-ce-cli \
  && rm -rf /var/lib/apt/lists/* \
  && curl -fsSLo /usr/local/bin/kubectl "https://dl.k8s.io/release/$(curl -fsSL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" \
  && chmod +x /usr/local/bin/kubectl

# Preinstall Jenkins plugins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# JCasC
COPY casc /usr/share/jenkins/ref/casc
COPY pipeline.groovy /usr/share/jenkins/ref/casc/jobs/ci-cd-app.groovy

USER jenkins

ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc/jenkins.yaml
