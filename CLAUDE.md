# SmartRecruit : règles de travail

## 1. Le projet
ATS (logiciel de suivi des candidatures) multi-entreprises, projet portfolio.
Stack : Spring Boot 4.1 (Java 25, Spring Modulith, Flyway, PostgreSQL 18), Angular 22, Ollama, Docker Compose, GitHub Actions.

## 2. Où trouver l'information
- `README.md` : présentation, installation, tests.
- `docs/roadmap.md` : surtout §8 « Rituel par story » et le bilan de chaque phase.
- `docs/user-stories.md` : stories, critères, statuts.
- `docs/stories/US-XX.md` : une note par story livrée.
- `docs/api.md` : contrat REST.
- `docs/architecture*.md` : architecture globale (registre des décisions au §10), backend et frontend.
- `docs/adr/` : une fiche par décision ; `docs/preparation-entretien.md` : questions d'entretien par thème.

## 3. Règles de travail
- Toujours répondre en français.
- Une story à la fois, en tranche verticale (backend, frontend, tests).
- Pour chaque story, écrire `docs/stories/US-XX.md` : critères avec leur preuve, fichiers, choix et alternatives écartées, commandes, questions d'entretien.
- Mettre à jour les documents concernés : user-stories, api, modèle de données, architecture, roadmap, README.
- Si une vérification est impossible, le dire clairement au lieu de supposer qu'elle passe.

## 4. Conventions du code
- Frontières Spring Modulith : API publique dans les paquets `api` annotés `@NamedInterface` ; inversion de dépendances par interfaces.
- Une migration Flyway publiée n'est jamais modifiée : on en ajoute une nouvelle.
- Aucun secret dans le dépôt (`.env` est ignoré ; `.env.example` ne contient que des valeurs fictives).
- Dates stockées en UTC (RG-32).
- Exceptions documentées aux frontières : lectures SQL du module `reporting` et de `RecruiterApplicationSearch` (US-30).

## 5. Avant de déclarer une étape terminée
- Backend (`backend/`) : `./mvnw verify` (tests unitaires et d'intégration, seuil JaCoCo de 70 % sur les services) et `./mvnw spotbugs:check`.
- Frontend (`frontend/`) : `npm run lint`, `npm run format:check`, `npm run test:ci`, `npm run e2e:typecheck`, `npm run build`.
- Bout en bout : `npm run e2e` contre l'application lancée par `docker compose up --build`.

## 6. Comptes de démonstration
Profil `demo`, mot de passe commun `Demo-SmartRecruit-2026`.
- Admin : `admin@smartrecruit.example`
- Recruteur : `recruteur@atlasia.example`
- Candidat : `candidat@demo.example`

## 7. Points en attente
- Valider la vraie construction des images Docker.
- Faire passer la CI au vert sur GitHub (jalon J4).
- Exécuter le banc d'essai IA, puis épingler le modèle et la version de l'image Ollama.
- Générer le wrapper Maven (`mvnw`).
- Phase 12, US-58 (en cours) : captures d'écran ; vérifier springdoc (écrit, jamais compilé). README, ADR, préparation à l'entretien et diagrammes : faits.
