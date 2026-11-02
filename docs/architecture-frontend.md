# SmartRecruit — Architecture Angular

Oct 19, 2026 · @Hajar

## 0. Décisions

Le frontend suit les choix par défaut d'Angular 22 (composants autonomes, signals, OnPush, sans Zone.js), découpé par espace utilisateur et chargé à la demande, avec Angular Material ; une seule décision reste à valider, celle des formulaires.

Ce que change Angular 22, sorti le 3 juin 2026 : les Signal Forms et les API `resource` deviennent stables, OnPush devient la stratégie de détection par défaut, le client HTTP passe à Fetch et Vitest remplace Karma comme lanceur de tests ([synthèse](https://www.bacancytechnology.com/blog/angular-22)). Le mode sans Zone.js était déjà le défaut des nouveaux projets depuis la v21.

| Sujet | Décision | Raison |
| --- | --- | --- |
| Composants | Autonomes (standalone), sans NgModule | Défaut d'Angular ; chaque composant déclare ses dépendances |
| Réactivité | Signals (`signal`, `computed`, `input`, `output`) ; RxJS seulement pour les flux HTTP et la saisie temporisée | Modèle recommandé, plus simple à lire que des `subscribe` imbriqués |
| Détection des changements | OnPush et sans Zone.js (défauts Angular 22) | Performance, et comportement prévisible avec les signals |
| Templates | Contrôle de flux intégré `@if`, `@for`, `@switch`, `@defer` | Syntaxe actuelle, remplace `*ngIf` et `*ngFor` |
| Découpage | Par espace : public, candidat, recruteur, admin | Correspond aux routes et aux rôles ; un candidat ne télécharge jamais le code recruteur |
| État | Stores par feature à base de signals, sans NgRx | Peu d'état partagé ; NgRx ajouterait actions, reducers et effects sans besoin réel |
| Lecture de données | `httpResource` paramétré par des signals (filtres, page) | Chargement, erreur et rechargement gérés sans abonnement manuel |
| Écriture | `HttpClient` dans des services API, puis rechargement de la ressource concernée | Les mutations restent explicites et testables |
| UI | Angular Material (Material 3) et CDK | Composants accessibles et cohérents sans créer de design system |
| Langue | Français ; textes marqués avec `$localize` pour une traduction future | G-9 |
| Tests | Vitest pour les tests unitaires, Playwright pour le bout en bout | Défaut d'Angular 22 ; US-57 |

### Décision à valider : Reactive Forms ou Signal Forms

|  | Reactive Forms typés | Signal Forms |
| --- | --- | --- |
| Statut | Stables depuis des années | Stables depuis Angular 22 (juin 2026) |
| Présence dans les projets existants | Quasi universelle | Encore rare |
| Cohérence avec le reste de l'app | Basés sur RxJS, à relier aux signals | Natifs signals |
| Demandé par ton prompt | Oui | Non |

**Recommandation : Reactive Forms typés partout.** C'est ce que tu croiseras dans presque toutes les bases de code en entreprise, et ce que ton prompt demande. Les Signal Forms sont une bonne évolution à citer en entretien, et pourront être essayés sur un formulaire isolé une fois le MVP livré. Mélanger les deux dès le départ doublerait la charge d'apprentissage.

## 1. Arborescence

Quatre dossiers de premier niveau, comme demandé : `core` (une seule instance, chargé au démarrage), `shared` (réutilisable, sans état), `layouts` (gabarits de page) et `features` (le métier, chargé à la demande).

```
frontend/src/app/
├── app.config.ts              # providers : router, http + intercepteurs, initialisation de session
├── app.routes.ts              # routes de premier niveau, chargement différé par espace
├── core/
│   ├── auth/                  # AuthService, AuthStore (signals), session-initializer
│   ├── http/                  # intercepteurs : correlation, auth, refresh, error
│   ├── guards/                # authGuard, roleGuard, passwordChangeGuard, unsavedChangesGuard
│   ├── errors/                # ApiError, GlobalErrorHandler, NotificationService
│   └── models/                # Page<T>, ProblemDetails, enums partagés avec l'API
├── shared/
│   ├── ui/                    # page-header, empty-state, confirm-dialog, status-chip,
│   │                          # file-upload, skill-autocomplete, data-table-paginator
│   ├── forms/                 # validateurs, applyServerErrors, form-field-error
│   └── pipes/                 # candidateStatusLabel, enumLabel, relativeDate
├── layouts/
│   ├── public-layout/         # en-tête public, pied de page
│   └── app-layout/            # barre latérale selon le rôle, menu utilisateur
└── features/
    ├── public/                # jobs (recherche, détail), auth (login, register)
    ├── account/               # changement de mot de passe
    ├── candidate/             # dashboard, profile, applications, interviews
    ├── recruiter/             # dashboard, jobs, applications, interviews, ai
    └── admin/                 # dashboard, users, companies, categories, skills, audit
```

**Règles de dépendance**

- `features` peut importer `core` et `shared` ; jamais une autre feature d'un autre espace.
- `shared` n'importe rien de `features` et ne contient aucun appel HTTP.
- `core` n'importe rien de `features`.
- Ces règles sont vérifiées par ESLint (règle de frontières d'import), comme Spring Modulith le fait pour le backend.

## 2. Anatomie d'une feature

Chaque feature a la même forme : des pages routées qui orchestrent, des composants de présentation qui affichent, et une couche d'accès aux données.

```
features/recruiter/applications/
├── applications.routes.ts
├── pages/
│   ├── application-list.page.ts      # filtres dans l'URL, tableau paginé
│   └── application-detail.page.ts    # onglets profil, CV, historique, notes, entretiens, IA
├── components/
│   ├── status-change-dialog.ts       # cible + motif de rejet
│   ├── application-timeline.ts
│   └── notes-panel.ts
└── data-access/
    ├── applications.api.ts           # appels HttpClient, un par endpoint
    ├── application-detail.store.ts   # état du dossier ouvert (signals)
    └── application.models.ts         # interfaces TypeScript des DTO
```

| Type | Rôle | Injecte des services ? | Exemple |
| --- | --- | --- | --- |
| Page | Liée à une route ; lit les paramètres, appelle le store, gère navigation et dialogues | Oui | `application-detail.page.ts` |
| Composant de présentation | Reçoit des `input()`, émet des `output()`, aucune logique d'accès aux données | Non | `application-timeline.ts` |
| Service API | Un appel HTTP par méthode, typé avec les DTO | `HttpClient` seulement | `applications.api.ts` |
| Store | État de la feature en signals, actions qui appellent l'API | Le service API | `application-detail.store.ts` |
| Modèles | Interfaces TypeScript, miroir des DTO de l'API | — | `application.models.ts` |

### Correspondance avec les modules de ton prompt

| Module du prompt | Emplacement |
| --- | --- |
| auth | `features/public/auth` (écrans) + `core/auth` (session) |
| jobs | `features/public/jobs` (recherche) + `features/recruiter/jobs` (gestion) |
| applications | `features/candidate/applications` + `features/recruiter/applications` |
| candidates | `features/candidate/profile` |
| interviews | `features/candidate/interviews` + `features/recruiter/interviews` |
| dashboard | Un `dashboard` dans chaque espace |
| ai | `features/recruiter/ai` : composants affichés dans l'onglet IA du dossier |
| admin | `features/admin` |

Un même domaine apparaît dans deux espaces quand deux publics le voient différemment, exactement comme les deux vues d'une candidature dans l'API.

## 3. Routage et guards

Chaque espace est chargé à la demande derrière un guard `canMatch` : un utilisateur sans le bon rôle ne télécharge même pas le code de l'espace. Les guards améliorent l'expérience, la sécurité reste assurée par l'API.

```
/                       → redirection vers /jobs
/jobs, /jobs/:id        PublicLayout  · public
/login, /register       PublicLayout  · guestGuard
/account/password       AppLayout     · authGuard
/candidate/...          AppLayout     · canMatch [authGuard, passwordChangeGuard, roleGuard(CANDIDATE)] · loadChildren
/recruiter/...          AppLayout     · canMatch [authGuard, passwordChangeGuard, roleGuard(RECRUITER)] · loadChildren
/admin/...              AppLayout     · canMatch [authGuard, passwordChangeGuard, roleGuard(ADMIN)]     · loadChildren
/forbidden, /not-found  PublicLayout
**                      → /not-found
```

Les routes détaillées de chaque espace reprennent l'inventaire des 26 écrans de l'analyse fonctionnelle (section 8).

| Guard | Type | Condition | Si refusé |
| --- | --- | --- | --- |
| authGuard | canMatch | Session ouverte | `/login?returnUrl=...` |
| guestGuard | canMatch | Aucune session | Tableau de bord du rôle |
| passwordChangeGuard | canMatch | `mustChangePassword` faux | `/account/password` |
| roleGuard(rôle) | canMatch | Rôle attendu | Tableau de bord de son propre rôle (US-10) |
| unsavedChangesGuard | canDeactivate | Formulaire non modifié, ou confirmation | Reste sur la page |

**Pourquoi `canMatch` plutôt que `canActivate`** : `canActivate` s'exécute après que la route a été retenue, donc après le téléchargement du code de l'espace. `canMatch` empêche la route d'être retenue, et le code n'est jamais chargé.

**Options du routeur** : liaison des paramètres de route et de requête aux `input()` des pages (`withComponentInputBinding`), titre d'onglet par route, restauration du défilement.

## 4. Session côté client

L'access token ne vit qu'en mémoire, dans un signal ; au rechargement de la page, la session est restaurée silencieusement grâce au cookie du refresh token.

### Cycle de vie

1. **Démarrage de l'application** : un initialiseur appelle `POST /auth/refresh`. Si le cookie est valide, la réponse contient un access token et l'utilisateur : la session est restaurée sans nouvelle connexion. Sinon, l'utilisateur est anonyme.
2. **Connexion** : `AuthStore` enregistre le token et l'utilisateur, puis redirige vers `returnUrl` ou le tableau de bord du rôle.
3. **Chaque requête** : l'intercepteur ajoute `Authorization: Bearer ...` aux appels `/api/v1`, sauf `/auth/login`, `/auth/register`, `/auth/refresh` et `/auth/logout` (`/auth/password` exige le token).
4. **Expiration (15 min)** : l'API répond 401 `AUTH_TOKEN_EXPIRED` ; l'intercepteur de renouvellement obtient un nouveau token puis rejoue la requête. Si plusieurs requêtes expirent en même temps, elles attendent toutes le même renouvellement.
5. **Renouvellement impossible** : session effacée, retour à la connexion avec un message « Votre session a expiré ».
6. **Déconnexion** : `POST /auth/logout`, effacement du store (même si le serveur est injoignable), retour à la connexion (US-09).

### Le piège des onglets multiples

Deux onglets ouverts partagent le même cookie. S'ils renouvellent en même temps, le second présente un refresh token que le premier vient de faire tourner : le backend y voit une réutilisation suspecte et révoque toutes les sessions (US-08). Parade : le renouvellement passe par l'API Web Locks du navigateur (`navigator.locks.request('refresh', ...)`), qui garantit qu'un seul onglet renouvelle à la fois ; l'autre attend puis relit le nouveau cookie. Décision de la phase 3 : pas de tolérance côté backend. La détection de réutilisation reste stricte ; le verrou Web Locks côté client et le verrou de ligne (`SELECT … FOR UPDATE`) côté serveur suffisent pour les onglets d'un même navigateur.

### Intercepteurs, dans l'ordre

| # | Intercepteur | Sur la requête | Sur la réponse |
| --- | --- | --- | --- |
| 1 | correlation | Ajoute `X-Correlation-Id` | — |
| 2 | error | — | Transforme l'erreur finale en `ApiError` typée (Problem Details) ; notification globale pour réseau coupé, 5xx, 429 et 403 ; laisse 400, 404, 409 et 422 à l'écran concerné |
| 3 | refresh | — | Sur 401 `AUTH_TOKEN_EXPIRED` : renouvelle une fois, puis rejoue la requête |
| 4 | auth | Ajoute le Bearer token courant | — |

Les intercepteurs sont des fonctions (`HttpInterceptorFn`) enregistrées avec `withInterceptors([...])`. Chacun enveloppe ceux qui le suivent dans la liste : la requête les traverse de haut en bas, la réponse remonte de bas en haut. D'où cet ordre :

- `refresh` est placé avant `auth` : quand il rejoue la requête, celle-ci repasse par `auth`, qui y met le nouveau token.
- `error` est placé avant `refresh` : il ne voit que l'erreur finale, après la tentative de renouvellement, et n'affiche jamais de faux message pour un token simplement expiré.

C'est une erreur fréquente que de raisonner dans l'autre sens ; un test unitaire avec `HttpTestingController` vérifie ce scénario (401, renouvellement, rejeu réussi, aucune notification).

## 5. État et accès aux données

L'état est rangé à quatre niveaux selon sa durée de vie ; le serveur reste la source de vérité, et aucune donnée métier n'est dupliquée dans un état global.

| Niveau | Où | Durée de vie | Exemples |
| --- | --- | --- | --- |
| Session | `AuthStore` dans `core`, fourni à la racine | Toute la visite | Utilisateur, token, rôle |
| Feature | Store fourni au niveau de la route | Tant que l'utilisateur reste dans l'écran | Dossier de candidature ouvert, résultat IA |
| URL | Paramètres de requête | Partageable, survit au rechargement | Filtres, page, tri |
| Composant | `signal()` local | Le temps du composant | Onglet actif, dialogue ouvert |

### Le schéma de lecture et d'écriture

1. La page reçoit filtres et page depuis l'URL, sous forme de `input()`.
2. Le store expose une ressource `httpResource` dont la requête dépend de ces signals : quand un filtre change, la liste se recharge seule, et la ressource fournit `value()`, `isLoading()` et `error()`.
3. Une modification (changer un statut, ajouter une note) passe par le service API (`HttpClient`), puis le store appelle `reload()` sur la ressource concernée et affiche une notification.
4. Pas de mise à jour optimiste : avec le verrouillage par version, c'est la réponse du serveur qui fait foi.

### Cas particuliers

| Besoin | Solution |
| --- | --- |
| Référentiels (catégories) | Chargés une fois par session dans un `ReferenceDataStore`, réutilisés par tous les formulaires |
| Autocomplétion des compétences | Saisie temporisée de 300 ms, au moins 2 caractères, annulation de la requête précédente (`switchMap`). Le composant vit dans `shared/ui` et reçoit l'accès au référentiel par le jeton `SKILL_SEARCH`, que `core` fournit dans `app.config.ts` : `shared` reste sans appel HTTP |
| Suivi d'une tâche IA | Interrogation toutes les 3 s jusqu'à DONE ou FAILED, arrêt automatique à la destruction du composant ou après 3 min ; puis rechargement du résultat |
| Annonce du résultat IA | Message pour les lecteurs d'écran via `LiveAnnouncer` quand l'analyse se termine |

**Pourquoi pas NgRx** : NgRx apporte un journal des actions et un état global prévisible, utiles quand de nombreux écrans partagent et modifient les mêmes données. Ici, chaque écran charge ses propres données et l'état partagé se limite à la session. Des stores à signals donnent la même clarté pour une fraction du code.

## 6. Formulaires et validation

Tous les formulaires sont des Reactive Forms typés, qui reprennent les règles de l'API pour un retour immédiat ; le backend revalide tout, et ses erreurs reviennent s'afficher sous le bon champ.

### Règles communes

- `FormBuilder.nonNullable` : chaque contrôle a un type précis, sans `null` inattendu.
- Validateurs partagés dans `shared/forms` : URL en https, téléphone international, plage de dates, taille maximale d'une liste.
- Erreurs serveur : la fonction `applyServerErrors(form, apiError)` place chaque entrée de `errors[]` sous son champ ; les erreurs métier (409, 422) s'affichent dans un bandeau au-dessus du formulaire.
- Envoi : bouton désactivé pendant la requête, pour éviter le double envoi.
- Sortie de page avec des modifications non enregistrées : confirmation (`unsavedChangesGuard`).
- Validation conditionnelle par validateur de groupe (fonction pure, testable), plutôt que par ajout et retrait de validateurs à la volée.

### Inventaire des formulaires

| Formulaire | Particularité | US |
| --- | --- | --- |
| Inscription, connexion | Mot de passe de 12 caractères minimum, case de confidentialité obligatoire | US-06, US-07 |
| Changement de mot de passe | Confirmation identique au nouveau mot de passe | US-11 |
| Profil, informations de base | Envoie `version` | US-13 |
| Expérience, formation | Dans un dialogue ; date de fin facultative si « en cours » | US-14 |
| Compétences | `FormArray` avec autocomplétion ; 50 au maximum, sans doublon | US-15 |
| Langues, liens | `FormArray` ; 5 liens au maximum | US-16 |
| Téléversement du CV | Contrôle PDF et 5 Mo avant l'envoi, barre de progression indéterminée (le client HTTP `withFetch()` ne remonte pas la progression d'envoi) | US-17 |
| Offre d'emploi | Deux `FormArray` (requises, souhaitées) ; une compétence ne peut pas être dans les deux | US-21 |
| Publication d'une offre | Date d'expiration entre demain et 180 jours | US-23 |
| Changement de statut | Choix limité aux `allowedTransitions` ; motif obligatoire si REJECTED | US-32, US-33 |
| Entretien | Lien obligatoire si VIDEO, adresse si ON\_SITE ; date et heure locales converties en UTC | US-35 |
| Clôture d'entretien | Avis obligatoire | US-37 |
| Note | 1 à 5 000 caractères, compteur visible | US-39 |
| Utilisateur (admin) | Entreprise obligatoire si RECRUITER, interdite si ADMIN | US-47 |
| Entreprise, catégorie, fusion de compétences | Fusion : la cible doit différer de la source | US-49 à US-51 |

## 7. Interface

Angular Material 3 fournit les composants et le thème ; une dizaine de composants partagés évitent de répéter les mêmes motifs dans les 26 écrans.

### Mise en page

- `PublicLayout` : en-tête avec recherche d'offres, connexion et inscription.
- `AppLayout` : barre latérale dont les entrées dépendent du rôle, menu utilisateur, zone de contenu. La barre est fixe au-dessus de 960 px de large et devient un tiroir en dessous.
- Thème Material 3 défini par des variables (couleur primaire, typographie, densité), avec un mode sombre facultatif.

### Composants partagés

| Composant | Rôle |
| --- | --- |
| page-header | Titre, fil d'Ariane, actions principales de la page |
| empty-state | Illustration, message et action pour une liste vide |
| confirm-dialog | Confirmation des actions irréversibles (rejet, retrait, archivage, désactivation) |
| status-chip | Statut avec texte et couleur, jamais la couleur seule |
| file-upload | Glisser-déposer, contrôle de type et de taille, progression |
| skill-autocomplete | Recherche dans le référentiel et création d'une compétence |
| data-table-paginator | Tableau Material relié à la pagination de l'API et à l'URL |
| form-field-error | Message d'erreur unique par champ, y compris les erreurs serveur |
| loading-skeleton | Squelettes de chargement pour listes et fiches |

### Accessibilité (WCAG 2.1 AA visé sur le parcours candidat)

- Chaque champ a un libellé ; les erreurs sont reliées au champ pour les lecteurs d'écran.
- Navigation complète au clavier, focus visible, focus déplacé dans les dialogues puis rendu à la fermeture (CDK a11y).
- Les statuts ne reposent jamais sur la couleur seule.
- Les résultats asynchrones (analyse IA, enregistrement) sont annoncés par `LiveAnnouncer`.
- Contrastes vérifiés sur le thème, et contrôle automatique avec axe dans les tests de bout en bout.

### Responsive

Tout écran fonctionne à partir de 360 px. Les tableaux denses (candidatures, utilisateurs) basculent en cartes en dessous de 600 px ; les formulaires passent sur une colonne.

## 8. Erreurs, chargement, listes

Chaque écran de données gère explicitement quatre états — chargement, erreur, vide, contenu — à partir de la ressource qui l'alimente.

| État | Affichage | Source |
| --- | --- | --- |
| Chargement initial | Squelette à la forme du contenu attendu | `isLoading()` sans valeur |
| Rechargement | Contenu conservé, indicateur discret | `isLoading()` avec valeur |
| Vide | `empty-state` avec action utile (« Publier une première offre ») | Page avec 0 élément |
| Erreur de chargement | Message et bouton « Réessayer » | `error()` |
| Ressource introuvable | Page « introuvable » | 404 sur une page de détail |
| Erreur d'action | Bandeau dans le formulaire ou le dialogue | 409, 422 |
| Erreur générale | Notification en bas d'écran avec l'identifiant de corrélation | Réseau, 5xx, 429 |

Une erreur JavaScript imprévue est captée par un `ErrorHandler` global, qui affiche une notification générique et la journalise dans la console en développement.

### Pagination et filtres

- Filtres, page, taille et tri vivent dans les paramètres de l'URL : un lien copié ou le bouton Précédent restaurent exactement la même liste.
- La recherche texte attend 300 ms après la dernière frappe ; les autres filtres s'appliquent immédiatement.
- Changer un filtre ramène à la première page.
- La pagination Material affiche le total renvoyé par l'API ; 20 éléments par page par défaut.

### Chargement différé dans la page

L'onglet IA du dossier de candidature est enveloppé dans un bloc `@defer (on viewport)` : son code et ses données ne sont chargés que lorsque le recruteur l'ouvre.

## 9. Tests, qualité et build

Les tests visent d'abord ce qui casserait silencieusement (intercepteurs, guards, stores, validateurs), puis trois parcours de bout en bout ; la qualité est vérifiée automatiquement en CI.

| Niveau | Outil | Cible | Exemples |
| --- | --- | --- | --- |
| Unitaire | Vitest | `core`, `shared/forms`, stores, pipes | Intercepteur de renouvellement (401 → refresh → rejeu), `roleGuard`, `applyServerErrors`, validateur de groupe de l'entretien |
| Composant | Vitest + TestBed | Composants de présentation et dialogues | `status-change-dialog` exige un motif pour REJECTED |
| Bout en bout | Playwright, sur l'application lancée par Docker Compose | 3 parcours critiques (US-57) | Inscription puis candidature ; recruteur qui change un statut ; accès refusé à un autre espace |

Objectif de couverture : 70 % des lignes sur `core`, `shared` et les dossiers `data-access`, sans objectif sur les templates.

### Qualité

- TypeScript en mode strict et templates en `strictTemplates`.
- ESLint (angular-eslint) avec la règle de frontières d'import entre `core`, `shared` et `features` ; Prettier pour le formatage.
- Budgets de taille de bundle dans `angular.json` : avertissement au-delà de 600 kB pour le bundle initial, échec au-delà de 1 Mo. Le seuil d'avertissement est passé de 500 à 600 kB en phase 4 : esbuild place dans un morceau commun, chargé au démarrage, tout module Material utilisé à la fois au démarrage (barre d'outils, notifications) et par un écran différé ; le transfert compressé reste de 138 kB.

### Build et déploiement

- `ng build` (constructeur esbuild) produit des fichiers statiques, copiés dans l'image Nginx par un Dockerfile multi-étapes.
- Aucune URL d'API propre à un environnement : grâce à la même origine, le frontend appelle toujours `/api/v1` en relatif. La même image sert en démo comme en production.
- En développement, `proxy.conf.json` redirige `/api` vers `localhost:8080`, ce qui reproduit la même origine sans CORS.
- Nginx renvoie `index.html` pour toute route inconnue (routage côté client), sauf `/api`.

## 10. Ce que tu dois savoir défendre

**« Qu'est-ce qu'un signal, et pourquoi en utiliser ? »** Une valeur réactive : quand elle change, Angular sait exactement quels `computed` et quelles parties de template en dépendent et ne met à jour qu'elles. Plus besoin de Zone.js pour deviner quand vérifier toute l'application, ni de `subscribe`/`unsubscribe` pour de l'état simple.

**« Que veut dire OnPush ? »** Un composant n'est revérifié que si l'un de ses `input()` change, si un signal lu dans son template change, ou si un événement s'y produit. Avec les signals, c'est le comportement naturel ; Angular 22 en a fait le défaut.

**« Où stockes-tu le JWT, et pourquoi pas dans le localStorage ? »** L'access token est en mémoire, le refresh token dans un cookie HttpOnly. Le localStorage est lisible par n'importe quel script de la page : une seule faille XSS suffirait à voler la session. En mémoire, le token disparaît au rechargement, d'où la restauration silencieuse par le cookie au démarrage.

**« Tes guards suffisent-ils à sécuriser l'application ? »** Non : tout code frontend peut être modifié dans le navigateur. Les guards évitent d'afficher des écrans inutiles et de télécharger du code ; la sécurité réelle est dans l'API (rôle et propriété à chaque requête).

**« Pourquoi un `canMatch` ? »** Il empêche la route d'être retenue, donc le code de l'espace d'être téléchargé. Un candidat ne charge jamais le code de l'administration.

**« Composant intelligent et composant de présentation ? »** La page connaît les services, l'URL et les données ; le composant de présentation reçoit des entrées et émet des événements. Ce dernier se teste et se réutilise facilement, car il ne dépend de rien d'autre que de ses entrées.

**« Reactive Forms ou Signal Forms ? »** Les Signal Forms sont stables depuis Angular 22 et plus cohérents avec les signals. J'ai choisi les Reactive Forms typés parce qu'ils dominent les projets existants ; je sais expliquer la différence et migrer un formulaire si on me le demande.
