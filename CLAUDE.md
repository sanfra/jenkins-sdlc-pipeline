# CLAUDE.md — jenkins-sdlc-pipeline

Esempio funzionante di governance architetturale continua su Java/Spring Boot con Jenkins come enforcement point. Ogni commit è verificato contro regole architetturali, analisi statica e Quality Gate SonarQube prima di poter essere pacchettizzato.

## Comandi

```bash
mvn clean verify                    # build completa con tutti i check
mvn test                            # solo test (JUnit 5 + ArchUnit)
mvn checkstyle:check                # solo Checkstyle
mvn pmd:check                       # solo PMD
docker-compose up -d                # avvia SonarQube locale (porta 9000)
mvn sonar:sonar -Dsonar.host.url=http://localhost:9000   # analisi SonarQube
```

## Pipeline stages

| Stage | Tool | Quando fallisce |
|---|---|---|
| Enforcer | Maven Enforcer | Java < 17, Maven < 3.9, dipendenze vietate |
| Test | JUnit 5 + ArchUnit | test fallisce o regola architetturale violata |
| Static Analysis | Checkstyle + PMD | violazione stile o pattern error-prone |
| Dependency Security | OWASP Dep-Check | CVE con CVSS ≥ 7 (opzionale, disabilitato di default) |
| SonarQube | SonarQube 10 | Quality Gate non passato |
| Package | Maven | errore compilazione o packaging |

## Architettura layer — enforced da ArchUnit

```
controller → service, domain
service    → repository, domain
repository → domain
```

Nessun controller può importare direttamente un repository — ArchUnit fa fallire la build se violato.

## Struttura progetto

```
src/
├── main/java/   # codice applicativo (controller, service, repository, domain)
└── test/java/   # test JUnit 5 + regole ArchUnit
Jenkinsfile      # definizione pipeline Jenkins
checkstyle.xml   # regole Checkstyle
pmd-ruleset.xml  # regole PMD
docker/          # Dockerfile e config Docker
docker-compose.yml
```

## Regole

- Non modificare `checkstyle.xml` e `pmd-ruleset.xml` senza aggiornare il README
- Le regole ArchUnit vivono in `src/test/` — sono test normali, non configurazione separata
- `dependency-check-suppressions.xml` va aggiornato solo con motivazione documentata nel commit message
- Il Jenkinsfile è il contratto della pipeline — ogni stage deve restare nel file, anche se disabilitato
