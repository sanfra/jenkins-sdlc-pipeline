---
name: build
description: Esegui mvn clean verify e interpreta i risultati per stage della pipeline
allowed-tools:
  - Bash(mvn *)
  - Bash(find . *)
  - Bash(grep *)
  - Bash(ls *)
  - Read
---

# Build — jenkins-sdlc-pipeline

Esegui la build completa e interpreta i risultati per ogni stage della pipeline.

## Comando principale

```bash
mvn clean verify
```

## Stage e cosa cercare nell'output

| Stage | Cosa cercare nell'output |
|-------|--------------------------|
| Enforcer | `BUILD FAILURE` + `Rule X violated` |
| Test (JUnit) | `Tests run: X, Failures: Y, Errors: Z` |
| ArchUnit | `ArchConditionViolation` + nome regola violata |
| Checkstyle | `Checkstyle violations found` + file:riga |
| PMD | `PMD Failure` + regola violata |
| Package | `BUILD SUCCESS` + JAR in `target/` |

## Comandi per stage singolo

```bash
# Solo test (JUnit + ArchUnit)
mvn test

# Solo Checkstyle
mvn checkstyle:check

# Solo PMD
mvn pmd:check

# Solo Enforcer
mvn enforcer:enforce
```

## Interpretazione ArchUnit

Se ArchUnit fallisce, il messaggio indica quale regola architetturale è stata violata:
- `controller imported repository` → un controller sta importando un repository direttamente
- La regola è definita in `src/test/java/` — guardare lì per capire il vincolo

## Risultato atteso

Riporta:
1. BUILD SUCCESS o FAILURE
2. Per ogni stage fallito: il messaggio di errore esatto
3. Suggerimento di fix (senza modificare `checkstyle.xml`, `pmd-ruleset.xml` o il Jenkinsfile)
