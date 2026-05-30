# SDLC Framework — Guida operativa

Framework condiviso per E2E e Performance testing, progettato per essere riusabile su qualsiasi applicazione onboardata nella pipeline Jenkins.

---

## Indice

1. Filosofia e responsabilità
2. Struttura del framework
3. Pipeline — stage per stage
4. Deployment pipeline — promozione tra ambienti
5. Configurazione YAML — come funziona il deep merge
6. Profili tecnologici
7. Ambienti
8. Criticality tiers
9. Onboarding di una nuova app
10. Estendere il framework
11. Lezioni apprese

---

## 1. Filosofia e responsabilità

### Chi fa cosa

| Layer | Proprietario | Contenuto |
|---|---|---|
| Codice app | App team | Sorgenti, configurazione applicativa |
| Unit test | App team | Test interni, tecnologia specifica dell'app |
| Deploy YAML | App team | Configurazione per ambiente + sezione `testing:` |
| Smoke test generico | Framework team | HTTP check automatico, guidato da `techProfile` |
| NRT funzionali | Cross QA team | Scenari di regressione specifici per app, integrati nel framework |
| Performance scenarios | Cross QA team | Simulazioni Gatling per app, integrate nel framework |
| Metriche e soglie | Framework team | YAML base + profili + ambienti + criticality |

### Principio chiave

L'applicazione **non sa nulla** di E2E né di performance. Fornisce solo quattro campi nella sezione `testing:` del suo deploy YAML. Il framework fa il resto.

### Onboarding in due fasi

**Fase 1 — immediata:** l'app aggiunge la sezione `testing:` al deploy YAML → smoke test automatico in pipeline da subito.

**Fase 2 — differita:** il cross QA team studia l'app, scrive NRT + scenari Gatling specifici, li integra nel framework.

---

## 2. Struttura del framework

```text
jenkins-sdlc-pipeline/
├── docs/
│   └── framework-guide.md        ← questo file
├── framework/
│   ├── e2e/
│   │   ├── pom.xml               ← JUnit 5 + RestAssured
│   │   └── src/test/java/net/sanfra/framework/
│   │       └── smoke/
│   │           └── SmokeSpa.java ← smoke HTTP per techProfile=spa
│   └── perf/
│       ├── pom.xml               ← Gatling + scala-maven-plugin
│       └── src/test/
│           ├── scala/net/sanfra/framework/
│           │   └── GenericSimulation.scala
│           └── resources/metrics/
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
└── e2e/                          ← NRT Selenium (cross QA team, per app)
    └── src/test/java/net/sanfra/e2e/
        └── tests/HomePageTest.java   ← NRT sanfra-app (pilota)
```

---

## 3. Pipeline — stage per stage

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

## 4. Deployment pipeline — promozione tra ambienti

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

| Stage | ci | prod |
|---|---|---|
| Setup Environment | ✓ | ✓ |
| Unit Test | ✓ | ✓ |
| Static Analysis | ✓ | ✗ già eseguita in ci |
| Build | ✓ | ✓ |
| Deploy | preview locale | FTP su www |
| Smoke Test | ✓ | ✓ |
| Functional | ✓ | ✗ NRT non su prod |
| Performance | ✓ | ✗ load test mai su prod |
| Archive | ✓ | ✓ |
| Promote | ci → prod | — fine catena |

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

## 5. Configurazione YAML — come funziona il deep merge

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

| Campo | base.yml | profile-spa.yml | env-ci.yml | Risultato |
|---|---|---|---|---|
| `load.plateau.users` | 10 | — | 3 | **3** |
| `load.plateau.duration` | 120 | — | 30 | **30** |
| `assertions.p95RtMaxMs` | 8000 | 2000 | — | **2000** |
| `assertions.minSuccessPercent` | 90 | 95 | 90 | **90** |

### Parametri runtime obbligatori

