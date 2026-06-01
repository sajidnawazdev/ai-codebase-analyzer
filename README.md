# AI Codebase Analyzer

**AI-powered architecture analysis for Spring Boot projects.**

> Built as a portfolio project demonstrating AI-native Java engineering — LLM integration, static analysis, prompt engineering, and full-stack delivery.

![Demo](docs/demo.gif)

Paste a Git URL or local path, get a full architecture report with scores, violation detection, coupling analysis, and an AI-generated assessment — in seconds.

---

## What It Does

- **Scans any Spring Boot project** — provide a local file path or a public Git URL. The tool clones, scans, and cleans up automatically.
- **Extracts architecture metadata** — uses JavaParser to analyze class hierarchies, dependencies, endpoints, annotations, and package structure. Detects Spring Data JPA repositories, controller inheritance, and more.
- **Detects architectural violations** — layer violations, circular dependencies, god classes, fat controllers, orphan services, empty controllers, and coupling hotspots.
- **Generates AI-powered analysis** — sends structured metadata to an LLM, which returns a scored assessment with specific, actionable feedback referencing your actual class names.
- **Event-driven architecture** — publishes analysis events to Kafka, consumed by a separate Report Service that stores results in PostgreSQL
- **Secured API** — OAuth2/JWT authentication via Keycloak with role-based access control
- **Kubernetes-ready** — Helm chart for all services including Ingress

---

## Architecture

Two microservices communicating via Kafka:

- **Analyzer Service** (port 8080) — scans code, detects violations, calls AI, publishes events
- **Report Service** (port 8081) — consumes Kafka events, stores analysis history in PostgreSQL

Infrastructure: Kafka, Zookeeper, PostgreSQL, Keycloak — all defined in Docker Compose and Helm chart.

**Monitoring:** Prometheus scrapes application metrics via Spring Actuator, visualized in Grafana dashboards. OpenTelemetry provides distributed tracing.

---

## How to Run

### Start infrastructure
```bash
docker-compose up -d
```
Starts Kafka, Zookeeper, PostgreSQL, and Keycloak.

### Option 1: Docker

```bash
docker build -t ai-codebase-analyzer .
docker run -p 8080:8080 ai-codebase-analyzer
```

To analyze a local project from inside Docker, mount it as a volume:

```bash
docker run -p 8080:8080 -e OPENAI_API_KEY=sk-your-key -v /path/to/project:/scan ai-codebase-analyzer
```

Then use `/scan` as the project path in the UI.

### Option 2: Local (Maven)

Requires Java 21.

```bash
./mvnw spring-boot:run
```

### Option 3: Kubernetes (Helm)

```bash
helm install codebase-analyzer ./helm \
  --set analyzer.openaiApiKey=sk-your-actual-key
```


### Option 4: Azure AKS

```bash
az group create --name codebase-analyzer-rg --location westeurope
az acr create --resource-group codebase-analyzer-rg --name codebaseanalyzeracr --sku Basic
az aks create --resource-group codebase-analyzer-rg --name codebase-analyzer-aks --node-count 1 --node-vm-size standard_b2s_v2 --attach-acr codebaseanalyzeracr --generate-ssh-keys
az aks get-credentials --resource-group codebase-analyzer-rg --name codebase-analyzer-aks

# Push images to ACR
az acr login --name codebaseanalyzeracr
docker build -t codebaseanalyzeracr.azurecr.io/ai-codebase-analyzer:latest .
docker push codebaseanalyzeracr.azurecr.io/ai-codebase-analyzer:latest
docker build -t codebaseanalyzeracr.azurecr.io/report-service:latest ./report-service
docker push codebaseanalyzeracr.azurecr.io/report-service:latest
helm install codebase-analyzer ./helm \
  --set analyzer.openaiApiKey=sk-your-actual-key
```
### Then

Open **http://localhost:8080** and enter a project path or Git URL.

For Azure AKS, get the external IP:

```bash
kubectl get svc analyzer
```

Then open `http://EXTERNAL-IP:8080`.

**Example inputs:**
```
D:\dev\projects\my-spring-app
https://github.com/spring-projects/spring-petclinic.git
```

---

## What It Detects

