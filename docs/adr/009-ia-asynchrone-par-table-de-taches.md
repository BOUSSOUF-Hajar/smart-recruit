# ADR-009 — Traitement IA asynchrone par table de tâches

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [cahier des charges G-7](../cahier-des-charges.md), [US-41](../stories/US-41.md)

## Contexte

Sur un poste sans GPU, une analyse de CV par un modèle local peut dépasser une minute. Une requête HTTP bloquée aussi longtemps est fragile (délais des proxys, double clic, onglet fermé), et elle tiendrait une connexion de base de données pendant tout l'appel.

## Décision

- `POST /applications/{id}/ai/resume-analysis` crée une tâche `PENDING` dans la table `ai_task` et répond **202** avec un en-tête `Location`. Contrôle d'entreprise, limite de 10 demandes par minute, historique et audit sont faits dans la même transaction.
- **Après le commit** (`@TransactionalEventListener(AFTER_COMMIT)`), la tâche est confiée à un exécuteur dédié : 2 threads, file de 50 ; si la file est pleine, la tâche passe `FAILED`.
- Le traitement se fait en **trois temps de transaction courts** : passage à `RUNNING`, travail sans transaction (texte du PDF, pseudonymisation, appel au modèle), enregistrement et passage à `DONE`.
- Le frontend interroge l'état de la tâche **toutes les 3 secondes** jusqu'à un état final.

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| Appel synchrone | Requête bloquée plus d'une minute, connexion du pool tenue pendant tout l'appel |
| Bus de messages (Kafka, RabbitMQ) | Un seul type de traitement asynchrone, quelques analyses par heure : un conteneur de plus sans gain |
| `@Async` sans table | Rien ne survit à un redémarrage ; impossible de suivre l'état d'une tâche |
| WebSocket ou Server-Sent Events | Plus complexe que l'interrogation périodique pour quelques secondes d'attente |

## Conséquences

- **+** L'utilisateur voit une progression ; un double clic renvoie la tâche déjà en cours.
- **+** L'état est en base : visible, auditable, rattrapable.
- **−** Tâches orphelines possibles : au démarrage (`ApplicationReadyEvent`), toute tâche `PENDING` ou `RUNNING` passe `FAILED`, sans quoi l'écran attendrait indéfiniment. Le recruteur relance.
- **−** Avec plusieurs instances du backend, il faudrait un mécanisme de prise de tâche partagé ; le verrou optimiste sur `ai_task` empêche déjà deux exécuteurs de prendre la même tâche.

## Ce que le développement a confirmé ou changé

- Les résultats (`resume_analysis`, `match_result`) sont stockés en **JSONB via JDBC**, et non en entités JPA : ce sont des documents écrits et lus d'un bloc, jamais modifiés, dont aucune requête ne filtre le contenu.
- L'analyse est rattachée au CV, pas à la candidature : un même CV envoyé à plusieurs offres n'est analysé qu'une fois par version de prompt.
