# SmartRecruit — Architecture Spring Boot

Oct 19, 2026 · @Hajar

## 0. Décisions techniques

Un seul module Maven, des paquets par module métier vérifiés par Spring Modulith, et les briques standard de Spring Boot 4.1 partout où elles suffisent : aucune bibliothèque n'entre sans un besoin du cahier des charges.

| Sujet | Décision | Alternative écartée | Raison |
| --- | --- | --- | --- |
| Projet | Un module Maven `backend` | Multi-modules Maven par domaine | Les frontières sont vérifiées par test (Spring Modulith) ; plusieurs modules Maven compliqueraient le build sans protection supplémentaire |
| Paquets | Par module métier, puis par couche | Par couche globale (`controller/`, `service/`…) | Tout ce qui concerne les candidatures est au même endroit ; c'est la condition d'un monolithe découpable |
| JWT | Encodeur et décodeur Nimbus de Spring Security (ressource OAuth2), clé HMAC | Bibliothèque JWT tierce, filtre écrit à la main | Validation de signature, d'expiration et extraction des droits déjà écrites et testées par Spring |
| Identifiants | `@UuidGenerator(style = VERSION_7)` de Hibernate 7 ([doc](https://docs.hibernate.org/orm/7.0/javadocs/org/hibernate/annotations/UuidGenerator.Style.html)) | Génération par la base (`uuidv7()`) | L'identifiant existe dès la création de l'objet : `equals`/`hashCode` stables, événements publiés avec un id |
| Requêtes filtrées | Spring Data JPA + Specifications | Querydsl, jOOQ | Suffisant pour des filtres combinés, sans génération de code supplémentaire |
| Recherche d'offres | Requête SQL native sur la colonne `tsvector` | Moteur de recherche | Au volume visé, PostgreSQL suffit (architecture §9) |
| DTO | `record` Java + MapStruct | Lombok + mapping manuel | Immuables, concis ; mapping vérifié à la compilation |
| Lombok | Non utilisé | Lombok sur les entités | Les records couvrent les DTO ; les entités ont peu de getters et aucun setter public : rien à générer |
| Threads | Threads virtuels pour les requêtes web ; pool de 2 threads classiques pour l'IA | Pool classique partout | Les requêtes attendent surtout la base : idéal pour les threads virtuels ; l'IA est limitée par le CPU d'Ollama, donc un petit pool borné |
| Erreurs | `ProblemDetail` natif de Spring + `@RestControllerAdvice` | Format d'erreur maison | Standard RFC 9457 (API §0) |
| Logs | Logs structurés natifs de Spring Boot | Configuration Logback écrite à la main | Une propriété suffit pour du JSON en production |
| Renouvellement multi-onglets | Pas de tolérance côté serveur en V1 ; coordination par l'API Web Locks côté Angular | Fenêtre de tolérance de quelques secondes | Garde la détection de vol stricte ; décision laissée ouverte par l'architecture Angular, tranchée ici |

**Point de vigilance** : springdoc-openapi suit les versions de Spring Boot, et sa série 3.x a démarré avec Spring Boot 4.0 ([exemple de changelog](https://dependabot.ecosyste.ms/hosts/GitHub/repositories/amilacjay%2FSuperChain/issues/11)). À l'initialisation du projet, il faudra vérifier qu'une version compatible avec Spring Boot 4.1 est publiée ; sinon, démarrer sur la dernière 4.0.x, encore supportée jusqu'à fin 2026.

*(Phase 12)* springdoc n'avait finalement pas été ajouté pendant les phases 2 à 11 ; l'écart a été constaté et corrigé en US-58 avec la série 3.1 (3.1.1, construite sur Spring Boot 4.1.0). Documentation servie sous `/api/docs` en `dev` et `demo` seulement ([US-58](stories/US-58.md)).

## 1. Projet Maven et dépendances

Quinze dépendances au total, chacune reliée à une exigence ; avec Spring Boot 4, chaque technologie a son propre starter, ce qui rend la liste explicite.

| Dépendance (par fonction) | Utilisée pour | Exigence |
| --- | --- | --- |
| Starter Web MVC | Contrôleurs REST, Jackson 3 | API §0 |
| Starter Validation | Bean Validation sur les DTO | ENF-SEC-05 |
| Starter Data JPA | Entités, dépôts, Hibernate 7 | Modèle de données |
| Driver PostgreSQL | Connexion à PostgreSQL 18 | — |
| Starter Flyway + module PostgreSQL de Flyway | Migrations versionnées | US-02 |
| Starter Security + starter OAuth2 Resource Server | Chaîne de filtres, JWT (Nimbus) | US-07 à US-10 |
| Starter Actuator | Health checks, métriques | Architecture §8 |
| Spring Modulith (core) | Vérification des frontières de modules, documentation | Architecture §3 |
| MapStruct + son processeur d'annotations | Mapping entité → DTO | Prompt §15 |
| springdoc-openapi (Web MVC UI) | OpenAPI et Swagger UI | Prompt §20 |
| Apache PDFBox | Extraction du texte des CV | F-AI-01 |
| Starter Test (JUnit Jupiter, AssertJ, Mockito) | Tests unitaires et de tranche | Prompt §19 |
| Spring Security Test | Tests avec utilisateur simulé | US-10 |
| Testcontainers (PostgreSQL) + support Testcontainers de Spring Boot | Tests d'intégration sur un vrai PostgreSQL | US-02 |
| Spring Modulith Test | Test `verify()` des modules | Architecture §3 |

Absents volontairement : Lombok, une bibliothèque JWT tierce, Querydsl, un client HTTP tiers (le `RestClient` de Spring suffit pour Ollama), Bucket4j (deux limitations de débit simples, écrites en une classe).

### Plugins

| Plugin | Rôle |
| --- | --- |
| Spring Boot Maven | JAR exécutable, image de build |
| Compiler (Java 25) | Processeur MapStruct, option `-parameters` |
| Surefire / Failsafe | Tests unitaires (`*Test`) puis d'intégration (`*IT`) dans deux phases distinctes |
| JaCoCo | Rapport de couverture ; échec sous 70 % sur les paquets `application` et `domain` |
| Spotless | Formatage automatique vérifié en CI |
| Enforcer | Versions de Java et Maven minimales, pas de dépendance en double |

## 2. Arborescence des paquets

Un paquet racine par module métier, directement sous `com.smartrecruit` : c'est la convention que Spring Modulith reconnaît pour détecter les modules. Le paquet `api` de chaque module est le seul visible des autres.

```
backend/src/main/java/com/smartrecruit/
├── SmartRecruitApplication.java
├── shared/          # noyau partagé : CurrentUser, DomainException, Location, config transverse
│   ├── security/    # SecurityConfig, JwtService, CurrentUserResolver
│   ├── web/         # GlobalExceptionHandler, CorrelationIdFilter, PageResponse
│   └── config/      # ClockConfig, AsyncConfig, propriétés typées
├── identity/
├── audit/
├── company/
├── referential/
├── candidate/
├── job/
├── application/
├── interview/
├── ai/
└── reporting/

backend/src/main/resources/
├── application.yml, application-dev.yml, application-test.yml, application-prod.yml
├── db/migration/    # V1__identity.sql, V2__company.sql, ... V9__seed_reference_data.sql
└── prompts/         # resume-extraction-v1.txt, match-explain-v1.txt, schémas JSON
```

### Exemple détaillé : le module `application`

```
application/
├── api/                                   # visible des autres modules
│   ├── ApplicationQueries.java            # interface : findStatus(id), findSummary(id)
│   ├── CompletedInterviewChecker.java     # interface implémentée par le module interview
│   └── events/
│       ├── ApplicationSubmitted.java      # record
│       └── ApplicationStatusChanged.java  # record
├── web/
│   ├── RecruiterApplicationController.java    # /applications/**
│   ├── CandidateApplicationController.java    # /jobs/{id}/applications, /candidates/me/applications/**
│   ├── dto/                               # ChangeStatusRequest, RecruiterApplicationResponse...
│   └── ApplicationMapper.java             # MapStruct
├── service/
│   ├── ApplicationService.java            # postuler, changer de statut, retirer
│   ├── ApplicationQueryService.java       # listes et dossier (lecture seule)
│   ├── ApplicationEligibilityChecker.java
│   └── ApplicationHistoryListener.java    # écoute les événements, écrit l'historique
├── domain/
│   ├── Application.java, ApplicationStatus.java, TransitionContext.java
│   ├── ApplicationHistoryEntry.java, Note.java, RejectionReason.java
│   └── ...
└── infrastructure/
    ├── ApplicationRepository.java         # Spring Data JPA
    ├── ApplicationSpecifications.java     # filtres de la liste recruteur
    └── ApplicationHistoryRepository.java
```

Ce découpage interne reprend les quatre couches de l'architecture globale (§4), en remplaçant les paquets transverses du prompt (`controller/`, `service/`, `repository/`…) par des paquets propres à chaque module.

## 3. Règles par couche

Chaque couche a une responsabilité unique ; ces règles sont celles qu'un relecteur de code vérifierait dans une pull request.

| Couche | Fait | Ne fait jamais |
| --- | --- | --- |
| Contrôleur (`web`) | Déclare la route, valide le DTO (`@Valid`), récupère le `CurrentUser`, appelle un seul service, convertit le résultat en DTO, fixe le statut HTTP | Logique métier, accès à un dépôt, `try/catch` (le gestionnaire global s'en charge) |
| DTO | `record` immuable avec annotations de validation ; un DTO de requête et un de réponse par cas d'usage | Être une entité ; être partagé entre la vue candidat et la vue recruteur |
| Mapper | Interface MapStruct, entité → DTO de réponse ; champs calculés via méthodes par défaut | Charger des données, appeler un service |
| Service (`service`) | Ouvre la transaction, vérifie la propriété, charge l'agrégat, appelle ses méthodes, sauvegarde | Règle métier propre à un agrégat (elle est dans le domaine) ; renvoyer une entité au contrôleur hors lecture interne |
| Domaine (`domain`) | Entités, invariants, transitions, événements ; lève des `DomainException` | Dépendre de Spring MVC, d'un service, d'un dépôt ou de l'heure système |
| Dépôt (`infrastructure`) | Interface Spring Data, requêtes nommées, Specifications, projections | Logique métier ; requêtes sur les tables d'un autre module |
| API de module (`api`) | Interfaces de lecture et événements publiés pour les autres modules | Exposer une entité JPA |

### Conventions de nommage

- Contrôleurs : `<Public><Ressource>Controller` quand deux publics existent (`RecruiterApplicationController`, `CandidateApplicationController`).
- DTO : `<Action><Ressource>Request`, `<Ressource><Vue>Response` (`ChangeStatusRequest`, `RecruiterApplicationResponse`).
- Services d'écriture et de lecture séparés quand la lecture devient volumineuse (`ApplicationService`, `ApplicationQueryService`).
- Tests : `<Classe>Test` pour les tests unitaires, `<Classe>IT` pour les tests d'intégration.

### Visibilité entre modules

Par défaut, Spring Modulith considère comme public le seul paquet racine d'un module. Le sous-paquet `api` est déclaré interface nommée (`@NamedInterface("api")` dans son `package-info.java`) ; tout le reste est interne, et le test `verify()` échoue si un autre module y accède.

## 4. Sécurité

Spring Security valide le JWT et applique les règles de rôle de l'API (§11) ; les services appliquent la propriété avec le `CurrentUser`. Aucun filtre JWT n'est écrit à la main.

### Chaîne de filtres

| Ordre | Élément | Rôle |
| --- | --- | --- |
| 1 | `CorrelationIdFilter` | Lit ou génère l'identifiant de corrélation, le place dans le contexte des logs |
| 2 | Filtre CORS | Actif uniquement en profil `dev` |
| 3 | `BearerTokenAuthenticationFilter` (ressource OAuth2) | Extrait et valide le JWT avec le `JwtDecoder` |
| 4 | `PasswordChangeRequiredFilter` | Renvoie 403 `PASSWORD_CHANGE_REQUIRED` hors des chemins autorisés |
| 5 | `AuthorizationFilter` | Règles d'URL par rôle, refus par défaut |

Configuration : sans état (`SessionCreationPolicy.STATELESS`), protection CSRF désactivée pour les endpoints à Bearer (API §2 explique pourquoi les endpoints à cookie restent protégés), réponses 401 et 403 au format Problem Details via un point d'entrée et un gestionnaire d'accès refusé personnalisés.

### Jetons

| Élément | Implémentation |
| --- | --- |
| Émission | `JwtService` utilise le `JwtEncoder` Nimbus avec une clé HMAC SHA-256 de 256 bits minimum, lue dans `JWT_SECRET` |
| Contenu | `sub` (id), `role`, `companyId` (recruteur), `iat`, `exp` (+15 min), `iss`, `sv` (version de session, absente si 0 ; phase 10, US-48) |
| Validation | `JwtDecoder` Nimbus : signature, expiration, émetteur ; le rôle devient l'autorité `ROLE_<role>` |
| Expiration | Point d'entrée qui distingue un token expiré (`AUTH_TOKEN_EXPIRED`) d'un token invalide, pour que le frontend sache s'il doit renouveler |
| Refresh token | 32 octets aléatoires (`SecureRandom`), encodés en Base64 URL ; seul le SHA-256 est stocké |

### Utilisateur courant

Un `HandlerMethodArgumentResolver` construit le record `CurrentUser(id, role, companyId)` à partir du JWT et l'injecte dans les méthodes de contrôleur. Les services le reçoivent en paramètre : ils ne lisent jamais le `SecurityContextHolder` eux-mêmes, ce qui les rend testables sans contexte de sécurité.

### Mots de passe

`DelegatingPasswordEncoder` de Spring Security, BCrypt par défaut : le hash stocké commence par `{bcrypt}`, ce qui permettra de changer d'algorithme plus tard sans invalider les anciens mots de passe. La liste des mots de passe courants (quelques milliers d'entrées) est un fichier de ressources chargé au démarrage.

### Limitation de débit

Une classe `RateLimiter` en mémoire, à fenêtre glissante, sert les deux besoins : 5 échecs de connexion par email en 15 minutes, et 10 demandes IA par recruteur et par minute. Suffisant pour une instance ; à remplacer par un stockage partagé avec plusieurs instances (architecture §9).

## 5. Persistance et transactions

La transaction s'ouvre dans le service et se ferme avant la réponse HTTP ; aucun appel lent (IA, fichiers volumineux) ne se fait à l'intérieur d'une transaction.

### Réglages JPA

| Propriété | Valeur | Raison |
| --- | --- | --- |
| `spring.jpa.open-in-view` | `false` | Sinon une session Hibernate reste ouverte pendant tout le rendu de la réponse, et des chargements paresseux cachés déclenchent des requêtes depuis le contrôleur |
| `spring.jpa.hibernate.ddl-auto` | `validate` (tous profils) | Seul Flyway modifie le schéma ; Hibernate vérifie qu'il correspond aux entités |
| `hibernate.jdbc.time_zone` | `UTC` | RG-32 |
| `hibernate.default_batch_fetch_size` | 20 | Charge les collections paresseuses par lots au lieu d'une requête par élément |
| `hibernate.jdbc.batch_size` + `order_inserts` | 50, vrai | Insertions groupées (données de démonstration, fusion de compétences) |

### Entités

- Constructeur sans argument `protected` (exigé par JPA), fabriques statiques publiques.
- `@Id @UuidGenerator(style = VERSION_7)`, `@Version` sur les agrégats modifiables.
- `@Enumerated(EnumType.STRING)` partout ; objets valeur en `@Embeddable`.
- Colonnes JSONB mappées sur des records avec `@JdbcTypeCode(SqlTypes.JSON)`.
- Associations internes à un agrégat en `LAZY`, avec `cascade = ALL` et `orphanRemoval = true` depuis la racine.

### Requêtes

| Besoin | Technique |
| --- | --- |
| Lecture d'un agrégat | Méthode de dépôt Spring Data |
| Liste filtrée (candidatures, utilisateurs) | Specifications combinées selon les filtres présents. *Phase 6* : la liste des candidatures filtre sur des données d'autres modules (nom, compétences) que l'entité ne référence que par identifiant ; elle est donc une requête SQL de lecture (`RecruiterApplicationSearch`), exception documentée dans US-30 |
| Liste affichée (une ligne de tableau) | Projection JPQL vers un record (`select new ...`), sans charger les entités |
| Recherche d'offres | Requête native sur `search_vector` avec `plainto_tsquery('french', unaccent(:q))` |
| Tableaux de bord | Requêtes d'agrégation natives dans le module `reporting` |

### Transactions

- `@Transactional` sur les méthodes publiques des services d'écriture, `@Transactional(readOnly = true)` sur les services de lecture. Jamais sur un contrôleur ni sur une entité.
- Piège classique : une méthode `@Transactional` appelée depuis une autre méthode de la même classe ne déclenche pas de transaction, car l'appel ne passe pas par le proxy Spring. Les découpages se font donc entre classes distinctes.
- La tâche IA est traitée en trois temps : transaction courte pour passer RUNNING, appel à Ollama hors transaction, transaction courte pour enregistrer le résultat. Une transaction ouverte pendant deux minutes bloquerait une connexion du pool.
- `ObjectOptimisticLockingFailureException` est traduite en 409 `CONCURRENT_MODIFICATION`.

### Migrations

Un script par module à l'initialisation (`V1__identity.sql` à `V8__ai_audit.sql`), puis un script par évolution, jamais modifié une fois fusionné. `V9__seed_reference_data.sql` crée les catégories et quelques compétences de départ.

L'admin initial n'est pas créé par une migration, qui ne peut pas lire de secret proprement : un composant `InitialAdminBootstrap`, exécuté au démarrage, le crée à partir de `INITIAL_ADMIN_EMAIL` et `INITIAL_ADMIN_PASSWORD` s'il n'existe encore aucun admin, avec `mustChangePassword = true`. Les données de démonstration ne sont pas non plus une migration : un composant les charge uniquement en profil `demo`.

## 6. Erreurs et validation

Un seul `@RestControllerAdvice` traduit toute exception en `ProblemDetail` avec le code du catalogue ; les contrôleurs ne contiennent aucun `try/catch`.

| Exception | Statut | Code |
| --- | --- | --- |
| `MethodArgumentNotValidException`, `HandlerMethodValidationException` | 400 | VALIDATION\_FAILED, avec `errors[]` par champ |
| `HttpMessageNotReadableException` (JSON mal formé, énumération inconnue) | 400 | VALIDATION\_FAILED |
| `AuthenticationException` (via le point d'entrée de sécurité) | 401 | AUTH\_INVALID\_CREDENTIALS, AUTH\_TOKEN\_EXPIRED ou AUTH\_REFRESH\_INVALID |
| `AccessDeniedException` | 403 | ACCESS\_DENIED |
| `ResourceNotFoundException` | 404 | RESOURCE\_NOT\_FOUND |
| `InvalidStateException` | 409 | Code porté par l'exception (INVALID\_STATUS\_TRANSITION, INVALID\_STATE, JOB\_NOT\_OPEN…) |
| `ObjectOptimisticLockingFailureException` | 409 | CONCURRENT\_MODIFICATION |
| `DataIntegrityViolationException` sur une contrainte connue | 409 | Code associé au nom de la contrainte (ex. `uk_application_job_candidate` → APPLICATION\_ALREADY\_EXISTS) |
| `BusinessRuleViolationException` | 422 | Code porté par l'exception (PROFILE\_INCOMPLETE, REJECTION\_REASON\_REQUIRED…) + détails éventuels |
| `MaxUploadSizeExceededException` | 413 | FILE\_TOO\_LARGE |
| `AiUnavailableException` | 503 | AI\_DISABLED ou AI\_UNAVAILABLE |
| Toute autre exception | 500 | INTERNAL\_ERROR, détail uniquement dans les logs |

Le domaine ne connaît pas les statuts HTTP : chaque exception porte un code métier, et c'est ce gestionnaire qui choisit le statut. Les contraintes de base ont des noms explicites dans les migrations, pour que la traduction d'une violation en code métier soit fiable.

### Validation à trois niveaux

| Niveau | Outil | Exemple |
| --- | --- | --- |
| Forme de la requête | Annotations Bean Validation sur les records (`@NotBlank`, `@Size`, `@Email`, `@Pattern`) et validateurs personnalisés (`@HttpsUrl`, `@E164Phone`) | Mot de passe de 12 à 128 caractères |
| Règle métier | Méthodes du domaine, `DomainException` | Transition interdite, rejet sans motif |
| Garantie finale | Contraintes PostgreSQL (UNIQUE, CHECK, FK) | Une seule candidature par offre |

La limite de 5 Mo des CV est aussi fixée dans la configuration multipart de Spring et dans Nginx : une requête trop grosse est refusée avant même d'être lue en entier.

## 7. Événements, asynchrone et IA

Les événements de domaine passent par le mécanisme standard de Spring ; seul le traitement IA sort du thread de la requête, dans un pool dédié et borné.

### Événements

| Mécanisme | Usage |
| --- | --- |
| `AbstractAggregateRoot.registerEvent()` dans les agrégats | Publication automatique à la sauvegarde par Spring Data |
| `@EventListener` | Réactions dans la même transaction : historique, audit, annulation d'entretiens, cascade de désactivation d'entreprise |
| `@TransactionalEventListener(phase = AFTER_COMMIT)` | Soumission de la tâche IA à l'exécuteur, une fois la tâche visible en base |

Les événements sont des `record` dans le paquet `api` du module émetteur ; ils ne transportent que des identifiants et des valeurs, jamais d'entité.

### Exécution asynchrone

| Élément | Réglage |
| --- | --- |
| Requêtes HTTP | `spring.threads.virtual.enabled=true` : chaque requête sur un thread virtuel |
| Exécuteur IA | `ThreadPoolTaskExecutor` nommé `aiExecutor` : 2 threads, file de 50 tâches ; au-delà, la tâche passe FAILED avec `AI_UNAVAILABLE` |
| Reprise au démarrage | Écouteur `ApplicationReadyEvent` : passe en FAILED les tâches restées PENDING ou RUNNING (analyse fonctionnelle 4.4) |
| Arrêt propre | L'exécuteur attend la fin des tâches en cours jusqu'à 30 s à l'arrêt |

### Adaptateur Ollama

| Élément | Implémentation |
| --- | --- |
| Client | `RestClient` dédié, URL de base `AI_OLLAMA_URL`, délai de connexion 5 s, délai de lecture 120 s |
| Appel | `POST /api/chat`, `stream: false`, `format` = schéma JSON chargé depuis `prompts/`, `options.temperature` = 0 |
| Réponse | Désérialisée en record (`ResumeExtraction`), puis validée par Bean Validation ; non conforme → une nouvelle tentative, puis `AI_INVALID_RESPONSE` |
| Prompts | Fichiers texte versionnés ; le nom de fichier donne la version enregistrée avec le résultat |
| Santé | `OllamaHealthIndicator` interroge la liste des modèles et vérifie que le modèle configuré est présent |
| Activation | `@ConditionalOnProperty("ai.enabled")` sur l'adaptateur ; sinon une implémentation `DisabledAIService` lève `AI_DISABLED` |
| Tests | `FakeAIService` en profil `test`, et un test d'intégration de l'adaptateur contre un faux serveur HTTP qui renvoie des réponses enregistrées |

Le module `ai` ne dépend que de l'interface `AIService` ; changer de fournisseur revient à écrire un autre adaptateur et à changer `ai.provider`.

## 8. Configuration et profils

Toute la configuration propre à l'application passe par des records `@ConfigurationProperties` validés au démarrage : une valeur manquante ou invalide empêche l'application de démarrer, au lieu de provoquer une erreur plus tard.

| Record | Préfixe | Propriétés principales |
| --- | --- | --- |
| `JwtProperties` | `security.jwt` | `secret` (≥ 32 octets), `issuer`, `accessTokenTtl` (15 min), `refreshTokenTtl` (7 j) |
| `CorsProperties` | `security.cors` | `allowedOrigins` (profil `dev`) |
| `StorageProperties` | `storage` | `rootDirectory`, `maxFileSize` (5 Mo) |
| `AiProperties` | `ai` | `enabled`, `provider`, `ollama.url`, `ollama.model`, `timeout`, `maxConcurrentTasks` |
| `BootstrapProperties` | `bootstrap.admin` | `email`, `password` |

### Variables d'environnement (`.env.example`)

| Variable | Exemple | Obligatoire en `prod` |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` | Oui |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:postgresql://postgres:5432/smartrecruit` | Oui |
| `JWT_SECRET` | 64 caractères aléatoires | Oui |
| `STORAGE_ROOT` | `/data/resumes` | Oui |
| `AI_ENABLED`, `AI_OLLAMA_URL`, `AI_OLLAMA_MODEL` | `true`, `http://ollama:11434`, nom épinglé après le banc d'essai | Si l'IA est active |
| `INITIAL_ADMIN_EMAIL`, `INITIAL_ADMIN_PASSWORD` | — | Au premier démarrage |

### Profils

`application.yml` contient les valeurs communes ; chaque profil ne surcharge que ce qui diffère (architecture §7). Le profil `prod` n'a aucune valeur par défaut pour les secrets : leur absence fait échouer le démarrage.

## 9. Observabilité

Les outils natifs de Spring Boot couvrent la V1 : logs structurés activés par une propriété, identifiant de corrélation dans chaque ligne, health checks et métriques Micrometer.

| Élément | Implémentation |
| --- | --- |
| Logs | Format texte en `dev` ; JSON en `prod` via `logging.structured.format.console` |
| Corrélation | `CorrelationIdFilter` place `correlationId` et, après authentification, `userId` dans le MDC ; tous les logs de la requête les portent |
| Masquage | Aucune journalisation de corps de requête ; email, mot de passe, token et contenu de CV ne sont jamais passés au logger |
| Health | Groupes `liveness` et `readiness` ; `readiness` inclut la base, pas Ollama ; `OllamaHealthIndicator` visible dans le détail réservé à l'admin |
| Métriques HTTP | Temps de réponse par endpoint et statut, fournis par Micrometer |
| Métriques métier | `smartrecruit.ai.task.duration` (timer, par type et statut), `smartrecruit.applications.submitted` (compteur), `smartrecruit.auth.login.failures` (compteur) |
| Exposition | `health` public ; `metrics` et `info` réservés à l'admin ; `info` affiche la version et le commit Git |

Niveaux de log : `INFO` pour les événements métier importants (candidature créée, tâche IA terminée), `WARN` pour une situation anormale récupérée (fichier introuvable, réponse IA non conforme), `ERROR` pour une erreur non prévue, toujours avec l'identifiant de corrélation.

## 10. Tests

La majorité des tests sont unitaires et rapides, parce que les règles vivent dans le domaine ; les tests d'intégration vérifient ce que seul un vrai environnement montre : sécurité, SQL, contraintes, transactions.

| Niveau | Outils | Ce qu'il vérifie | Exemples |
| --- | --- | --- | --- |
| Unitaire (domaine) | JUnit Jupiter, AssertJ | Règles métier pures, sans Spring | Les 64 couples de statuts de candidature (test paramétré) ; `MatchingCalculator` donne 81 sur l'exemple chiffré ; `JobOffer.publish` refuse plus de 180 jours |
| Unitaire (service) | JUnit, Mockito | Orchestration, contrôle de propriété | Un recruteur d'une autre entreprise obtient `ResourceNotFoundException` |
| Tranche web | `@WebMvcTest`, Spring Security Test | Routes, validation, sérialisation, codes HTTP, règles de rôle | Chaque endpoint renvoie 401 sans token et 403 avec un mauvais rôle |
| Tranche JPA | `@DataJpaTest` + Testcontainers | Mappings, requêtes, contraintes PostgreSQL | La contrainte d'unicité refuse une seconde candidature ; la recherche ignore les accents |
| Intégration complète | `@SpringBootTest` + Testcontainers (`@ServiceConnection`) | Parcours d'API de bout en bout, transactions, événements | Rejet d'une candidature : statut, historique, annulation d'entretien et audit dans la même transaction ; deux candidatures simultanées n'en créent qu'une |
| Architecture | Spring Modulith `ApplicationModules.verify()` | Frontières et absence de cycle | Échec si `ai` accède au dépôt de `job` |
| Adaptateur IA | Faux serveur HTTP | Format des appels à Ollama, traitement des erreurs | Réponse hors schéma → nouvelle tentative puis `AI_INVALID_RESPONSE` |

Un seul conteneur PostgreSQL est partagé par toute la suite d'intégration, pour qu'elle reste sous quelques minutes. Les tests de bout en bout Playwright (US-57) testent l'application complète lancée par Docker Compose.

**Objectif de couverture** : 70 % des lignes sur `domain` et `service`, mesuré par JaCoCo et bloquant en CI. Les contrôleurs sont couverts par les tests de tranche, sans objectif chiffré séparé.

## 11. Ce que tu dois savoir défendre

**« Pourquoi désactiver `open-in-view` ? »** Activé, il garde la session Hibernate ouverte jusqu'à la fin de la réponse HTTP. Un accès paresseux dans un mapper ou un contrôleur déclenche alors des requêtes invisibles, souvent en N+1, et monopolise une connexion. Désactivé, tout ce qui doit être chargé l'est explicitement dans le service.

**« Pourquoi `@Transactional` ne marche pas parfois ? »** Spring l'applique par un proxy. Un appel d'une méthode à une autre de la même classe ne passe pas par le proxy, donc pas de transaction. Autres pièges : méthode non publique, exception vérifiée (pas de rollback par défaut).

**« Comment valides-tu un JWT sans bibliothèque tierce ? »** Spring Security fournit, via le support « resource server », un `JwtDecoder` qui vérifie la signature, l'expiration et l'émetteur. Je ne l'ai pas réécrit : un filtre JWT fait à la main est une source classique de failles.

**« Que sont les threads virtuels ? »** Des threads très légers gérés par la JVM depuis Java 21 : un thread bloqué sur une requête SQL ne monopolise plus un thread système. Idéal pour une API qui attend surtout la base. Je ne les utilise pas pour l'IA, limitée par le CPU d'Ollama, où un petit pool borné protège la machine.

**« Pourquoi ne pas appeler Ollama dans une transaction ? »** Une transaction tient une connexion du pool. Deux minutes d'attente par analyse épuiseraient vite le pool et bloqueraient toute l'application. D'où le découpage : petite transaction, appel externe, petite transaction.

**« Que teste un `@WebMvcTest` par rapport à un `@SpringBootTest` ? »** `@WebMvcTest` ne charge que la couche web (contrôleurs, sécurité, sérialisation) avec des services simulés : rapide et ciblé. `@SpringBootTest` charge toute l'application avec une vraie base : plus lent, mais il vérifie que tout fonctionne ensemble.

**« Pourquoi Testcontainers plutôt que H2 ? »** H2 n'est pas PostgreSQL : pas de JSONB, pas de `tsvector`, des contraintes et un SQL légèrement différents. Un test qui passe sur H2 peut échouer en production. Testcontainers lance un vrai PostgreSQL 18 dans Docker, le même qu'en production.