| Detection | Description |
|---|---|
| **Layer Violations** | Controller directly accessing a repository, service depending on a controller, configuration depending on a controller |
| **Circular Dependencies** | A depends on B, B depends on A |
| **God Classes** | Classes with more than 5 project dependencies or more than 10 methods |
| **Fat Controllers** | Controllers with more than 6 endpoints |
| **Empty Controllers** | Controllers with no mapped endpoints |
| **Orphan Services** | Services not injected by any other class |
| **Coupling Ranking** | Top 3 most coupled classes by incoming + outgoing project dependencies |
| **Spring Data Repositories** | Detects interfaces extending JpaRepository, CrudRepository, MongoRepository, etc. |
| **Hierarchy Detection** | Two-pass detection resolves component types through class inheritance and interface chains |

---

## Tech Stack

| | |
|---|---|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.5 |
| **AST Parsing** | JavaParser |
| **AI** | OpenAI API via Spring AI |
| **Security** | Spring Security, OAuth2, Keycloak |
| **Messaging** | Apache Kafka, Spring Kafka |
| **Database** | PostgreSQL, Spring Data JPA |
| **Git Cloning** | JGit |
| **Diagrams** | Mermaid.js (CDN) |
| **API Docs** | SpringDoc OpenAPI (Swagger) |
| **Containerization** | Docker |
| **Orchestration** | Kubernetes, Helm, Minikube |
| **Monitoring** | Prometheus, Grafana, OpenTelemetry, Spring Actuator |
| **Cloud** | Azure AKS, Azure Container Registry |

---

## API Documentation

Swagger UI is available at:

```
http://localhost:8080/swagger-ui/index.html
```

OpenAPI spec:

```
http://localhost:8080/v3/api-docs
```

### Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/analyse` | Returns raw analysis (no AI) |
| `POST` | `/analyse/ai` | Returns full analysis with AI report |

**Parameters** (query string, provide one):
- `projectPath` — absolute path to a local project
- `repoUrl` — Git URL to clone and analyze

**Example:**
```bash
curl -X POST "http://localhost:8080/analyse/ai?repoUrl=https://github.com/spring-projects/spring-petclinic.git"
```

---

## Configuration

The API key is **never committed to Git**. Three options:

### Option A: Local profile (recommended)

Create `src/main/resources/application-local.yaml`:

```yaml
spring:
  ai:
    openai:
      api-key: sk-your-actual-key
```

This file is in `.gitignore`. Activate the profile in your IDE run config or via command line:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

### Option B: Environment variable

```bash
export OPENAI_API_KEY=sk-your-actual-key
./mvnw spring-boot:run
```

### Option C: Docker

```bash
docker run -p 8080:8080 -e OPENAI_API_KEY=sk-your-actual-key ai-codebase-analyzer
```

### Option D: Kubernetes (Helm)

```bash
helm install codebase-analyzer ./helm \
  --set analyzer.openaiApiKey=sk-your-actual-key
```

---

## Project Structure

```
src/main/java/com/isbrain/codebaseanalyzer/
  controller/        REST endpoints
  service/           Core analysis logic
    ClassAnalyserService       JavaParser-based class analysis
    ProjectScannerService      Orchestrates the full scan pipeline
    ViolationDetectorService   Architectural violation detection
    MermaidGeneratorService    Diagram generation
    SummaryBuilderService      Statistics and relationship building
    GitCloneService            JGit-based repo cloning
    AiAnalysisService          LLM integration
    EndpointExtractorService   REST endpoint extraction
  prompt/            AI prompt construction
  model/             Data records
  config/            Spring AI configuration
src/main/resources/
  static/index.html  Single-page dashboard
  application.yaml   Configuration
helm/
  Chart.yaml            Helm chart with dependencies (PostgreSQL, Kafka, Keycloak, Prometheus, Grafana)
  values.yaml           Default configuration (Minikube)
  values-azure.yaml     Azure AKS overrides
  templates/
    analyzer.yaml       Analyzer deployment + service + secret
    report-service.yaml Report service deployment + service
    ingress.yaml        External access
docker-compose.yml      Local infrastructure (Kafka, PostgreSQL, Keycloak)
```

---

## Future Improvements

- Authentication flow in dashboard (login via Keycloak)
- Analysis result comparison between runs
- Input validation and path sanitization
- Support for additional languages beyond Java/Spring
- Azure AKS deployment with custom domain and TLS
