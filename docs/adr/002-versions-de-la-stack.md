# ADR-002 — Java 25, Spring Boot 4.1, Angular 22, PostgreSQL 18

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [cahier des charges G-11, F](../cahier-des-charges.md)

## Contexte

Le projet démarre en octobre 2026. Beaucoup de tutoriels et d'offres d'emploi parlent encore de Java 17/21 et de Spring Boot 3, mais le support open source de Spring Boot 3.5, dernière 3.x, a pris fin le 30 juin 2026. Un projet portfolio qui démarre sur une génération en fin de support serait difficile à défendre.

## Décision

| Brique | Version retenue | Raison principale |
| --- | --- | --- |
| Java | 25 (LTS, septembre 2025) | Support long ; records, pattern matching, threads virtuels |
| Spring Boot | 4.1 (Spring Framework 7, Jakarta EE 11, Jackson 3) | Génération maintenue au démarrage du projet |
| Spring Modulith | 2.1 | Version alignée sur Spring Boot 4.1 ([ADR-001](001-monolithe-modulaire.md)) |
| Angular | 22 (signaux, composants autonomes), Material 3 | Version en support actif depuis juin 2026 |
| PostgreSQL | 18 | `uuidv7()` natif ([ADR-005](005-cles-uuid-v7.md)), recherche plein texte suffisante |

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| Java 21 + Spring Boot 3.5 | Plus de support open source : on démarrerait un projet neuf sur une base en fin de vie |
| Spring Boot 4.0 | Repli prévu si springdoc n'était pas compatible avec la 4.1 (roadmap §10) ; Spring Boot 4.1 a été retenu, mais springdoc n'a finalement pas été intégré (voir plus bas) |

## Conséquences

- **+** Versions maintenues pendant toute la durée de vie raisonnable du projet.
- **+** Bon sujet d'entretien : savoir citer les différences de Spring Boot 4 (Jakarta EE 11, Jackson 3, `RestClient`, modularisation des starters).
- **−** Moins de réponses toutes faites en ligne ; certaines bibliothèques ont dû être vérifiées une à une.
- **−** Il faut un JDK 25 sur le poste de développement : avec un JDK plus ancien, `./mvnw verify` échoue avant même de compiler.

## Ce que le développement a confirmé ou changé

- Versions effectives dans le dépôt : Spring Boot **4.1.1**, Spring Modulith **2.1.0**, Angular **22.2**, TypeScript **6.0**, Vitest **5**, Playwright **1.64**.
- Les tests unitaires du frontend utilisent Vitest à la place de Karma et Jasmine.
- **Écart constaté pendant la phase 12** : springdoc-openapi, prévu par le cahier des charges (F.1) et l'architecture backend, n'avait jamais été ajouté au `pom.xml`. Il l'est désormais en US-58 (springdoc **3.1.1**, construit sur Spring Boot 4.1.0), avec la documentation servie sous `/api/docs` en `dev` et `demo` seulement ([US-58](../stories/US-58.md)).
- La tzdata 2026e livrée avec le JDK 25 place `Africa/Casablanca` en UTC+0 permanent ; les tests d'expiration utilisent un décalage fixe pour ne pas dépendre de cette version (README, « Fuseau horaire marocain »).
