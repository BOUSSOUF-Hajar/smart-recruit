# SmartRecruit — Guide de reprise

Ce guide permet de lancer, vérifier et continuer le projet en autonomie, sans l'historique des sessions de travail. Il est à jour au 3 novembre 2026.

Il complète le [README](../README.md), qui présente le projet, et la [roadmap](roadmap.md), qui en raconte l'avancement. Ici, on trouve seulement ce qu'il faut savoir pour reprendre.

## Sommaire

1. [Où en est le projet](#1-où-en-est-le-projet)
2. [Préparer le poste](#2-préparer-le-poste)
3. [Lancer l'application](#3-lancer-lapplication)
4. [Vérifier avant de livrer](#4-vérifier-avant-de-livrer)
5. [Le cycle d'une modification](#5-le-cycle-dune-modification)
6. [Ce qui reste à faire, pas à pas](#6-ce-qui-reste-à-faire-pas-à-pas)
7. [Entretenir le projet](#7-entretenir-le-projet)
8. [Problèmes connus et solutions](#8-problèmes-connus-et-solutions)
9. [Travailler avec un assistant IA](#9-travailler-avec-un-assistant-ia)
10. [Où trouver quoi](#10-où-trouver-quoi)

## 1. Où en est le projet

| Élément | État |
| --- | --- |
| Phases 1 à 11 (57 stories sur 58) | Livrées |
| Phase 12, US-58 (documentation) | En cours : il manque les captures d'écran |
| Dépôt | Public : https://github.com/BOUSSOUF-Hajar/smart-recruit |
| CI (GitHub Actions) | Entièrement verte sur `main` le 3 novembre 2026 (6 jobs) : **jalon J4 atteint** |
| Backend sur le poste | `./mvnw verify` et SpotBugs verts le 3 novembre 2026 (325 tests unitaires, 225 d'intégration) |
| Frontend sur le poste | Pas encore vérifié : `frontend/node_modules` n'est pas installé |
| Pile Docker complète sur le poste | Pas encore lancée : images trop lourdes pour la connexion actuelle ; la CI la lance à chaque push |
| Banc d'essai de l'IA | Pas exécuté : modèle `qwen3.5:9b` provisoire, image Ollama sur `latest` |

## 2. Préparer le poste

### Logiciels

| Logiciel | Version | Remarque |
| --- | --- | --- |
| Git | récente | Déjà configuré : `BOUSSOUF-Hajar` |
| JDK | **25** | Installé dans `%USERPROFILE%\.jdks\jdk-25.0.5+8` (Temurin 25.0.5) |
| Node.js | 24 LTS (ou 22.22.3 et plus) | Pour le frontend et Playwright |
| Docker Desktop | 28 ou plus | À démarrer avant les tests d'intégration et `docker compose` |
| Maven | inutile | Le wrapper `./mvnw` (ou `mvnw.cmd`) télécharge Maven 3.9.14 tout seul |

### Rendre le JDK 25 visible

Les JDK 17 et 21 sont aussi installés sur le poste. Si `java -version` n'affiche pas 25, `./mvnw verify` échoue avant même de compiler.

Pour une seule session Git Bash :

```bash
export JAVA_HOME="$USERPROFILE/.jdks/jdk-25.0.5+8"
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # doit afficher 25.0.5
```

Pour toujours : Paramètres Windows → « Modifier les variables d'environnement système » → Variables d'environnement. Mettre `JAVA_HOME` à `C:\Users\HP\.jdks\jdk-25.0.5+8`, puis placer `%JAVA_HOME%\bin` en tête de `Path`. Rouvrir le terminal et VS Code ensuite.

### Fichier `.env`

```bash
cp .env.example .env
```

Les valeurs sont fictives et suffisent en local. `.env` est ignoré par Git : ne jamais le commiter, ni y mettre un vrai secret destiné au dépôt.

### Images Docker déjà présentes

`postgres:18-alpine` est téléchargée : les tests d'intégration du backend tournent sans autre téléchargement.

Il manque encore, pour `docker compose up --build` :
- `eclipse-temurin:25-jre-alpine` et `maven` (image backend) ;
- `node` et `nginxinc/nginx-unprivileged:1.29-alpine` (image frontend) ;
- `ollama/ollama` et le modèle (environ 6,6 Go) pour l'IA, facultatifs.

## 3. Lancer l'application

### En une commande (démonstration)

```bash
cp .env.example .env          # une seule fois
docker compose up --build     # premier lancement : plusieurs minutes
```

Puis ouvrir http://localhost:8080. Comptes de démonstration, mot de passe commun `Demo-SmartRecruit-2026` :

| Rôle | Email |
| --- | --- |
| Administrateur | `admin@smartrecruit.example` |
| Recruteur | `recruteur@atlasia.example` |
| Candidat | `candidat@demo.example` |

- Documentation de l'API : http://localhost:8080/api/docs/swagger-ui.html (profils `dev` et `demo` seulement).
- Sans l'IA (machine modeste ou connexion lente) : `AI_ENABLED=false` dans `.env`. L'application reste entièrement utilisable.
- Arrêter : `docker compose down`. Repartir d'une base vide : `docker compose down -v` (supprime aussi les CV et le modèle téléchargés).

### En développement

```bash
docker compose -f docker-compose.dev.yml up -d postgres   # PostgreSQL 18 sur localhost:5432

cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo   # http://localhost:8080

cd frontend                                                 # autre terminal
npm ci
npm start                                                   # http://localhost:4200
```

## 4. Vérifier avant de livrer

### Sur le poste

| Où | Commande | Vérifie |
| --- | --- | --- |
| `backend/` | `./mvnw verify` | Tests unitaires et d'intégration (Docker démarré), seuil de couverture de 70 % sur les services ; environ 5 min 30 |
| `backend/` | `./mvnw spotbugs:check` | Analyse statique |
| `frontend/` | `npm run lint` | ESLint et frontières entre dossiers |
| `frontend/` | `npm run format:check` | Formatage Prettier (`npx prettier --write …` pour corriger) |
| `frontend/` | `npm run test:ci` | Tests unitaires Vitest |
| `frontend/` | `npm run e2e:typecheck` | Typage des tests Playwright |
| `frontend/` | `npm run build` | Build de production |
| `frontend/` | `npm run e2e` | Playwright, contre l'application lancée par `docker compose up --build` (une fois : `npx playwright install chromium`) |

Si une vérification ne peut pas tourner (réseau, Docker), l'écrire dans la note de story plutôt que la supposer verte.

### Sur GitHub (la CI)

Chaque push lance [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) : https://github.com/BOUSSOUF-Hajar/smart-recruit/actions

| Job | Rôle |
| --- | --- |
| Backend | `./mvnw verify` et SpotBugs ; rapport de couverture en artefact |
| Frontend | lint, formatage, Vitest, typage e2e, build, `npm audit` |
| Dépendances et secrets (Trivy) | Analyse du dépôt : dépendances vulnérables, secrets commités |
| Bout en bout | Construit les images, lance `docker compose` (profil demo), passe Playwright |
| Images Docker (backend, frontend) | Construit et analyse chaque image ; publication sur ghcr.io depuis `main` seulement |

Lire un échec :
- ouvrir l'exécution, puis le job en rouge, puis l'étape en rouge ;
- pour l'analyse des images, les vulnérabilités sont aussi listées en annotations et dans le résumé de l'exécution ;
- en cas d'échec des tests, les rapports sont téléchargeables en artefacts (`backend-test-reports`, `e2e-report`).

## 5. Le cycle d'une modification

Le rituel complet est dans la [roadmap §8](roadmap.md#8-rituel-par-story). En bref :

1. Partir de `main` à jour : `git switch main && git pull`.
2. Créer une branche : `git switch -c feat/us-xx-sujet` (ou `docs/…`, `fix/…`, `ci/…`).
3. Coder en tranche verticale : backend, frontend et tests ensemble.
4. Lancer les vérifications de la section 4.
5. Commiter au format Conventional Commits : `feat(application): …`, `fix(backend): …`, `docs(us-58): …`.
6. Pousser : `git push -u origin feat/us-xx-sujet`, puis ouvrir la pull request depuis le lien affiché.
7. Attendre la CI verte, puis fusionner avec « Squash and merge ».
8. Pour une story : écrire `docs/stories/US-XX.md` (critères et preuves, fichiers, choix et alternatives écartées, commandes, questions d'entretien) et mettre à jour les documents touchés (user stories, API, modèle de données, architecture, roadmap, README).

Règles du code à ne pas casser :
- une migration Flyway publiée (`V1` à `V14`…) n'est **jamais** modifiée : on en ajoute une nouvelle ;
- un module n'utilise un autre module que par son paquet `api` (Spring Modulith le vérifie dans les tests) ;
- dates stockées en UTC ;
- aucun secret dans le dépôt.

## 6. Ce qui reste à faire, pas à pas

### 6.1 Relire et clore les stories

Le jalon J4 est atteint (CI verte sur `main` le 3 novembre 2026, noté dans la roadmap). Les stories US-01 à US-57 sont encore « En revue » dans `docs/user-stories.md` : les relire, puis passer leur statut à « Terminé ». Les notes de `docs/stories/` donnent, pour chacune, les critères et leur preuve.

### 6.2 Captures d'écran (fin d'US-58)

Il faut l'application lancée avec les données de démonstration (section 3). Si la connexion empêche `docker compose up --build`, le mode développement suffit : PostgreSQL déjà téléchargé, backend avec `dev,demo`, frontend avec `npm start` ; seul `npm ci` est à télécharger.

1. Créer `docs/screenshots/`.
2. Prendre les écrans dans cet ordre (largeur de fenêtre d'environ 1440 px, PNG) :
   1. dossier de candidature côté recruteur (profil, CV, historique, aide IA) : `dossier-candidature.png` ;
   2. liste filtrée des candidatures : `candidatures.png` ;
   3. tableau de bord recruteur : `tableau-de-bord-recruteur.png` ;
   4. profil et candidatures d'un candidat : `espace-candidat.png` ;
   5. tableau de bord et journal d'audit de l'administrateur : `administration.png`.
3. Réduire leur poids si besoin (moins de 500 Ko chacune).
4. README, rubrique « Captures d'écran » : remplacer « À venir » par les images, par exemple `![Dossier de candidature](docs/screenshots/dossier-candidature.png)`.
5. US-58 : passer le critère « Captures » à « Fait », puis la story à « Terminé » dans `docs/user-stories.md` et la note.
6. Roadmap, phase 12 : noter le jalon J5 après une relecture complète du dépôt.

### 6.3 Banc d'essai de l'IA

Le protocole complet est dans [ai-model-benchmark.md](ai-model-benchmark.md) §2. Il demande environ 10 Go de téléchargements et 16 Go de RAM recommandés, ce qui dépasse le poste actuel (7,8 Go alloués à Docker) : une autre machine ou une meilleure connexion sera nécessaire.

1. `docker compose -f docker-compose.dev.yml up -d ollama`.
2. Télécharger les modèles candidats (`ollama pull qwen3.5:9b`, etc.).
3. `python3 backend/scripts/ai-benchmark/run_benchmark.py qwen3.5:9b llama3.1:8b mistral:7b gemma3:12b`.
4. Reporter le tableau dans le §4 du document et écrire la décision.
5. Épingler :
   - le modèle : `AI_MODEL` dans `.env.example` et la valeur par défaut dans `application.yml` ;
   - l'image Ollama : remplacer `latest` par la version mesurée dans `docker-compose.yml` (`OLLAMA_VERSION`) et `docker-compose.dev.yml`.
6. Mettre à jour la roadmap §10, le README (rubrique IA, état du projet) et `CLAUDE.md` §7.

### 6.4 Recommandé : protéger `main`

Sur GitHub : Settings → Branches → Add branch ruleset, cible `main`. Cocher « Require a pull request before merging » et « Require status checks to pass », puis choisir les jobs de la CI. Ainsi, rien n'arrive sur `main` sans CI verte. C'est prévu par US-05.

### 6.5 Facultatif (phase 12)

- Courte vidéo de démonstration (parcours candidat puis recruteur, deux à trois minutes).
- Ligne de CV et description LinkedIn du projet.
- Relire [preparation-entretien.md](preparation-entretien.md) et s'entraîner au pitch.

## 7. Entretenir le projet

- **Vulnérabilité signalée par l'analyse des images** :
  - paquet système Alpine : relancer la CI suffit souvent, car les deux Dockerfiles font `apk upgrade` à chaque construction ;
  - bibliothèque Java (par exemple Tomcat) : monter la version de correctif de Spring Boot dans `backend/pom.xml` (comme 4.1.1 → 4.1.2, voir [US-54](stories/US-54.md) §4) plutôt que forcer la version de la bibliothèque seule.
- **Dépendances** : aucune mise à jour automatique n'est configurée. Ajouter un `.github/dependabot.yml` (Maven, npm, Docker, GitHub Actions, mises à jour groupées chaque semaine) serait le prochain pas, avec une revue de chaque pull request.
- **Montée de version majeure** (Spring Boot, Angular) : une branche dédiée, la CI comme filet, et un ADR si le choix le mérite ([docs/adr/](adr/README.md)).

## 8. Problèmes connus et solutions

| Symptôme | Cause | Solution |
| --- | --- | --- |
| `./mvnw verify` : « release version 25 not supported » ou erreur avant compilation | Mauvais JDK actif | Section 2, « Rendre le JDK 25 visible » |
| Tests `*IT` : « Could not find a valid Docker environment » | Docker Desktop arrêté | Le démarrer et attendre qu'il soit prêt (`docker info`) |
| Tests `*IT` bloqués sur le téléchargement de `testcontainers/ryuk` | Image de nettoyage de Testcontainers absente, connexion lente | `export TESTCONTAINERS_RYUK_DISABLED=true` le temps du lancement, puis supprimer les conteneurs restants : `docker ps -a` |
| Maven : « Could not transfer artifact … Read timed out » | Connexion lente | Relancer : Maven reprend où il en était ; `-Dmaven.wagon.http.retryHandler.count=5` aide |
| `docker pull` : « TLS handshake timeout » | Connexion lente vers Docker Hub | Relancer en boucle ; ou passer par la CI, qui construit et lance tout |
| Fichiers modifiés par Git sans raison (fins de ligne) | `core.autocrlf=true` sous Windows | `.gitattributes` impose LF ; après un changement, `git add --renormalize .` |
| Port 8080 déjà utilisé | Autre application locale | `HTTP_PORT=8081` dans `.env` (et `PUBLIC_URL` en conséquence) |
| Assistance IA « indisponible » | Ollama arrêté ou modèle pas encore téléchargé | Normal : l'application fonctionne sans ; suivre `docker compose logs -f ollama-model` |
| Tests d'expiration autour de `Africa/Casablanca` | tzdata 2026e : UTC+0 permanent | Déjà géré par un décalage fixe dans les tests (README, « Fuseau horaire marocain ») |
| Télécharger un gros fichier sur une connexion instable | Coupures | `curl -L -C - -o fichier URL`, en boucle : `-C -` reprend où le fichier s'est arrêté |

## 9. Travailler avec un assistant IA

- [`CLAUDE.md`](../CLAUDE.md), à la racine, donne à un assistant de code les règles du projet : langue, rituel, conventions, commandes de vérification, comptes de démonstration, points en attente. Le tenir à jour (§7 surtout) quand un point avance.
- Pour reprendre une tâche, il suffit de lui indiquer la section de ce guide concernée, par exemple « fais le §6.2 du guide de reprise ».
- Les commits et les pull requests ne mentionnent pas l'assistant.
- Relire et comprendre chaque modification avant de la fusionner : c'est ce qui permet de défendre le projet en entretien.

## 10. Où trouver quoi

| Besoin | Document |
| --- | --- |
| Présenter le projet, l'installer | [README](../README.md) |
| Avancement, jalons, rituel par story | [roadmap.md](roadmap.md) |
| Ce que fait chaque story et comment elle a été prouvée | [user-stories.md](user-stories.md), [stories/](stories/) |
| Contrat de l'API | [api.md](api.md), ou Swagger UI en `dev` et `demo` |
| Pourquoi tel choix technique | [adr/](adr/README.md), [architecture.md](architecture.md) §10 |
| Tables et migrations | [modele-de-donnees.md](modele-de-donnees.md), `backend/src/main/resources/db/migration/` |
| Questions d'entretien | [preparation-entretien.md](preparation-entretien.md) |
| Choix du modèle d'IA | [ai-model-benchmark.md](ai-model-benchmark.md) |
