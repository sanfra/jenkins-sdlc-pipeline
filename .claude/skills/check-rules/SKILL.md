---
name: check-rules
description: Esegui solo i test ArchUnit per verificare le regole architetturali
allowed-tools:
  - Bash(mvn *)
  - Bash(find . *)
  - Bash(grep *)
  - Read
---

# Check Rules — jenkins-sdlc-pipeline

Esegui solo i test ArchUnit per verificare rapidamente le regole architetturali senza build completa.

## Comando

```bash
# Esegui solo i test ArchUnit (filtra per nome classe)
mvn test -Dtest="*ArchitectureTest*,*ArchTest*,*LayerTest*"
```

Se non conosci il nome della classe ArchUnit:
```bash
find src/test -name "*.java" | xargs grep -l "ArchUnit\|ArchRuleDefinition\|layeredArchitecture" 2>/dev/null
```

Poi esegui:
```bash
mvn test -Dtest="<NomeClasseTrovata>"
```

## Regole architetturali attive

```
controller → service, domain      (controller può importare service e domain)
service    → repository, domain   (service può importare repository e domain)
repository → domain               (repository può importare solo domain)
```

**Violazione tipica:** un controller che importa un repository direttamente.

## Interpretazione

- `ArchConditionViolation`: indica quale import/dipendenza viola la regola
- Il fix è sempre nel codice applicativo (`src/main/java/`), mai nelle regole ArchUnit
- Se una regola sembra sbagliata, discutila prima di modificarla — è un contratto architetturale

## Dopo la verifica

Se tutti i test ArchUnit passano, puoi procedere con la build completa:
```bash
mvn clean verify
```
