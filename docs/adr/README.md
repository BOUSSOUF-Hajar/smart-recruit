# Décisions d'architecture (ADR)

Une fiche courte par décision structurante : contexte, décision, alternatives écartées, conséquences, puis ce que le développement a confirmé ou changé. La liste reprend le registre de l'[architecture §10](../architecture.md#10-registre-des-décisions-adr).

| ADR | Décision | Statut |
| --- | --- | --- |
| [001](001-monolithe-modulaire.md) | Monolithe modulaire vérifié par Spring Modulith | Acceptée |
| [002](002-versions-de-la-stack.md) | Java 25, Spring Boot 4.1, Angular 22, PostgreSQL 18 | Acceptée |
| [003](003-jwt-et-refresh-token.md) | JWT court et refresh token en cookie HttpOnly avec rotation | Acceptée |
| [004](004-meme-origine-via-nginx.md) | Frontend et API sur la même origine via Nginx | Acceptée |
| [005](005-cles-uuid-v7.md) | Clés primaires UUID v7 | Acceptée |
| [006](006-references-par-identifiant.md) | Références par identifiant entre agrégats | Acceptée |
| [007](007-workflow-dans-application-status.md) | Workflow de candidature porté par l'énumération `ApplicationStatus` | Acceptée |
| [008](008-score-par-formule.md) | Score de matching calculé par formule, LLM pour l'extraction et l'explication | Acceptée |
| [009](009-ia-asynchrone-par-table-de-taches.md) | Traitement IA asynchrone par table de tâches | Acceptée |
| [010](010-ollama-restclient-aiservice.md) | Ollama appelé par `RestClient` derrière `AIService` ; modèle choisi par test comparatif | Acceptée, modèle provisoire |
| [011](011-enumerations-en-texte-avec-check.md) | Énumérations stockées en texte avec contrainte CHECK | Acceptée |
| [012](012-diagrammes-en-mermaid.md) | Diagrammes en Mermaid versionnés avec le code | Acceptée |

## Écrire une nouvelle fiche

- Numéro suivant, nom de fichier `NNN-titre-court.md`.
- Une décision acceptée n'est pas réécrite : si elle change, une nouvelle fiche la remplace, et l'ancienne passe au statut « remplacée par ADR-NNN ».
