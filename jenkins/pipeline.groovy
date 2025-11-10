// This file mirrors the inline pipeline in JCasC. Kept for reference/editing.
// If you prefer, you can change JCasC to read from this file instead of inline.
pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }
  environment {
    APP_REPO_URL = "${APP_REPO_URL}"
    APP_REPO_BRANCH = "${APP_REPO_BRANCH}"
    IMAGE_REPO = "${IMAGE_REPO}"
    KUBE_NAMESPACE = "${KUBE_NAMESPACE}"
  }
  stages {
    stage('Checkout') {
      steps {
        git branch: env.APP_REPO_BRANCH, url: env.APP_REPO_URL
        script {
          def versionFile = 'VERSION'
          if (fileExists(versionFile)) {
            env.APP_VERSION = readFile(versionFile).trim()
          } else {
            env.APP_VERSION = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
          }
          echo "Using version: ${env.APP_VERSION}"
        }
      }
    }
    stage('Unit Tests (pytest)') {
      steps {
        sh label: 'Run pytest in Python container (skip if none)', script: '''
          set -euxo pipefail
          docker run --rm \
            -v "$PWD":/app -w /app \
            python:3.11 bash -lc "\
              set -euxo pipefail; \
              python -m pip install --upgrade pip; \
              [ -f requirements.txt ] && pip install -r requirements.txt || true; \
              pip install pytest >/dev/null 2>&1 || true; \
              if [ -d tests ] || ls -1 *test*.py 2>/dev/null | head -n1 >/dev/null; then \
                echo 'Tests detected; running pytest'; \
                pytest -q; \
              else \
                echo 'No tests found; skipping pytest'; \
              fi"
        '''
      }
    }
    stage('Code Quality (SonarQube)') {
      steps {
        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_LOGIN')]) {
          sh label: 'Sonar Scanner CLI (skip if no token)', script: '''
            set -euxo pipefail
            if [ "$SONAR_LOGIN" = "changeme" ] || [ -z "$SONAR_LOGIN" ]; then
              echo "Skipping SonarQube scan (no valid token)"
            else
              docker run --rm \
                -e SONAR_HOST_URL=http://sonarqube:9000 \
                -e SONAR_LOGIN=$SONAR_LOGIN \
                -v "$PWD":/usr/src \
                sonarsource/sonar-scanner-cli \
                -Dsonar.projectKey=ci_cd_app \
                -Dsonar.projectName=ci_cd_app \
                -Dsonar.sources=. || echo "Sonar scan failed; continuing"
            fi
          '''
        }
      }
    }
    stage('Build Image') {
      steps {
        sh '''
          set -euxo pipefail
          if [ ! -f Dockerfile ]; then
            echo "No Dockerfile found; generating a minimal one for test"
            cat > Dockerfile <<'EOF'
FROM python:3.11-slim
WORKDIR /app
COPY . /app
EXPOSE 8080
CMD [ "python", "-m", "http.server", "8080" ]
EOF
          fi
          docker build -t ${IMAGE_REPO}:${APP_VERSION} -t ${IMAGE_REPO}:latest .
        '''
      }
    }
    stage('Push Image') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', passwordVariable: 'DOCKER_PSW', usernameVariable: 'DOCKER_USR')]) {
          sh '''
            set -euxo pipefail
            if [ "$DOCKER_USR" = "changeme" ] || [ "$DOCKER_PSW" = "changeme" ] || [ -z "$DOCKER_USR" ] || [ -z "$DOCKER_PSW" ]; then
              echo "Skipping push (no registry credentials)"
            else
              echo "$DOCKER_PSW" | docker login -u "$DOCKER_USR" --password-stdin
              docker push ${IMAGE_REPO}:${APP_VERSION}
              docker push ${IMAGE_REPO}:latest
            fi
          '''
        }
      }
    }
    stage('Deploy Canary') {
      when { expression { sh(script: 'kubectl version --short >/dev/null 2>&1', returnStatus: true) == 0 } }
      steps {
        sh '''
          set -euxo pipefail
          kubectl get ns ${KUBE_NAMESPACE} >/dev/null 2>&1 || kubectl create ns ${KUBE_NAMESPACE}
          kubectl -n ${KUBE_NAMESPACE} apply -f /opt/cicd/k8s/service.yaml || true
          kubectl -n ${KUBE_NAMESPACE} apply -f /opt/cicd/k8s/deployment.yaml || true
          kubectl -n ${KUBE_NAMESPACE} apply -f /opt/cicd/k8s/deployment-canary.yaml || true
          kubectl -n ${KUBE_NAMESPACE} set image deployment/app-canary app-container=${IMAGE_REPO}:${APP_VERSION}
          kubectl -n ${KUBE_NAMESPACE} scale deployment/app-canary --replicas=1
          kubectl -n ${KUBE_NAMESPACE} rollout status deployment/app-canary --timeout=180s
        '''
      }
    }
    stage('Promote (Rolling Update)') {
      when { expression { sh(script: 'kubectl version --short >/dev/null 2>&1', returnStatus: true) == 0 } }
      steps {
        sh '''
          set -euxo pipefail
          kubectl -n ${KUBE_NAMESPACE} set image deployment/app app-container=${IMAGE_REPO}:${APP_VERSION}
          kubectl -n ${KUBE_NAMESPACE} rollout status deployment/app --timeout=300s
        '''
      }
    }
    stage('Cleanup Canary') {
      when { expression { sh(script: 'kubectl version --short >/dev/null 2>&1', returnStatus: true) == 0 } }
      steps {
        sh '''
          set -euxo pipefail
          kubectl -n ${KUBE_NAMESPACE} scale deployment/app-canary --replicas=0 || true
        '''
      }
    }
  }
  post {
    failure {
      script {
        sh '''
          set -euxo pipefail
          echo "Build failed. Attempting rollback..."
          kubectl -n ${KUBE_NAMESPACE} rollout undo deployment/app || true
          kubectl -n ${KUBE_NAMESPACE} scale deployment/app-canary --replicas=0 || true
        '''
      }
    }
  }
}