| Parametro | Esempio | Descrizione |
|---|---|---|
| `-Dapp.baseUrl` | `http://localhost:4173` | URL dell'app in questo ambiente |
| `-Dapp.techProfile` | `spa` | Profilo tecnologico |
| `-Dapp.criticality` | `low` | Tier di criticità |
| `-Denv` | `ci` | Ambiente target |

### Parametri runtime opzionali

| Parametro | Default | Descrizione |
|---|---|---|
| `-Dapp.healthPath` | `/` | Path per health check |
| `-Dapp.paths` | `/` | Percorsi da caricare nel load test (comma-separated) |
| `-Dload.plateau.duration` | dal YAML | Override diretto del plateau (secondi) |

---

## 5. Profili tecnologici

### `spa` — Single Page Application

App statiche servite da un web server (Vite, Nginx, ecc.). Caratteristiche:
- Tutte le route servono lo stesso `index.html`
- Asset statici: JS bundle, CSS, immagini
- Latenze tipicamente molto basse (file system / CDN cache)

Soglie: `p95 < 2000ms`, `success% > 95%`

Runner smoke: `SmokeSpa.java` (RestAssured HTTP)

### `rest-api` — REST API

API JSON su HTTP (Spring Boot, Node/Express, FastAPI, ecc.). Caratteristiche:
- Endpoint con logica business, accesso DB
- Latenze variabili in base alla complessità della query
- Concorrenza più alta attesa rispetto a una SPA

Soglie: `p95 < 1500ms`, `success% > 97%`

Runner smoke: `SmokeRestApi.java` — **da implementare**

---

## 6. Ambienti

| Ambiente | Scopo | Plateau users | Durata plateau |
|---|---|---|---|
| `ci` | Validazione pipeline, carico minimo | 3 | 30s |
| `dev` | Sviluppo locale, carico leggero | 5 | 60s |
| `qa` | Test funzionale, carico moderato | 20 | 180s |
| `staging` | Pre-prod, carico prod-like | 50 | 300s |
| `preprod` | Validazione finale, carico pieno | 100 | 600s |

---

## 7. Criticality tiers

La criticità dell'app inasprisce le soglie di performance:

| Tier | Applicazioni tipiche | `meanRt` | `p95Rt` | `success%` |
|---|---|---|---|---|
| `low` | Portfolio, vetrina, strumenti interni | < 3000ms | < 8000ms | > 90% |
| `medium` | Operativo, impatto su utenti | < 1500ms | < 4000ms | > 95% |
| `high` | Mission-critical, transazionale | < 500ms | < 1500ms | > 99% |

---

## 8. Onboarding di una nuova app

### Fase 1 — Smoke automatico (immediato)

Aggiungere la sezione `testing:` nel deploy YAML dell'app per ogni ambiente:

```yaml
# my-app/deploy/my-app-dev.yaml

app:
  name: my-app
  version: "${VERSION:-dev}"

deploy:
  type: ftp          # o helm, ssh, docker, ecc.
  host: dev.example.com

testing:
  baseUrl: "https://dev.example.com"
  healthPath: /actuator/health     # o / per SPA
  techProfile: rest-api            # spa | rest-api
  criticality: medium              # low | medium | high
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

## 9. Estendere il framework

### Aggiungere un profilo tecnologico

1. Creare `framework/perf/src/test/resources/metrics/profile-{nome}.yml` con override delle soglie
2. Creare `framework/e2e/src/test/java/net/sanfra/framework/smoke/Smoke{Nome}.java` con i check HTTP specifici
3. Documentare il profilo in questo file (sezione 5)

### Aggiungere un ambiente

Creare `framework/perf/src/test/resources/metrics/env-{nome}.yml` con load profile e soglie per quell'ambiente. Il framework lo carica automaticamente con `-Denv={nome}`.

### Aggiungere un livello di criticità

Creare `framework/perf/src/test/resources/metrics/criticality-{nome}.yml`. Il framework lo applica automaticamente con `-Dapp.criticality={nome}`.

---

## 10. Lezioni apprese

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
