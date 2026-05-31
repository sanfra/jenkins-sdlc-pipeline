# SDLC Framework — Guida operativa

Framework condiviso per E2E e Performance testing, progettato per essere riusabile su qualsiasi applicazione onboardata nella pipeline Jenkins.

**Jira** — [https://sanfranetstudio.atlassian.net/](https://sanfranetstudio.atlassian.net/)

| Campo     | Valore                                   |
| --------- | ---------------------------------------- |
| Login UI  | account Atlassian — <sanfra@sanfra.net> |
| API Token | in `.env` → `JIRA_API_TOKEN`        |
| Project   | SDLC (`KAN`)                           |

**SonarQube** — [http://localhost:9000](http://localhost:9000/)

| Campo     | Valore                                           |
| --------- | ------------------------------------------------ |
| Username  | admin                                            |
| Password  | SanfraAdmin2026!                                 |
| Token API | `sqa_6748110af246ab6d1be90efcfee46192f808428c` |

**InfluxDB** — [http://localhost:8087](http://localhost:8087/)

| Campo    | Valore                                       |
| -------- | -------------------------------------------- |
| Username | admin                                        |
| Password | SanfraInflux2026!                            |
| Token    | `Sbg7asHru8EIpn6Tdea5WkAlmABgaTgirhPIR2Z8` |
| Org      | sanfra                                       |
| Bucket   | jenkins                                      |

**Grafana** — [http://localhost:3002](http://localhost:3002/)

| Campo    | Valore             |
| -------- | ------------------ |
| Username | admin              |
| Password | SanfraGrafana2026! |

**Jenkins** — [http://localhost:8080](http://localhost:8080/)

| Campo    | Valore |
| -------- | ------ |
| Username | admin  |
| Password | admin  |

---

## Indice

1. Filosofia e responsabilità
2. Struttura del framework
3. Stack infrastruttura Docker
4. Pipeline — stage per stage
5. Deployment pipeline — promozione tra ambienti
6. Configurazione YAML — come funziona il deep merge
7. Profili tecnologici
8. Ambienti
9. Criticality tiers
10. Monitoring — InfluxDB + Grafana
11. Onboarding di una nuova app
12. Estendere il framework
13. Lezioni apprese

---

## 1. Filosofia e responsabilità

### Chi fa cosa

| Layer                 | Proprietario   | Contenuto                                                         |
| --------------------- | -------------- | ----------------------------------------------------------------- |
| Codice app            | App team       | Sorgenti, configurazione applicativa                              |
| Unit test             | App team       | Test interni, tecnologia specifica dell'app                       |
| Deploy YAML           | App team       | Configurazione per ambiente + sezione `testing:`                |
| Smoke test generico   | Framework team | HTTP check automatico, guidato da `techProfile`                 |
| NRT funzionali        | Cross QA team  | Scenari di regressione specifici per app, integrati nel framework |
| Performance scenarios | Cross QA team  | Simulazioni Gatling per app, integrate nel framework              |
| Metriche e soglie     | Framework team | YAML base + profili + ambienti + criticality                      |

### Principio chiave

L'applicazione **non sa nulla** di E2E né di performance. Fornisce solo quattro campi nella sezione `testing:` del suo deploy YAML. Il framework fa il resto.

### Onboarding in due fasi

**Fase 1 — immediata:** l'app aggiunge la sezione `testing:` al deploy YAML → smoke test automatico in pipeline da subito.

**Fase 2 — differita:** il cross QA team studia l'app, scrive NRT + scenari Gatling specifici, li integra nel framework.

---

## 2. Struttura del framework

```text
jenkins-sdlc-pipeline/
├── .env                              ← credenziali locali (gitignored)
├── .env.example                      ← template credenziali
├── docker-compose.yml                ← stack: Jenkins + SonarQube + PostgreSQL + InfluxDB + Grafana
├── Jenkinsfile                       ← pipeline sdlc-pipeline (app Java demo)
├── pom.xml                           ← Spring Boot 3.2.5, Java 17
├── checkstyle.xml
├── pmd-ruleset.xml
├── dependency-check-suppressions.xml
│
├── config/                           ← deploy YAML per ambiente (usati dalla pipeline)
│   ├── ci.yaml
│   ├── local.yaml
│   ├── prod.yaml
│   └── qa.yaml
│
├── docs/
│   └── framework-guide.md            ← questo file
│
├── docker/
│   ├── jenkins/
│   │   ├── Dockerfile                ← jenkins/jenkins:lts-jdk21 + Maven 3.9.6 + Node + Chromium
│   │   ├── plugins.txt               ← plugin Jenkins installati all'avvio
│   │   └── casc.yml                  ← Jenkins Configuration-as-Code (credenziali, SonarQube, InfluxDB)
│   └── grafana/
│       ├── dashboards/
│       │   └── jenkins-builds.json   ← dashboard Grafana provisioned
│       └── provisioning/
│           ├── dashboards/provider.yml
│           └── datasources/influxdb.yml
│
├── framework/
│   ├── e2e/
│   │   ├── pom.xml                   ← JUnit 5.10.2 + RestAssured 5.4.0 + Selenium 4.18.1
│   │   └── src/test/java/net/sanfra/framework/smoke/
│   │       ├── SmokeSpa.java         ← smoke HTTP per techProfile=spa
│   │       └── SmokeSpaBrowser.java  ← smoke browser (Selenium) per techProfile=spa
│   └── perf/
│       ├── pom.xml                   ← Gatling 3.11.5 + Scala 2.13.12
│       └── src/test/
│           ├── scala/net/sanfra/framework/
│           │   └── GenericSimulation.scala
│           └── resources/metrics/    ← YAML deep merge: base → profile → env → criticality
│               ├── base.yml
│               ├── profile-spa.yml
│               ├── profile-rest-api.yml
│               ├── env-ci.yml
│               ├── env-dev.yml
│               ├── env-qa.yml
│               ├── env-staging.yml
│               ├── env-preprod.yml
│               ├── criticality-low.yml
│               ├── criticality-medium.yml
│               └── criticality-high.yml
│
├── e2e/                              ← NRT Selenium (cross QA team, per app)
│   ├── pom.xml
│   └── src/test/java/net/sanfra/e2e/
│       ├── config/TestConfig.java
│       ├── pages/
│       │   ├── BasePage.java
│       │   └── HomePage.java
│       └── tests/
│           └── HomePageTest.java     ← NRT sanfra-app (pilota)
│
├── scripts/
│   ├── influx/                       ← script Node.js: metriche pipeline → InfluxDB
│   │   ├── write.js                  ← helper HTTP write
│   │   ├── pipeline.js               ← measurement: pipeline_run
│   │   ├── coverage.js               ← measurement: coverage
│   │   ├── sonarqube.js              ← measurement: code_quality
│   │   └── gatling.js                ← measurement: gatling_run
│   └── security/
│       └── scan.js                   ← security scan: npm audit + stub java/dotnet → measurement: security_scan
│
├── performance/                      ← JMeter (legacy, non in uso nella pipeline attuale)
│   └── src/test/jmeter/sanfra-smoke.jmx
│
└── src/                              ← app Java demo (target di sdlc-pipeline)
    ├── main/java/net/sanfra/pipeline/
    │   ├── SdlcApplication.java
    │   ├── controller/OrderController.java
    │   ├── domain/Order.java
    │   ├── repository/OrderRepository.java
    │   └── service/OrderService.java
    └── test/java/net/sanfra/pipeline/
        ├── arch/ArchitectureTest.java
        ├── controller/OrderControllerTest.java
        └── service/OrderServiceTest.java
```

---

## 3. Stack infrastruttura Docker

### Servizi e versioni

| Servizio   | Immagine                      | Porta host   | Note                                                      |
| ---------- | ----------------------------- | ------------ | --------------------------------------------------------- |
| Jenkins    | `jenkins/jenkins:lts-jdk21` | 8080         | Maven 3.9.6 + Node.js + Chromium integrati nel Dockerfile |
| SonarQube  | `sonarqube:community`       | 9000         | Quality gate su coverage e code smell                     |
| PostgreSQL | `postgres:15-alpine`        | — (interna) | Database per SonarQube                                    |
| InfluxDB   | `influxdb:2.7`              | 8087         | org `sanfra`, bucket `jenkins`; porta 8086 interna    |
| Grafana    | `grafana/grafana:13.0.1`    | 3002         | Dashboard `jenkins-builds` provisioned automaticamente  |

> **Nota:** `influxdb:2.7` è la versione stabile. La `2.9.x` ha un bug nell'entrypoint (`dasel` crash) che impedisce l'avvio.

### Comandi Docker

Avviare lo stack:
`docker compose up -d`

Verificare stato:
`docker compose ps`

Fermare lo stack (mantiene i volumi):
`docker compose down`

Fermare e rimuovere tutto (inclusi volumi):
`docker compose down -v`

Rebuild dell'immagine Jenkins (dopo modifiche a Dockerfile, plugins.txt, casc.yml):
`docker compose build jenkins --no-cache && docker compose up -d jenkins`

### Credenziali locali (dev only)

> Questi valori sono per l'ambiente locale. Le variabili effettive sono in `.env` (gitignored).

| Servizio  | URL                   | Username | Password / Token                                |
| --------- | --------------------- | -------- | ----------------------------------------------- |
| Jenkins   | http://localhost:8080 | admin    | admin                                           |
| SonarQube | http://localhost:9000 | admin    | SanfraAdmin2026!                                |
| InfluxDB  | http://localhost:8087 | admin    | SanfraInflux2026! (UI) — token API in `.env` |
| Grafana   | http://localhost:3002 | admin    | SanfraGrafana2026!                              |

### Plugin Jenkins installati

| Plugin                        | Scopo                                                    |
| ----------------------------- | -------------------------------------------------------- |
| `configuration-as-code`     | JCasC — carica credenziali e config da `casc.yml`     |
| `git`                       | Checkout da repository                                   |
| `workflow-aggregator`       | Pipeline base                                            |
| `pipeline-model-definition` | Declarative Pipeline syntax                              |
| `sonar`                     | Integrazione SonarQube                                   |
| `credentials-binding`       | Uso di credenziali in `withCredentials`                |
| `job-dsl`                   | Definizione job da `casc.yml`                          |
| `timestamper`               | Timestamp nei log                                        |
| `ansicolor`                 | Colori ANSI nei log                                      |
| `junit`                     | Pubblicazione report JUnit                               |
| `docker-workflow`           | `docker.build`, `docker.withRegistry` nelle pipeline |
| `ws-cleanup`                | `cleanWs()` — pulizia workspace                       |
| `pipeline-stage-view`       | Stage View UI nel job                                    |
| `copyartifact`              | Copia artifact tra job                                   |
| `influxdb`                  | `influxDbPublisher` — metriche build → InfluxDB      |
| `jira`                      | Integrazione Jira Cloud — apertura Bug automatica       |
| `xray-connector`            | Import risultati test → Jira Test Execution (Xray)      |

### Versioni componenti framework

| Componente                      | Versione   |
| ------------------------------- | ---------- |
| Java (Jenkins runtime)          | 21         |
| Java (compiler app + framework) | 17         |
| Maven                           | 3.9.6      |
| Spring Boot                     | 3.2.5      |
| JUnit 5                         | 5.10.2     |
| RestAssured                     | 5.4.0      |
| Selenium                        | 4.18.1     |
| WebDriverManager                | 5.8.0      |
| Gatling                         | 3.11.5     |
| Scala                           | 2.13.12    |
| Jackson (YAML)                  | 2.16.1     |
| Gatling Maven Plugin            | 4.9.0      |
| Scala Maven Plugin              | 4.8.1      |
| Maven Surefire                  | 3.2.5      |
| ArchUnit                        | 1.3.0      |
| SonarQube Maven Plugin          | 4.0.0.4121 |
| Checkstyle Maven Plugin         | 3.3.1      |
| PMD Maven Plugin                | 3.21.0     |
| OWASP Dependency Check          | 10.0.3     |

---

## 4. Pipeline — stage per stage

La pipeline segue questo ordine, progettato per il fail-fast: ogni stage presuppone che i precedenti abbiano passato.

```
Setup Environment → Unit Test → Static Analysis → Build
→ Deploy → Smoke Test → E2E → Performance Gate → Archive
```

### Setup Environment

Prepara tutto ciò che serve per la build:

- Installa le dipendenze dell'app (`npm install`, `mvn install`, ecc.)
- Clona il framework (`git clone --depth 1` del repo jenkins-sdlc-pipeline)
- Nessuna logica di test — pura preparazione

**Nota:** il framework viene clonato fresho ad ogni build per garantire coerenza. Futura evoluzione: Jenkins Shared Library per evitare il clone.

### Unit Test

Esegue i test unitari interni all'app. Tecnologia dipendente dall'app:

- SPA React/Vue: Vitest, Jest
- Spring Boot: JUnit 5 + Mockito
- Node API: Jest, Mocha

I report JUnit vengono pubblicati in Jenkins (trend visibile nel job).

### Static Analysis

Analisi statica del codice sorgente (non del bundle compilato). In un unico stage:

1. Esegue lo scanner (SonarQube, Checkstyle, PMD, ecc.)
2. Verifica il quality gate — se fallisce, la pipeline si ferma qui

Eseguito prima del Build perché analizza i sorgenti originali.

### Build

Produce l'artefatto deployabile (`dist/`, `.jar`, `.war`, ecc.). Questo è l'output che viene testato nei successivi stage.

### Deploy

Avvia l'applicazione nell'ambiente target per i test. Due modalità:

**Locale (CI):** avvia un server locale (`npm run preview`, `java -jar app.jar`, ecc.) sulla porta definita. Il server resta vivo per tutta la durata di Smoke, E2E e Performance Gate, poi viene spento nel `post { always }`.

**Ambiente reale (dev/qa/staging/preprod):** deploy sull'ambiente target via FTP, SSH, Helm, ecc. I test successivi puntano all'URL reale definito nel deploy YAML.

Il Deploy include un health check con timeout esplicito (default: 30 secondi). Se il server non risponde entro il timeout, la build fallisce con messaggio chiaro — nessun loop infinito.

### Smoke Test

Verifica che l'applicazione sia viva e risponda correttamente alle richieste base. Eseguito dal framework tramite `SmokeSpa` (o il runner corrispondente al `techProfile`).

Cosa verifica per `techProfile=spa`:

- `GET healthPath` → HTTP 200, Content-Type `text/html`
- `GET /` → 200, body contiene `<html`
- Routes principali (`/about`, `/contact`, ecc.) → 200, response time < 5s

Se lo Smoke fallisce, E2E e Performance Gate vengono saltati (fail-fast).

### E2E

Scenari di regressione funzionale scritti dal cross QA team. Testano i flussi di business dell'applicazione:

- Navigazione tra pagine
- Form, validazioni, messaggi di errore
- Flussi autenticati
- Comportamento responsive

**Stato attuale:** placeholder fino a quando il cross QA team non onboarda gli scenari per l'app.

### Performance Gate

Esegue `GenericSimulation` (Gatling) con configurazione completamente derivata dal deep merge YAML. La simulazione fallisce se le soglie non vengono rispettate, bloccando la pipeline.

I report HTML di Gatling vengono copiati nel workspace e archiviati come artifact Jenkins.

### Archive

Archivia gli artifact di build e i report di performance in Jenkins per consultazione storica:

- `dist/**` — bundle deployabile
- `target/gatling-reports/**` — report HTML Gatling

---

## 5. Deployment pipeline — promozione tra ambienti

La pipeline non è solo CI — è un **deployment pipeline** completo. Ogni run sa in quale ambiente sta girando (`TARGET_ENV`) e, se tutto passa, propone la promozione all'ambiente successivo.

### Visibilità dell'ambiente

Ogni pipeline mostra un banner esplicito all'inizio:

```text
╔══════════════════════════════════════════════╗
║  sanfra-app                                  ║
║  Environment : CI                            ║
║  Base URL    : http://localhost:4173         ║
║  Build       : #14                           ║
╚══════════════════════════════════════════════╝
```

`TARGET_ENV` è un parametro Jenkins (`parameters { choice(...) }`) — visibile nell'UI, nei log e nella Stage View.

### Stage condizionali per ambiente

Non tutti gli stage girano in tutti gli ambienti:

| Stage             | ci             | prod                     |
| ----------------- | -------------- | ------------------------ |
| Setup Environment | ✓             | ✓                       |
| Unit Test         | ✓             | ✓                       |
| Static Analysis   | ✓             | ✗ già eseguita in ci   |
| Build             | ✓             | ✓                       |
| Deploy            | preview locale | FTP su www               |
| Smoke Test        | ✓             | ✓                       |
| Functional        | ✓             | ✗ NRT non su prod       |
| Performance       | ✓             | ✗ load test mai su prod |
| Archive           | ✓             | ✓                       |
| Promote           | ci → prod     | — fine catena           |

### Catena di promozione — due modelli

**Caso A — catena completa (app enterprise):**

```text
commit
  ↓
[CI]       build + full test suite        → verde → auto-promuove
  ↓
[DEV]      deploy + smoke + functional    → verde → auto-promuove
  ↓
[QA]       deploy + smoke + functional    → verde → 🔐 approval
  ↓
[STAGING]  deploy + smoke + functional    → verde → 🔐 approval
  ↓
[PREPROD]  deploy + smoke + functional    → verde → 🔐 approval
  ↓
[PROD]     deploy + smoke
```

**Caso B — catena ridotta (app pilota / piccola):**

```text
commit
  ↓
[CI]    build + full test suite    → verde → 🔐 approval
  ↓
[PROD]  FTP deploy + smoke
```

Il framework supporta entrambi — la catena è configurabile nel `Promote` stage di ogni app.

### Stage Promote

```groovy
stage('Promote') {
    steps {
        script {
            def chain = [ci: 'prod']           // configura la catena qui
            def next  = chain[params.TARGET_ENV]

            if (!next) { return }              // fine catena

            // gate manuale da QA in poi
            timeout(time: 30, unit: 'MINUTES') {
                input message: "Deploy su ${next.toUpperCase()}?", ok: 'Promuovi'
            }

            build job: env.JOB_NAME,
                  parameters: [string(name: 'TARGET_ENV', value: next)],
                  wait: false
        }
    }
}
```

### Credenziali per deploy su ambienti reali

Le credenziali (FTP, SSH, token) **non vanno nel Jenkinsfile né nel deploy YAML**. Vanno configurate in Jenkins:

`Manage Jenkins → Credentials → (global) → Add Credentials → Username with password`

Nel Jenkinsfile si referenziano per ID:

```groovy
withCredentials([usernamePassword(credentialsId: 'aruba-ftp', ...)]) { ... }
```

---

## 6. Configurazione YAML — come funziona il deep merge

La configurazione finale è il risultato di quattro livelli sovrapposti, ciascuno dei quali sovrascrive solo i valori che specifica:

```
base.yml                      valori minimi di default per tutto
    ↓ override
profile-{techProfile}.yml     specializzazione per tipo di app
    ↓ override
env-{env}.yml                 carico e soglie per ambiente
    ↓ override
criticality-{level}.yml       inasprimento soglie per criticità
    ↓ override
-D system properties          override da CLI / Jenkinsfile (priorità massima)
```

### Esempio concreto — sanfra-app in CI

Parametri: `techProfile=spa`, `env=ci`, `criticality=low`

| Campo                            | base.yml | profile-spa.yml | env-ci.yml | Risultato      |
| -------------------------------- | -------- | --------------- | ---------- | -------------- |
| `load.plateau.users`           | 10       | —              | 3          | **3**    |
| `load.plateau.duration`        | 120      | —              | 30         | **30**   |
| `assertions.p95RtMaxMs`        | 8000     | 2000            | —         | **2000** |
| `assertions.minSuccessPercent` | 90       | 95              | 90         | **90**   |

### Parametri runtime obbligatori

| Parametro             | Esempio                   | Descrizione                     |
| --------------------- | ------------------------- | ------------------------------- |
| `-Dapp.baseUrl`     | `http://localhost:4173` | URL dell'app in questo ambiente |
| `-Dapp.techProfile` | `spa`                   | Profilo tecnologico             |
| `-Dapp.criticality` | `low`                   | Tier di criticità              |
| `-Denv`             | `ci`                    | Ambiente target                 |

### Parametri runtime opzionali

| Parametro                   | Default  | Descrizione                                          |
| --------------------------- | -------- | ---------------------------------------------------- |
| `-Dapp.healthPath`        | `/`    | Path per health check                                |
| `-Dapp.paths`             | `/`    | Percorsi da caricare nel load test (comma-separated) |
| `-Dload.plateau.duration` | dal YAML | Override diretto del plateau (secondi)               |

---

## 7. Profili tecnologici

### `spa` — Single Page Application

App statiche servite da un web server (Vite, Nginx, ecc.). Caratteristiche:

- Tutte le route servono lo stesso `index.html`
- Asset statici: JS bundle, CSS, immagini
- Latenze tipicamente molto basse (file system / CDN cache)

Soglie: `p95 < 2000ms`, `success% > 95%`

Runner smoke: `SmokeSpa.java` (RestAssured HTTP) + `SmokeSpaBrowser.java` (Selenium)

### `rest-api` — REST API

API JSON su HTTP (Spring Boot, Node/Express, FastAPI, ecc.). Caratteristiche:

- Endpoint con logica business, accesso DB
- Latenze variabili in base alla complessità della query
- Concorrenza più alta attesa rispetto a una SPA

Soglie: `p95 < 1500ms`, `success% > 97%`

Runner smoke: `SmokeRestApi.java` — **da implementare**

---

## 8. Ambienti

| Ambiente    | Scopo                               | Plateau users | Durata plateau |
| ----------- | ----------------------------------- | ------------- | -------------- |
| `ci`      | Validazione pipeline, carico minimo | 3             | 30s            |
| `dev`     | Sviluppo locale, carico leggero     | 5             | 60s            |
| `qa`      | Test funzionale, carico moderato    | 20            | 180s           |
| `staging` | Pre-prod, carico prod-like          | 50            | 300s           |
| `preprod` | Validazione finale, carico pieno    | 100           | 600s           |

---

## 9. Criticality tiers

La criticità dell'app inasprisce le soglie di performance:

| Tier       | Applicazioni tipiche                  | `meanRt` | `p95Rt` | `success%` |
| ---------- | ------------------------------------- | ---------- | --------- | ------------ |
| `low`    | Portfolio, vetrina, strumenti interni | < 3000ms   | < 8000ms  | > 90%        |
| `medium` | Operativo, impatto su utenti          | < 1500ms   | < 4000ms  | > 95%        |
| `high`   | Mission-critical, transazionale       | < 500ms    | < 1500ms  | > 99%        |

---

## 10. Monitoring — InfluxDB + Grafana

### Architettura

Ogni build della pipeline scrive metriche in InfluxDB tramite due meccanismi:

1. **Plugin Jenkins** (`influxDbPublisher`) — scrive automaticamente `build_data` e `junit_data` nel `post { always }` di ogni build
2. **Script Node.js** (`scripts/influx/`) — scrivono misurazioni custom per coverage, SonarQube, Gatling

### Measurements in InfluxDB

| Measurement       | Script           | Campi principali                                                 |
| ----------------- | ---------------- | ---------------------------------------------------------------- |
| `pipeline_run`  | `pipeline.js`  | `result`, `duration_ms`, `stage`                           |
| `coverage`      | `coverage.js`  | `line_coverage`, `branch_coverage`, `threshold`            |
| `code_quality`  | `sonarqube.js` | `bugs`, `vulnerabilities`, `code_smells`, `quality_gate` |
| `gatling_run`   | `gatling.js`   | `p50`, `p95`, `p99`, `mean_rt`, `success_percent`      |
| `build_data`    | plugin Jenkins   | `build_result`, `build_time`, `test_count`, `fail_count` |
| `security_scan` | `scan.js`      | `critical`, `high`, `moderate`, `low` — tag: `tech`   |

Tutti i measurements portano i tag `app` (nome app), `env` (TARGET_ENV), `build` (BUILD_NUMBER).

### Configurazione JCasC

Il target InfluxDB viene configurato automaticamente da `docker/jenkins/casc.yml`:

```yaml
unclassified:
  influxDbGlobalConfig:
    targets:
      - description: "jenkins-monitoring"
        url: "http://influxdb-jenkins:8086"
        credentialsId: "influxdb-token"
        organization: "${INFLUXDB_ORG}"
        database: "${INFLUXDB_BUCKET}"
        exposeExceptions: false
```

> **Nota:** il campo si chiama `database` (non `bucket`) — è il nome usato dal plugin Jenkins `influxdb` per il bucket di InfluxDB 2.x.

Le env var `INFLUXDB_ORG` e `INFLUXDB_BUCKET` sono passate dal `docker-compose.yml` al container Jenkins:

```yaml
# docker-compose.yml — sezione jenkins environment
INFLUXDB_ORG: ${INFLUXDB_JENKINS_ORG}
INFLUXDB_BUCKET: ${INFLUXDB_JENKINS_BUCKET}
```

Il token è nel credential `influxdb-token` (type: `string`), configurato da JCasC con il valore `${INFLUXDB_ADMIN_TOKEN}` da `.env`.

### Grafana

Dashboard `jenkins-builds` provisioned automaticamente all'avvio da:

- `docker/grafana/provisioning/datasources/influxdb.yml` — datasource InfluxDB
- `docker/grafana/provisioning/dashboards/provider.yml` — provider di dashboard
- `docker/grafana/dashboards/jenkins-builds.json` — definizione dashboard

URL: http://localhost:3002 — login: `admin` / `SanfraGrafana2026!`

### Uso degli script InfluxDB nel Jenkinsfile

Esempio per scrivere la coverage:

```groovy
script {
    try {
        withCredentials([string(credentialsId: 'influxdb-token', variable: 'INFLUXDB_TOKEN')]) {
            sh """
                node /tmp/sdlc-framework/scripts/influx/coverage.js \
                    --app ${env.APP_NAME} --env ${params.TARGET_ENV} --build ${env.BUILD_NUMBER} \
                    --coverage-file target/site/jacoco/jacoco.xml || true
            """
        }
    } catch (Exception e) {
        echo "⚠ InfluxDB metrics (coverage): ${e.message}"
    }
}
```

Il `|| true` e il `try/catch` sono intenzionali: le metriche sono non-bloccanti — un'anomalia in InfluxDB non deve bloccare la build.

> **Importante:** `try/catch` in Declarative Pipeline **deve stare dentro un blocco `script {}`**. Non è valido direttamente in `steps {}` o `post { always {} }`.

---

## 11. Onboarding di una nuova app

### Fase 1 — Smoke automatico (immediato)

Aggiungere la sezione `testing:` nel deploy YAML dell'app per ogni ambiente:

```yaml
# my-app/deploy/my-app-dev.yaml

app:
  name: my-app
  version: "${VERSION:-dev}"

deploy:
  type: ftp # o helm, ssh, docker, ecc.
  host: dev.example.com

testing:
  baseUrl: "https://dev.example.com"
  healthPath: /actuator/health # o / per SPA
  techProfile: rest-api # spa | rest-api
  criticality: medium # low | medium | high
```

Aggiornare il Jenkinsfile dell'app con le variabili d'ambiente del framework:

```groovy
environment {
    FRAMEWORK_REPO   = 'https://github.com/sanfra/jenkins-sdlc-pipeline.git'
    APP_TECH_PROFILE = 'rest-api'
    APP_CRITICALITY  = 'medium'
    APP_HEALTH_PATH  = '/actuator/health'
    APP_PATHS        = '/orders,/orders/1'
    PREVIEW_PORT     = '8080'
}
```

### Fase 2 — NRT e Performance (differita, cross QA team)

Il cross QA team:

1. Analizza l'app e identifica i flussi critici
2. Scrive i test NRT in `jenkins-sdlc-pipeline/e2e/src/test/java/net/sanfra/e2e/tests/{AppName}Test.java`
3. Aggiunge scenari Gatling specifici in `jenkins-sdlc-pipeline/framework/perf/src/test/scala/net/sanfra/framework/scenarios/{AppName}Simulation.scala`
4. Aggiorna lo stage `E2E` nel Jenkinsfile dell'app per eseguire gli NRT

---

## 12. Estendere il framework

### Aggiungere un profilo tecnologico

1. Creare `framework/perf/src/test/resources/metrics/profile-{nome}.yml` con override delle soglie
2. Creare `framework/e2e/src/test/java/net/sanfra/framework/smoke/Smoke{Nome}.java` con i check HTTP specifici
3. Documentare il profilo in questo file (sezione 7)

### Aggiungere un ambiente

Creare `framework/perf/src/test/resources/metrics/env-{nome}.yml` con load profile e soglie per quell'ambiente. Il framework lo carica automaticamente con `-Denv={nome}`.

### Aggiungere un livello di criticità

Creare `framework/perf/src/test/resources/metrics/criticality-{nome}.yml`. Il framework lo applica automaticamente con `-Dapp.criticality={nome}`.

---

## 13. Lezioni apprese

### `scala-maven-plugin` richiede `<executions>` esplicito

Senza il binding a `test-compile`, Scala non viene compilata prima che Gatling tenti di caricare la simulation class → `ClassNotFoundException` a runtime.

```xml
<plugin>
  <groupId>net.alchim31.maven</groupId>
  <artifactId>scala-maven-plugin</artifactId>
  <version>4.8.1</version>
  <executions>
    <execution>
      <id>scala-test-compile</id>
      <phase>test-compile</phase>
      <goals><goal>testCompile</goal></goals>
    </execution>
  </executions>
</plugin>
```

### Il framework in `/tmp` sopravvive tra build

`cleanWs()` pulisce solo il workspace Jenkins (`/var/jenkins_home/workspace/{job}`), non `/tmp`. Usare sempre `rm -rf /tmp/sdlc-framework` prima del `git clone`.

### Loop health check senza timeout blocca la pipeline

Un `until curl ...` senza limite di iterazioni aspetta fino al timeout globale (20 minuti) senza messaggio d'errore utile. Usare sempre un contatore con `exit 1` esplicito e messaggio descrittivo.

### Il preview server deve vivere per tutti gli stage di test

Avviare e uccidere il server dentro ogni stage (Smoke, E2E, Performance) crea rischio di porta già occupata quando il kill fallisce silenziosamente. Il server deve essere avviato una volta nel stage **Deploy** e terminato nel `post { always }` tramite PID file.

### Artefatti Gatling fuori dal workspace non vengono archiviati

`archiveArtifacts` funziona solo dentro il workspace Jenkins. Copiare i report Gatling da `/tmp/sdlc-framework/.../target/gatling/` in `target/gatling-reports/` prima di archiviarli.

### `jenkins-cli.jar` non persiste dopo `docker compose down`

Il file `/tmp/cli.jar` viene scaricato nel container Jenkins. Il container viene ricreato ad ogni `docker compose down` → `/tmp` è vuoto. Riscaricarlo ogni volta:

```sh
docker exec jenkins-sdlc-pipeline-jenkins-1 sh -c \
  'curl -sf http://localhost:8080/jnlpJars/jenkins-cli.jar -o /tmp/cli.jar && \
   java -jar /tmp/cli.jar -s http://localhost:8080/ -auth admin:admin build sanfra-app -s -v'
```

### Il primo build dopo rebuild Docker non riconosce i parametri

Dopo un rebuild del container Jenkins, il job perde la definizione dei parametri (non ancora parsata dal nuovo Jenkinsfile). Il primo build va lanciato senza parametri tramite l'endpoint `/build` (non `/buildWithParameters`) per fare il discovery. Poi funziona normalmente con `Build with Parameters`.

```sh
# Primo build — discovery parametri
curl -sf -u admin:admin -b /tmp/jc.txt \
  -H "Jenkins-Crumb: <crumb>" \
  -X POST http://localhost:8080/job/sanfra-app/build
```

### Credenziali persistenti via JCasC + .env

Le credenziali Jenkins vanno in `.env` (gitignored) e referenziate in `docker/jenkins/casc.yml` con `${VAR}`. Jenkins le carica ad ogni avvio automaticamente — nessuna configurazione manuale dopo rebuild o riavvio.

```yaml
# casc.yml
credentials:
  system:
    domainCredentials:
      - credentials:
          - usernamePassword:
              id: "my-credential"
              username: "${MY_USER}"
              password: "${MY_PASS}"
```

```yaml
# docker-compose.yml jenkins environment section
MY_USER: ${MY_USER}
MY_PASS: ${MY_PASS}
```

### `influxdb:2.9.x` — bug entrypoint `dasel`

L'immagine `influxdb:2.9.0` (e versioni recenti 2.9.x) ha un bug nell'entrypoint script: il tool `dasel` usato per la configurazione crasha con `map key not found: "http-bind-address"`, il container entra in loop di restart e cancella i file del volume ad ogni tentativo (`cleaning bolt and engine files`).

Usare `influxdb:2.7` — è la versione 2.x stabile.

### JCasC InfluxDB: campo `database`, non `bucket`

Il plugin Jenkins `influxdb` modella il bucket di InfluxDB 2.x come `database` (retaggio dell'API 1.x). Usare `bucket` in `casc.yml` causa un `UnknownAttributesException` all'avvio di Jenkins e il target non viene registrato.

```yaml
# CORRETTO
unclassified:
  influxDbGlobalConfig:
    targets:
      - database: "${INFLUXDB_BUCKET}"

      # SBAGLIATO — causa UnknownAttributesException
      - bucket: "${INFLUXDB_BUCKET}"
```

### Env var in `casc.yml` devono corrispondere esattamente a quelle passate al container

JCasC risolve `${VAR}` usando le env var del processo Jenkins (il container). Se `docker-compose.yml` passa `INFLUXDB_ORG` ma `casc.yml` referenzia `INFLUXDB_JENKINS_ORG`, il valore rimane non risolto e JCasC lancia un warning `Found unresolved variable`. Verificare sempre la coerenza tra le due.

### `try/catch` in Declarative Pipeline richiede `script {}`

In una Declarative Pipeline, `try/catch` non è un valid step se usato direttamente in `steps {}` o `post { always {} }`. Deve stare dentro un blocco `script {}`:

```groovy
// CORRETTO
post {
    always {
        script {
            try {
                influxDbPublisher(selectedTarget: 'jenkins-monitoring')
            } catch (Exception e) {
                echo "⚠ influxDbPublisher skipped: ${e.message}"
            }
        }
    }
}

// SBAGLIATO — causa "Expected a step" a compile time
post {
    always {
        try {
            influxDbPublisher(selectedTarget: 'jenkins-monitoring')
        } catch (Exception e) { ... }
    }
}
```
