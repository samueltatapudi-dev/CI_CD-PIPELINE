**Overview**
- Dockerized Jenkins (with JCasC) + SonarQube + Postgres
- Jenkins polls the repo `https://github.com/samueltatapudi-dev/CI_CD_APP_VERSION` for changes
- Pipeline stages: pytest, SonarQube scan, Docker build/push, Kubernetes canary + rollout deploy, rollback on failure

**Prerequisites**
- Docker Desktop (Windows/macOS) or Docker Engine (Linux)
- kubectl installed locally and Minikube started, or access to a Kubernetes cluster
- Docker registry account (Docker Hub or compatible)

**Setup**
- Copy `.env.sample` to `.env` and fill:
  - `JENKINS_ADMIN_ID`, `JENKINS_ADMIN_PASSWORD`
  - `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`
  - `SONARQUBE_TOKEN`
  - `IMAGE_REPO` (e.g., `yourname/ci-cd-app`)
- Ensure Kubernetes config is present at `${USERPROFILE}/.kube` and Minikube data at `${USERPROFILE}/.minikube` (these are mounted into Jenkins).

**Start Services**
- `docker compose up -d --build`
- Jenkins: `http://localhost:8080` (admin user from `.env`)
- SonarQube: `http://localhost:9000` (default admin/admin on first run; set a new password and create a token)

**What the Pipeline Does**
- Polls SCM every 2 minutes
- Checks out `CI_CD_APP_VERSION`
- Detects version from `VERSION` file or commit SHA
- Runs pytest in a Python container
- Runs SonarQube analysis via scanner CLI container
- Builds Docker image `IMAGE_REPO:VERSION` and `:latest`
- Pushes images to registry
- Deploys canary (`app-canary` 1 replica), waits for readiness
- Promotes via rolling update on `app` deployment, then scales canary to 0
- On failure, rolls back `app` to previous ReplicaSet and scales down canary

**Kubernetes Manifests**
- `k8s/service.yaml`: ClusterIP service on port 80 -> 8080
- `k8s/deployment.yaml`: main deploy with rolling update and probes
- `k8s/deployment-canary.yaml`: canary deploy initially scaled to 0

**Notes**
- Ensure your app listens on port 8080 inside the container or edit manifests.
- If using Minikube, make sure Jenkins can reach the cluster (the compose mounts `${USERPROFILE}/.kube` and `${USERPROFILE}/.minikube`).
- If the target repo has its own Dockerfile/requirements.txt, the pipeline will use them.
- For a different registry (e.g., quay.io), update `IMAGE_REPO` and creds accordingly.

**Manual Trigger / Logs**
- In Jenkins, open job `ci-cd-app` and click “Build Now”.
- Check “Console Output” for each stage’s logs.

