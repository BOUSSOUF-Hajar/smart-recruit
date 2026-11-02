# ADR-007 — Workflow de candidature porté par l'énumération `ApplicationStatus`

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [diagramme de classes §5](../diagrams/classes.md), [US-32](../stories/US-32.md), [US-34](../stories/US-34.md)

## Contexte

Une candidature passe par 8 statuts, dont 3 finaux. Les règles sont précises : 15 transitions autorisées sur 64 couples possibles, rejet avec motif obligatoire, retrait réservé au candidat, proposition impossible sans entretien terminé. Dispersées dans des `if` de services, ces règles finiraient contournées par un nouvel endpoint.

## Décision

Deux niveaux, tous deux sans Spring :

1. **La table des transitions vit dans l'énumération** `ApplicationStatus` (`allowedTargets()`, `canMoveTo()`, `isFinal()`). Elle se lit comme la table du cahier des charges :
   `SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW → OFFER → HIRED`, et tout statut non final peut aller vers `REJECTED` (recruteur, avec motif) ou `WITHDRAWN` (candidat).
2. **Les conditions qui dépendent du contexte** (qui agit, motif, entretien terminé) sont dans `ApplicationWorkflow`. Elles sont vérifiées dans un ordre fixe : table, puis acteur, puis motif, puis entretien.

L'agrégat `Application.moveTo()` est la seule porte d'entrée. Le serveur renvoie au frontend les transitions possibles, et le frontend n'affiche que celles-là.

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| Spring Statemachine | Une dépendance lourde et une configuration à apprendre pour 15 transitions |
| Patron État (une classe par statut) | 8 classes pour une table qui tient en 8 lignes |
| `if` dans le service | Règles dispersées, contournables, difficiles à tester exhaustivement |

## Conséquences

- **+** Le test `onlyTheFifteenTransitionsOfTheTableAreAllowed` vérifie les **64 couples** contre une table attendue écrite à la main, en quelques millisecondes.
- **+** Une requête forgée (`SUBMITTED → HIRED`) reçoit toujours `INVALID_STATUS_TRANSITION` (409), jamais un message trompeur.
- **+** L'énumération est dans l'API publique du module : le module Entretiens lit les statuts sans dépendre des entités.
- **−** Une transition qui dépend d'un autre module (OFFER exige un entretien terminé) a demandé une interface, `CompletedInterviewChecker`, pour éviter un cycle ([ADR-001](001-monolithe-modulaire.md)).

## Ce que le développement a confirmé ou changé

- Concurrence : la version lue par l'écran est comparée avant la modification, puis `@Version` protège l'écriture. Deux recruteurs qui agissent en même temps : le second reçoit `CONCURRENT_MODIFICATION` et l'écran propose de recharger.
- L'historique (`application_event`) est écrit dans la même transaction que le changement de statut et rendu immuable par un déclencheur PostgreSQL (US-34).
- Rejet ou retrait : les entretiens planifiés sont annulés automatiquement par un écouteur d'événement (phase 7).
