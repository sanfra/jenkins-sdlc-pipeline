# jenkins-sdlc-pipeline

A working example of continuous architectural governance in a Java/Spring Boot project, using Jenkins as the enforcement point.

Every commit is verified against architectural rules, static analysis, and a SonarQube Quality Gate before it can be packaged. Violations break the build — that is the point.

## What's in the pipeline

| Stage | Tool | Fails when |
| --- | --- | --- |
| Enforcer | Maven Enforcer | Java < 17, Maven < 3.9, banned deps found |
| Test | JUnit 5 + ArchUnit | unit test or architecture rule fails |
| Static Analysis | Checkstyle + PMD | style violation or error-prone pattern found |
| Dependency Security | OWASP Dependency-Check | CVE with CVSS ≥ 7 (optional — disabled by default) |
| SonarQube | SonarQube 10 + sonar-maven-plugin | Quality Gate not passed |
| Package | Maven | compile or packaging error |

## Architecture rules enforced by ArchUnit

The application has four layers. ArchUnit verifies these rules on every build:

- `controller` → may only access `service` and `domain`
- `service` → may only access `repository` and `domain`
- `repository` → may only access `domain`
- No controller imports a repository directly
- All concrete classes in `service` are annotated `@Service`
- All types in `repository` are annotated `@Repository`

## Stack

- Java 17 + Spring Boot 3.2
- ArchUnit 1.3 (architecture testing)
- Maven Enforcer 3.4 (supply chain control)
- Checkstyle 3.3 + PMD 3.21 (static analysis)
- OWASP Dependency-Check 10 (SCA — optional)
- SonarQube 10 Community (Quality Gate)
- Jenkins LTS (pipeline orchestration)
- Docker Compose (local stack)

## Prerequisites

- Docker and Docker Compose
- Git

## Local setup — step by step

### 1. Clone and copy the env file

    git clone https://github.com/sanfra/jenkins-sdlc-pipeline
    cd jenkins-sdlc-pipeline
    cp .env.example .env

### 2. Start SonarQube first

    docker compose up sonarqube postgres -d

Wait until SonarQube is healthy (about 60–90 seconds):

    docker compose ps

### 3. Generate a SonarQube token

Open `http://localhost:9000` and log in with `admin` / `admin` (you will be asked to change the password on first login).

Go to **My Account → Security → Generate Token**, name it `jenkins`, copy the token.

Paste it into `.env`:

    SONAR_TOKEN=sqa_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

### 4. Start Jenkins

    docker compose up jenkins -d

Jenkins starts at `http://localhost:8080`. Log in with `admin` / `admin`.

The `sdlc-pipeline` job is created automatically via Jenkins Configuration as Code. Open it and click **Build Now**.

## Running the pipeline

The pipeline runs all stages automatically. OWASP Dependency-Check is disabled by default because the NVD database download is slow on first run. To enable it, check the `RUN_OWASP` parameter when triggering a build, and add your NVD API key to `.env`:

    NVD_API_KEY=your-key-here

Get a free key at `nvd.nist.gov/developers/request-an-api-key`.

## Triggering a violation

To see ArchUnit fail the build, add a direct repository import in the controller:

    // in OrderController.java — this breaks the layering rule
    import com.example.repository.OrderRepository;

Commit, then re-run the pipeline. The **Test** stage fails with a clear ArchUnit message showing which rule was violated.

To see SonarQube block a build, introduce a bug or drop coverage below the Quality Gate threshold and check the **SonarQube** stage output.

## Project structure

    src/
    ├── main/java/com/example/
    │   ├── SdlcApplication.java
    │   ├── controller/OrderController.java
    │   ├── service/OrderService.java
    │   ├── repository/OrderRepository.java
    │   └── domain/Order.java
    └── test/java/com/example/
        ├── arch/ArchitectureTest.java     # ArchUnit rules
        ├── controller/OrderControllerTest.java
        └── service/OrderServiceTest.java

    docker/
    ├── docker-compose.yml
    └── jenkins/
        ├── Dockerfile                     # Jenkins LTS + Maven 3.9
        ├── plugins.txt                    # pre-installed plugins
        └── casc.yml                       # JCasC: credentials, SonarQube, seed job

## Extending to other stacks

The pipeline is Java/Maven focused. For other stacks mentioned in the SDLC governance model:

- **.NET/C#** — replace Maven stages with `dotnet` CLI + SonarScanner for .NET + Roslyn analyzers
- **Angular/TypeScript** — replace with `npm` + ESLint (eslint-plugin-boundaries) + SonarScanner CLI
- **SQL** — add a migration validation stage using Flyway or Liquibase checks

## License

MIT — see [LICENSE](LICENSE).
