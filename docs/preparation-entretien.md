# SmartRecruit — Préparation à l'entretien technique

Ce document rassemble, par thème, les questions de tous les documents de conception et de toutes les notes de story, sans doublons, avec une réponse courte et un lien vers la source à relire. Il ajoute ce qu'aucun document ne couvrait : le pitch du projet, les questions des stories qui n'en avaient pas, et les points faibles à assumer.

Mode d'emploi : savoir dire le pitch sans notes, puis être capable de répondre à chaque question en trente secondes et d'ouvrir le fichier cité pour montrer le code ou le test. Une réponse qui s'appuie sur un test qu'on peut montrer vaut plus qu'une définition.

## Sommaire

1. [Présenter le projet](#1-présenter-le-projet)
2. [Organisation et méthode](#2-organisation-et-méthode)
3. [Architecture](#3-architecture)
4. [Conception du domaine](#4-conception-du-domaine)
5. [Base de données](#5-base-de-données)
6. [API REST](#6-api-rest)
7. [Sécurité](#7-sécurité)
8. [Concurrence et cohérence](#8-concurrence-et-cohérence)
9. [Spring et Java](#9-spring-et-java)
10. [Intelligence artificielle](#10-intelligence-artificielle)
11. [Frontend Angular](#11-frontend-angular)
12. [Tests et qualité](#12-tests-et-qualité)
13. [Industrialisation](#13-industrialisation)
14. [Données personnelles et éthique](#14-données-personnelles-et-éthique)
15. [Points faibles et ce que je ferais autrement](#15-points-faibles-et-ce-que-je-ferais-autrement)

## 1. Présenter le projet

**En trente secondes.** SmartRecruit est un logiciel de suivi des candidatures multi-entreprises. Les candidats postulent, les recruteurs font avancer chaque candidature dans un workflow à 8 statuts, planifient des entretiens, et une IA locale analyse les CV. C'est un monolithe modulaire Spring Boot 4.1 et Angular 22, avec PostgreSQL 18 et Ollama, lancé en une commande par Docker Compose.

**En deux minutes, ajouter :**

- **La démarche** : dix documents de conception avant la première ligne de code, puis 58 user stories développées une à une en tranches verticales, chacune avec sa note (critères et preuves, choix, questions).
- **Trois décisions dont je suis fière** :
  - le score de matching est calculé par une formule et non par le LLM, donc reproductible et insensible aux CV piégés ([ADR-008](adr/008-score-par-formule.md)) ;
  - les frontières entre modules sont vérifiées par un test ([ADR-001](adr/001-monolithe-modulaire.md)) ;
  - l'isolation entre entreprises est garantie côté serveur par l'entreprise du token, avec un 404 hors périmètre.
- **Les chiffres** : 325 tests unitaires et 218 d'intégration sur un vrai PostgreSQL, 94,9 % de couverture des services, recherche d'offres en 20 ms au p95 sur 2 000 offres, tableaux de bord en 15 ms sur 10 000 candidatures.
- **Ce qui reste à faire, dit honnêtement** : voir la [section 15](#15-points-faibles-et-ce-que-je-ferais-autrement).

## 2. Organisation et méthode

**« Comment as-tu organisé ton projet ? »** Conception d'abord, dix documents validés étape par étape. Puis développement en tranches verticales : chaque phase livre une fonctionnalité complète, testée et intégrée en continu. Backlog priorisé en MoSCoW, estimé en points, suivi story par story. — [roadmap](roadmap.md)

**« Pourquoi écrire un cahier des charges pour un projet personnel ? »** Les règles métier (transitions, droits, unicité) déterminent le modèle de données et les tests. Les écrire avant évite de refaire le schéma en cours de route, et chaque règle RG-xx devient un test. — [cahier des charges](cahier-des-charges.md)

**« Pourquoi des tranches verticales plutôt que le backend puis le frontend ? »** Une tranche montre très tôt si l'API convient vraiment à l'écran qui l'utilise, et chaque phase produit quelque chose de démontrable. Faire tout le backend d'abord repousse de plusieurs mois la découverte des erreurs de conception. — [roadmap](roadmap.md)

**« Qu'est-ce qu'une bonne user story ? »** Elle respecte INVEST : indépendante autant que possible, négociable, porteuse de valeur, estimable, petite et testable. Ici, chaque critère d'acceptation est formulé pour devenir un test. — [user stories](user-stories.md)

**« Une story technique, est-ce vraiment une user story ? »** Pas au sens strict. Je les assume comme « enablers », limitées à une dizaine sur 58 et rattachées à un bénéficiaire concret (la développeuse, l'exploitant, le visiteur du dépôt). — [user stories](user-stories.md)

**« Pourquoi des points plutôt que des jours ? »** Les points comparent les stories entre elles sans prétendre prédire une durée. La durée se déduit ensuite de la vélocité mesurée. Je n'ai pas donné de date ferme avant d'avoir cette mesure. — [user stories](user-stories.md), [roadmap](roadmap.md)

**« Que ferais-tu en cas de retard ? »** Réduire le périmètre, pas la qualité : retirer d'abord les COULD, puis les SHOULD, jamais les tests ni la sécurité des MUST. L'ordre des leviers est décidé à l'avance, pas dans l'urgence. — [roadmap §9](roadmap.md)

**« Quelle différence entre MVP et V1 ? »** Le MVP est le plus petit produit qui démontre la valeur : candidature, workflow, IA, tableau de bord recruteur. La V1 y ajoute ce qui le rend exploitable : administration, déploiement en une commande, pipeline complète, documentation. — [roadmap](roadmap.md)

**« Pourquoi US-35 est-elle MUST alors que les entretiens étaient SHOULD ? »** En écrivant les dépendances, on voit que RG-33 (une proposition exige un entretien terminé) rend la planification et la clôture indispensables au workflow. Les dépendances révèlent les vraies priorités. — [user stories](user-stories.md)

**« À quoi sert la Definition of Done ? »** À rendre « terminé » vérifiable et identique pour toutes les stories : tests, documentation, CI verte. Sans elle, « c'est fini » veut souvent dire « ça marche sur mon poste ». — [user stories](user-stories.md)

**« Quelle différence entre « include » et « extend » dans un diagramme de cas d'utilisation ? »** « include » : le cas lié s'exécute toujours (postuler inclut la vérification du profil). « extend » : il s'exécute sous condition (le rejet n'annule les entretiens que s'il y en a). — [analyse fonctionnelle](analyse-fonctionnelle.md)

## 3. Architecture

**« Qu'est-ce qu'un monolithe modulaire ? »** Un seul déploiement, mais des modules aux frontières explicites et vérifiées. Chacun a une API publique, ne touche pas aux données internes des autres et communique par interfaces ou par événements. Un monolithe classique laisse n'importe quelle classe appeler n'importe quelle autre, ce qui le rend impossible à découper. — [ADR-001](adr/001-monolithe-modulaire.md)

**« Pourquoi pas des microservices, puisque les offres d'emploi en parlent ? »** Ils résolvent des problèmes d'organisation (équipes indépendantes) et d'échelle que ce projet n'a pas. Une développeuse, un domaine, un volume modeste : ils n'ajouteraient que réseau, déploiements et cohérence distribuée. Je sais où je couperais et pourquoi je ne le fais pas. — [architecture §9](architecture.md)

**« Comment les frontières sont-elles vérifiées ? »** Spring Modulith : chaque module expose un sous-paquet `api` déclaré `@NamedInterface`. Le test `ApplicationModules.verify()` échoue au build si un module touche l'intérieur d'un autre ou si un cycle apparaît. Le backend compte 12 modules. — [ADR-001](adr/001-monolithe-modulaire.md)

**« Le module reporting lit les tables des autres : n'est-ce pas contraire à ton architecture ? »** Si, et c'est volontaire, limité et écrit dans le code : lecture seule, deux endroits (`reporting` et `RecruiterApplicationSearch`). Toutes les écritures passent par les API. Spring Modulith vérifie le Java, pas le SQL : le risque est qu'une migration casse ces requêtes, et `DashboardIT` le détecte. — [US-30](stories/US-30.md), [US-44](stories/US-44.md)

**« Qu'est-ce que le modèle C4 ? »** Quatre niveaux de zoom : contexte (le système et ses utilisateurs), conteneurs (applications et bases déployées), composants (modules), code. L'architecture couvre les trois premiers. — [architecture](architecture.md)

**« Pourquoi Nginx devant Spring Boot ? »** Il sert les fichiers d'Angular bien plus efficacement que la JVM, pose les en-têtes de sécurité à un seul endroit, place frontend et API sur la même origine (pas de CORS, cookie `SameSite=Strict`) et ne publie qu'un port. Le backend n'est joignable que sur le réseau interne. — [ADR-004](adr/004-meme-origine-via-nginx.md)

**« Comment ton API sait-elle qui fait la requête, sans session ? »** Chaque requête porte un access token signé. Le serveur vérifie la signature et lit l'identifiant, le rôle et l'entreprise : aucune session à stocker, donc n'importe quelle instance peut répondre. — [architecture](architecture.md)

**« Et si ça devait passer à l'échelle ? »** Chaque évolution attend un déclencheur mesuré, et son point d'entrée est déjà prévu : stockage objet derrière `FileStorage`, module `ai` extractible avec un GPU, compteur de tentatives partagé dès la deuxième instance, moteur de recherche au-delà de quelques centaines de milliers d'offres. — [architecture §9](architecture.md)

## 4. Conception du domaine

**« Modèle anémique ou modèle riche ? »** Riche pour les règles locales : l'entité protège ses invariants (`Application.moveTo`, `JobOffer.publish`). Des services pour ce qui traverse plusieurs agrégats (éligibilité à postuler, fusion de compétences). — [diagramme de classes](diagrams/classes.md)

**« Qu'est-ce qu'un agrégat ? »** Un groupe d'objets modifié comme un tout, avec une racine par laquelle on passe obligatoirement. On ne modifie jamais une `Experience` sans passer par son `CandidateProfile`, et une transaction ne modifie qu'un agrégat. — [diagramme de classes](diagrams/classes.md)

**« Pourquoi pas un `@ManyToOne JobOffer` dans `Application` ? »** Chaque module dépendrait des entités des autres, et `application.getJobOffer().getCompany()` déclencherait des chargements en chaîne. Les références par identifiant gardent les modules indépendants ; les listes utilisent des projections. — [ADR-006](adr/006-references-par-identifiant.md)

**« Qu'est-ce que l'inversion des dépendances, concrètement ? »** Candidatures doit savoir si un entretien est terminé, sans dépendre d'Entretiens. Il déclare `CompletedInterviewChecker`, et c'est Entretiens qui l'implémente. Même principe pour `AIService` et le stockage : le métier définit le contrat, l'infrastructure le remplit. — [diagramme de classes](diagrams/classes.md), [US-32](stories/US-32.md)

**« Comment est codé le workflow de candidature ? »** La table des 15 transitions vit dans l'énumération `ApplicationStatus` ; les conditions de contexte (acteur, motif, entretien terminé) dans `ApplicationWorkflow`, sans Spring. Un test vérifie les 64 couples contre une table écrite à la main. — [ADR-007](adr/007-workflow-dans-application-status.md)

**« Pourquoi le workflow est-il contrôlé côté backend alors que le frontend n'affiche que les boutons autorisés ? »** Le frontend est une commodité, pas une sécurité : n'importe qui peut envoyer une requête à la main. Si le frontend envoie `WITHDRAWN`, le serveur refuse : c'est réservé au candidat. — [cahier des charges](cahier-des-charges.md), [US-32](stories/US-32.md)

**« Pourquoi un événement plutôt qu'un appel de service ? »** Pour respecter le sens des dépendances : Identité annonce « candidat inscrit », Candidats crée le profil ; `identity` ne connaît pas `company`. L'événement est synchrone et transactionnel : même comportement qu'un appel direct, sans le couplage. — [US-06](stories/US-06.md), [US-47](stories/US-47.md)

**« Le retrait annule les entretiens : comment, sans que Candidatures dépende d'Entretiens ? »** Candidatures publie `ApplicationStatusChanged` ; Entretiens l'écoute dans la même transaction. La dépendance va d'Entretiens vers Candidatures, jamais l'inverse. — [US-29](stories/US-29.md), [US-33](stories/US-33.md)

**« Où se fait la correspondance entre statut interne et statut affiché au candidat ? »** Dans une seule énumération de l'API du module (`CandidateStatus.of`), utilisée par la vue candidat, la chronologie et le tableau de bord. Une seule table, testée. — [US-45](stories/US-45.md)

**« Pourquoi interdire le retour en brouillon d'une offre qui a des candidatures ? »** Un brouillon est invisible : les candidats auraient postulé à une offre qui n'existe plus pour eux. On clôture à la place. — [US-23](stories/US-23.md)

**« Quelle différence entre historique et audit ? »** L'historique raconte la vie d'une candidature aux utilisateurs métier. L'audit est le registre de sécurité de toute la plateforme, lu par l'admin. Lecteurs, contenu et conservation différents ; les deux sont immuables. — [cahier des charges](cahier-des-charges.md), [US-34](stories/US-34.md)

**« Pourquoi l'historique d'une note ne contient-il pas son texte ? »** Le texte peut être modifié ou supprimé (suppression logique), alors que l'historique est immuable. On y garde « note ajoutée » et l'identifiant ; une note n'est modifiable que par son auteur et jamais visible du candidat. — [US-39](stories/US-39.md)

## 5. Base de données

**« Pourquoi des UUID plutôt que des entiers auto-incrémentés ? »** Un entier révèle le volume et se devine. Un UUID v4 disperse les insertions dans l'index. Le v7 commence par un horodatage : opaque mais inséré dans l'ordre, et `uuidv7()` est natif dans PostgreSQL 18. L'opacité n'est pas une sécurité : le contrôle de propriété reste obligatoire. — [ADR-005](adr/005-cles-uuid-v7.md)

**« Qu'est-ce que la troisième forme normale, et où t'en écartes-tu ? »** Chaque colonne dépend de la clé, de toute la clé et de rien d'autre que la clé. Je m'en écarte avec `last_match_score`, pour filtrer sans jointure coûteuse ; la copie est mise à jour par la même opération que la source. — [modèle de données](modele-de-donnees.md)

**« Quelles règles sont garanties par la base, et pourquoi pas toutes ? »** Celles qui portent sur des lignes à un instant : unicité de la candidature, un seul CV actif, motif de rejet, lien obligatoire pour une visio. Les transitions restent dans le code : une contrainte ne connaît pas l'état précédent. Règle d'or : tout ce que deux requêtes simultanées peuvent violer doit être garanti par la base. — [modèle de données](modele-de-donnees.md)

**« Pourquoi valider l'ordre des dates à trois endroits ? »** Le formulaire pour le confort, le DTO pour un message par champ, la contrainte SQL comme garantie finale si un autre chemin écrivait en base. — [US-14](stories/US-14.md)

**« Pourquoi des énumérations en texte avec CHECK plutôt qu'un type ENUM ? »** Un type ENUM PostgreSQL ne se modifie qu'en le recréant. Le texte avec CHECK se lit directement en SQL et refuse quand même une valeur inconnue. Jamais d'ordinal, qu'un réordonnancement corromprait. — [ADR-011](adr/011-enumerations-en-texte-avec-check.md)

**« Pourquoi pas de CASCADE partout ? »** Une cascade peut effacer un historique entier par erreur. RESTRICT fait échouer la suppression, ce qui est sûr. La cascade est réservée aux éléments sans sens hors de leur parent. Pour la même raison, une catégorie utilisée par une offre ne se supprime pas : elle se désactive. — [modèle de données](modele-de-donnees.md), [US-50](stories/US-50.md)

**« Quand utilises-tu JSONB ? »** Pour des documents lus en entier, jamais filtrés, dont la forme évolue : les résultats IA. Dès qu'une valeur sert à filtrer, trier ou agréger, elle devient une colonne. — [modèle de données](modele-de-donnees.md), [ADR-009](adr/009-ia-asynchrone-par-table-de-taches.md)

**« Une clé étrangère est-elle indexée automatiquement ? »** Non : dans PostgreSQL, seules la clé primaire et les contraintes UNIQUE le sont. Sans index, jointures et suppressions sur le parent deviennent lentes. — [modèle de données §11](modele-de-donnees.md)

**« Pourquoi un index sur `lower(email)` alors que vous normalisez déjà ? »** La normalisation est une règle applicative ; l'index est la garantie. Un import SQL qui l'oublierait serait quand même arrêté. — [US-06](stories/US-06.md)

**« Pourquoi Flyway plutôt que laisser Hibernate créer les tables ? »** Le schéma devient du code versionné et relu, appliqué dans le même ordre partout. `ddl-auto: update` ne supprime jamais une colonne, ne sait pas renommer et ne laisse aucune trace. Hibernate est en `validate` dans tous les profils. — [US-02](stories/US-02.md)

**« Que se passe-t-il si tu modifies un script déjà appliqué ? »** Flyway compare une somme de contrôle par script : « checksum mismatch », l'application refuse de démarrer. On corrige par un nouveau script, comme V8 pour la cause de révocation des jetons. — [US-02](stories/US-02.md), [US-08](stories/US-08.md)

**« Pourquoi pas Elasticsearch pour la recherche ? »** Avec 2 000 offres, le p95 est de 20 ms en plein texte PostgreSQL (`tsvector`, `unaccent`). Un moteur dédié ajouterait une synchronisation et un service à surveiller pour un gain nul. Le seuil de bascule : recherche floue tolérante aux fautes ou millions de documents. — [US-25](stories/US-25.md)

**« Comment une offre expirée disparaît-elle sans tâche planifiée ? »** Le filtre `expires_at > now()` fait partie de chaque requête publique, appuyé sur l'index `(status, expires_at)`. — [US-25](stories/US-25.md)

**« Pourquoi ne pas précalculer les statistiques ? »** La mesure dit que c'est inutile : 15 ms sur 10 000 candidatures. Précalculer, c'est un cache à invalider ; je le ferais le jour où la mesure le justifie, avec une vue matérialisée. — [US-44](stories/US-44.md)

**« Pourquoi masquer les offres archivées plutôt que les supprimer ? »** Les candidatures et l'audit y font référence ; l'archive sort l'offre du quotidien sans perdre l'historique. Même logique pour un CV déjà envoyé : supprimé par le candidat, il est seulement désactivé, car une candidature le référence. — [US-24](stories/US-24.md), [US-19](stories/US-19.md)

## 6. API REST

**« PUT ou PATCH ? »** PUT remplace la ressource entière : formulaires complets (offre, profil). PATCH modifie une partie : changement de statut, où le client n'envoie qu'une cible et un motif. — [API](api.md)

**« `POST /jobs/{id}/publish`, ce n'est pas très REST ? »** Une transition d'état est une commande avec ses propres règles, pas une écriture de champ. L'exposer comme action rend chaque règle testable et évite qu'un PUT change le statut en contournant les vérifications. — [API](api.md)

**« Quelles méthodes sont idempotentes ? »** GET, PUT et DELETE. POST ne l'est pas, sauf quand on le rend tel : `logout`, `POST /skills` (obtenir ou créer), et la candidature est protégée par la contrainte d'unicité. — [API](api.md)

**« 401, 403 ou 404 ? »** 401 : on ne sait pas qui vous êtes. 403 : on sait, mais votre rôle ne le permet pas. 404 : la ressource n'existe pas, ou elle est hors de votre périmètre, et on ne dit pas laquelle des deux pour ne pas révéler son existence. — [API](api.md), [US-10](stories/US-10.md)

**« Pourquoi 409 et pas 400 pour une transition interdite ? »** La requête est bien formée ; c'est l'état de la ressource qui l'empêche. 400 dit « corrige ta requête », 409 dit « la ressource a changé, recharge-la ». — [US-03](stories/US-03.md), [US-32](stories/US-32.md)

**« Pourquoi 202 pour l'IA ? »** « Demande acceptée, traitement en cours » : le client reçoit un identifiant de tâche et interroge son état ; aucune connexion ne reste ouverte une minute. — [API](api.md)

**« Pagination par numéro de page ou par curseur ? »** Par page ici : listes modestes, l'utilisateur veut sauter à la page 3, Spring Data la fournit. Le curseur devient préférable pour de très grands volumes ou un défilement infini. La taille de page est plafonnée et le tri en liste blanche. — [API](api.md), [US-25](stories/US-25.md)

**« Que renvoies-tu pour une erreur 500 ? »** Un Problem Details (RFC 9457) avec un message générique et l'identifiant de corrélation. La trace complète n'est que dans les logs : elle révélerait des noms de classes ou de tables. — [US-03](stories/US-03.md)

**« À quoi sert l'identifiant de corrélation ? »** À relier une erreur vue par l'utilisateur à toutes les lignes de log de sa requête. Il est renvoyé dans l'en-tête et dans chaque erreur. — [US-03](stories/US-03.md)

**« Comment garantis-tu que le catalogue d'erreurs est complet ? »** Par le compilateur : les `switch` sur l'énumération des codes n'ont pas de branche par défaut. — [US-03](stories/US-03.md)

**« Pourquoi `alreadyApplied` peut-il valoir `null` ? »** Trois états : `null` pour un visiteur ou un recruteur (la question n'a pas de sens), `true` ou `false` pour un candidat. Le frontend n'a pas à deviner le rôle. — [US-26](stories/US-26.md)

**« Où est la documentation de l'API ? »** Le contrat complet (règles, erreurs, matrice d'accès) est dans `docs/api.md`. La description OpenAPI générée par springdoc et Swagger UI sont servis sous `/api/docs` en `dev` et `demo`, et coupés en production, où ils décriraient la surface d'attaque. — [US-58](stories/US-58.md)

## 7. Sécurité

**« Où stockes-tu le JWT ? »** L'access token (15 minutes) en mémoire, le refresh token (7 jours) dans un cookie `HttpOnly`, `SameSite=Strict`, limité à `/api/v1/auth`. Le `localStorage` est lisible par tout script : une seule faille XSS suffirait. — [ADR-003](adr/003-jwt-et-refresh-token.md)

**« Pourquoi deux jetons ? »** L'access token est court et sans état : s'il fuit, il expire vite. Le refresh token est long mais révocable, stocké haché (SHA-256) en base, et inaccessible au JavaScript. — [US-07](stories/US-07.md)

**« Que se passe-t-il si un attaquant vole un refresh token ? »** Il peut l'utiliser une fois. Dès que l'utilisateur légitime renouvelle avec le même jeton, déjà tourné, le serveur détecte la réutilisation, ferme toutes les sessions et l'audit le signale. — [US-08](stories/US-08.md)

**« Un JWT est sans état : comment le révoquer avant son expiration ? »** On réintroduit un minimum d'état : une version de session par compte, incrémentée à la désactivation. Le token porte la version de son émission ; le validateur refuse une version dépassée. — [US-48](stories/US-48.md)

**« L'access token reste valable 15 minutes après la déconnexion : est-ce grave ? »** Il n'existe plus côté client. Pour un vol antérieur, la fenêtre est bornée à 15 minutes : c'est le compromis assumé des tokens sans état, et la version de session couvre les cas graves (compte désactivé). — [US-09](stories/US-09.md)

**« Comment valides-tu le JWT ? »** Avec le resource server de Spring Security (`NimbusJwtDecoder`) : signature HS256 seule acceptée (pas d'« alg: none »), expiration, émetteur, version de session. Un filtre JWT écrit à la main est une source classique de failles. — [architecture backend](architecture-backend.md)

**« Comment empêches-tu un recruteur de voir les candidatures d'une autre entreprise ? »** Le rôle ne suffit pas : chaque service vérifie que la ressource appartient à l'entreprise du recruteur, lue dans le token et jamais dans un paramètre. Si un recruteur envoie `companyId` dans le JSON, il est ignoré : le DTO n'a pas ce champ, et un test le prouve. — [cahier des charges](cahier-des-charges.md), [US-21](stories/US-21.md)

**« Comment évites-tu qu'un candidat lise le profil d'un autre ? »** L'URL ne contient aucun identifiant : `/candidates/me` lit l'identifiant dans le token. Pour les rubriques, chaque requête filtre sur le candidat connecté, et l'élément d'un autre répond 404. — [US-13](stories/US-13.md)

**« Comment empêches-tu un utilisateur de s'inscrire comme admin ? »** Le DTO n'a pas de champ rôle, la fabrique fixe CANDIDATE, et un test envoie `"role":"ADMIN"` pour le prouver. — [US-06](stories/US-06.md)

**« Les guards Angular suffisent-ils ? »** Non : tout code frontend se modifie dans le navigateur. Les guards évitent d'afficher des écrans inutiles et de télécharger du code ; la sécurité est dans l'API (matrice d'accès refusant par défaut), puis dans le contrôle de propriété des services. — [US-10](stories/US-10.md)

**« Pourquoi le même message pour un mot de passe faux et un compte désactivé ? »** Sinon l'API confirme qu'un compte existe. Un email inconnu prend même le temps d'une vérification de hash factice. Seul l'audit distingue les cas. — [US-07](stories/US-07.md)

**« Un attaquant peut-il bloquer le compte d'un autre ? »** Pendant 15 minutes après 5 échecs, oui : c'est le prix de la protection. Le blocage est temporaire et ne touche que la connexion par mot de passe ; les sessions en cours continuent. — [US-12](stories/US-12.md)

**« Pourquoi révoquer les autres sessions au changement de mot de passe ? »** On change souvent de mot de passe parce qu'on le croit compromis : les sessions d'un intrus doivent tomber en même temps. — [US-11](stories/US-11.md)

**« Comment te protèges-tu de la traversée de répertoires sur les CV ? »** Le nom d'origine n'est jamais un chemin : la clé est un UUID généré par le serveur, vérifiée par une expression stricte, et le chemin résolu doit rester sous la racine. Le fichier est contrôlé par sa signature PDF et par PDFBox, pas par son extension. — [US-17](stories/US-17.md)

**« Pourquoi pas une URL signée pour télécharger un CV ? »** Utile avec S3 pour décharger l'API. Ici, les fichiers sont petits et locaux, et passer par l'API garde un seul point de contrôle d'accès, audité pour les recruteurs. — [US-18](stories/US-18.md)

**« Pourquoi refuser les liens `http://` dans un profil ? »** Le lien est affiché aux recruteurs : https évite l'interception, et l'analyse de l'URL ferme la porte aux schémas exécutables. — [US-16](stories/US-16.md)

**« Quelle est ta politique CSP ? »** `script-src 'self'` sans aucun script en ligne (`inlineCritical` désactivé au build) ; `'unsafe-inline'` seulement pour les styles, qu'Angular injecte. L'API renvoie `default-src 'none'`. Les tests Playwright échouent à la moindre violation CSP. — [ADR-004](adr/004-meme-origine-via-nginx.md), [US-57](stories/US-57.md)

**« Un admin malveillant peut-il effacer ses traces ? »** Pas par l'application : aucun endpoint d'écriture. Pas par une requête SQL de l'utilisateur applicatif : un déclencheur refuse. Il faudrait être propriétaire de la base, ce qui relève de l'administration système. — [US-53](stories/US-53.md)

**« Quelle différence entre l'audit et les logs ? »** Les logs servent au diagnostic, vivent peu et peuvent être perdus. L'audit est une donnée métier : en base, dans la même transaction que l'action, consultable par l'admin, conservé 12 mois. — [US-52](stories/US-52.md)

**« Comment évites-tu les secrets dans le dépôt alors que `cp .env.example .env` suffit ? »** Les valeurs d'exemple sont fictives et nommées comme telles. Le profil `prod` n'a aucune valeur par défaut pour les secrets (démarrage refusé), et la CI cherche les secrets commités (Trivy). — [US-54](stories/US-54.md)

## 8. Concurrence et cohérence

**« Deux recruteurs changent le même statut en même temps ? »** Verrouillage optimiste : le client renvoie la version lue ; si elle a changé, 409 `CONCURRENT_MODIFICATION`, et l'écran propose de recharger. Pas de verrou bloquant, donc pas d'attente. — [US-32](stories/US-32.md)

**« Comment évites-tu les doublons de candidature ? »** Par une contrainte d'unicité sur le couple candidat et offre. Une vérification applicative seule laisse passer deux requêtes simultanées ; il n'y a rien à verrouiller avant que la ligne existe, donc pas de `SELECT … FOR UPDATE`. — [US-27](stories/US-27.md)

**« Comment as-tu testé la concurrence ? »** Quatre threads attendent un `CountDownLatch`, puis envoient la même candidature. On vérifie un seul 201 et une seule ligne en base ; le test échouerait sans l'index. — [US-27](stories/US-27.md)

**« Deux candidats créent « Quarkus » au même moment ? »** Le premier INSERT réussit, le second viole l'index unique. Sa petite transaction est annulée, le service relit et renvoie la compétence créée par l'autre. — [US-15](stories/US-15.md)

**« Pourquoi verrouiller pour compter les admins ? »** Sans verrou, deux transactions lisent chacune « 2 admins actifs » et chacune désactive l'autre. Le `FOR UPDATE` sérialise : la seconde ne voit plus qu'un admin. — [US-48](stories/US-48.md)

**« Deux onglets renouvellent la session en même temps ? »** Le second présenterait un jeton déjà tourné, ce qui ressemble à un vol. Côté Angular, un seul renouvellement est partagé dans l'onglet, et l'API Web Locks coordonne les onglets ; la règle serveur reste stricte. — [US-08](stories/US-08.md)

**« Le disque et la base ne partagent pas de transaction : comment restes-tu cohérent ? »** Fichier d'abord, puis base ; compensation si la base échoue ; nettoyage périodique des orphelins si la compensation échoue elle-même. On accepte un fichier orphelin, jamais une ligne sans fichier. — [US-17](stories/US-17.md)

**« Comment prouves-tu que la désactivation d'une entreprise est atomique ? »** Un test la fait échouer au milieu : un déclencheur de test refuse la mise à jour des offres. La réponse est 500, et l'entreprise, ses recruteurs et ses offres sont exactement comme avant. — [US-49](stories/US-49.md)

**« Pourquoi écrire l'audit dans la même transaction ? »** Pour qu'il n'existe pas d'action sans trace, ni de trace sans action. Exception : une connexion échouée est annulée, mais sa trace doit rester, d'où `REQUIRES_NEW`. — [US-52](stories/US-52.md)

**« Fusion de deux compétences : que devient un profil qui avait les deux ? »** La clé `(candidate_id, skill_id)` interdit deux lignes. On met d'abord à jour la ligne cible avec le maximum des niveaux et des durées, on supprime la source, puis on reporte les profils qui n'avaient que la source. — [US-51](stories/US-51.md)

## 9. Spring et Java

**« Pourquoi `@Transactional` ne marche-t-il pas parfois ? »** Spring l'applique par un proxy. Un appel interne à la même classe ne passe pas par le proxy. Autres pièges : méthode non publique, exception vérifiée (pas de rollback par défaut). — [architecture backend](architecture-backend.md)

**« Pourquoi désactiver `open-in-view` ? »** Activé, il garde la session Hibernate ouverte jusqu'à la fin de la réponse : un accès paresseux dans un mapper déclenche des requêtes invisibles, souvent en N+1, et monopolise une connexion. Désactivé, tout chargement est explicite dans le service. — [US-02](stories/US-02.md)

**« Comment évites-tu le problème N+1 ? »** Associations LAZY, aucune association entre agrégats, projections pour les listes, `default_batch_fetch_size`. Les tests comptent les requêtes SQL. — [diagramme de classes](diagrams/classes.md), [US-30](stories/US-30.md)

**« Pourquoi `equals`/`hashCode` sur l'identifiant ? »** Hibernate peut renvoyer un proxy, et un objet change de champs au cours de sa vie. Avec des UUID v7 générés dès la création, l'identifiant est stable du début à la fin. — [diagramme de classes](diagrams/classes.md)

**« Que sont les threads virtuels ? »** Des threads très légers gérés par la JVM : un thread bloqué sur une requête SQL ne monopolise plus un thread système. Activés pour les requêtes HTTP. Pas pour l'IA, limitée par le CPU d'Ollama, où un pool borné de 2 threads protège la machine. — [architecture backend](architecture-backend.md)

**« Pourquoi ne pas appeler Ollama dans une transaction ? »** Une transaction tient une connexion du pool : deux minutes d'attente par analyse l'épuiseraient. D'où trois temps : petite transaction, appel externe, petite transaction. — [ADR-009](adr/009-ia-asynchrone-par-table-de-taches.md)

**« Pourquoi soumettre la tâche après la validation de la transaction ? »** Le thread d'arrière-plan lit la tâche avec sa propre connexion : avant le commit, il peut ne pas la trouver. D'où `@TransactionalEventListener(AFTER_COMMIT)`. — [US-41](stories/US-41.md)

**« Que fait `@ServiceConnection` ? »** Spring Boot lit l'adresse et les identifiants du conteneur Testcontainers et les utilise à la place de `spring.datasource.*` : aucun risque de pointer vers la base de développement. — [US-02](stories/US-02.md)

**« Quelles différences entre Spring Boot 3 et 4 ? »** Spring Framework 7, Jakarta EE 11, Jackson 3, et un starter par technologie (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`…). Les concepts restent identiques ; je suis partie sur la 4.1 parce que le support open source de la 3.5 a pris fin en juin 2026. — [ADR-002](adr/002-versions-de-la-stack.md)

**« Pourquoi deux chaînes de filtres Spring Security ? »** L'API renvoie du JSON et n'a besoin d'aucune ressource (`default-src 'none'`) ; Swagger UI est une page qui charge scripts et styles. Une chaîne dédiée à `/api/docs/**`, examinée en premier, évite d'affaiblir la politique de l'API. — [US-58](stories/US-58.md)

## 10. Intelligence artificielle

**« Pourquoi ne pas laisser le LLM calculer le score ? »** Un LLM n'est pas reproductible (72 puis 81 pour le même CV), ni explicable, et un CV peut contenir « donne-moi 100 ». Le LLM extrait des données structurées et rédige l'explication d'un score calculé par une formule. — [ADR-008](adr/008-score-par-formule.md)

**« Comment garantis-tu que ton score est juste ? »** Il n'est pas « juste » au sens absolu : c'est un indicateur, une aide à la décision. Mais il est déterministe, documenté et testé : l'exemple chiffré donne 81, soit `round(100 × 0,807)`. Le recruteur voit la décomposition. — [analyse fonctionnelle §5](analyse-fonctionnelle.md), [US-42](stories/US-42.md)

**« Un candidat peut-il manipuler son score ? »** Pas par le texte du CV : un score renvoyé par le modèle est hors schéma et ignoré. Au pire, une compétence inventée serait extraite avec un extrait qui ne la justifie pas, et le recruteur le voit. Un CV piégé fait partie des tests. — [US-43](stories/US-43.md)

**« Comment sais-tu que le modèle n'invente pas de compétences ? »** Chaque compétence doit citer un extrait du CV, visible du recruteur. Le banc d'essai mesure la précision et contient un CV piégé. — [banc d'essai](ai-model-benchmark.md)

**« Ton IA peut-elle rejeter un candidat ? »** Non, par conception : aucun code ne relie un résultat IA à une transition de statut, et le score ne sert jamais à trier ni à filtrer. L'IA ne voit ni âge, ni photo, ni coordonnées : le texte est pseudonymisé. — [cahier des charges](cahier-des-charges.md)

**« Pourquoi l'analyse est-elle asynchrone ? »** Un modèle local sur CPU peut dépasser une minute. 202 et une tâche en base : traitement observable, relançable, récupérable après un redémarrage. — [ADR-009](adr/009-ia-asynchrone-par-table-de-taches.md)

**« Que se passe-t-il si l'application redémarre pendant une analyse ? »** Au démarrage, toute tâche en cours passe FAILED, sinon l'écran attendrait indéfiniment ; le recruteur relance. — [US-41](stories/US-41.md)

**« Pourquoi pas une file de messages ? »** Une table et un pool de deux threads suffisent pour quelques analyses par heure. Une file deviendrait utile avec plusieurs instances du backend. — [US-41](stories/US-41.md)

**« Et si Ollama tombe ? »** L'application démarre et fonctionne : les analyses échouent proprement, le health check reste UP avec un composant IA « indisponible », et l'admin le voit sur son tableau de bord (sonde gardée 30 s en cache). — [US-40](stories/US-40.md), [US-46](stories/US-46.md)

**« Comment changerais-tu de fournisseur ? »** Une classe qui implémente `AIService`, une valeur de `provider` : rien d'autre ne change. La pseudonymisation est déjà en place pour un fournisseur cloud. — [ADR-010](adr/010-ollama-restclient-aiservice.md)

**« Pourquoi pas Spring AI ? »** Une seconde abstraction au-dessus d'`AIService` qui masque l'appel réel. Un appel `RestClient` avec schéma JSON imposé reste lisible et se teste contre un faux serveur HTTP. Spring AI est une bonne évolution, présentée comme telle. — [ADR-010](adr/010-ollama-restclient-aiservice.md)

**« Comment as-tu choisi le modèle ? »** Par un banc d'essai et non au jugé : 10 CV fictifs dont un piégé, extraction attendue écrite à la main, précision et rappel des compétences, temps, mémoire. Seuils : précision 0,85, rappel 0,80, moins de 120 s par CV. — [banc d'essai](ai-model-benchmark.md)

**« Pourquoi pas le plus gros modèle ? »** L'application doit tourner sans GPU. Un modèle de 7 à 9 milliards de paramètres répond en dizaines de secondes sur CPU ; quatre fois plus gros, il dépasserait le délai. — [banc d'essai](ai-model-benchmark.md)

## 11. Frontend Angular

**« Qu'est-ce qu'un signal ? »** Une valeur réactive : quand elle change, Angular sait quels `computed` et quelles parties de template en dépendent, et ne met à jour qu'elles. Plus besoin de Zone.js pour deviner quand tout vérifier, ni de `subscribe` pour de l'état simple. — [architecture frontend](architecture-frontend.md)

**« Que veut dire OnPush ? »** Un composant n'est revérifié que si un `input()` change, si un signal lu dans son template change, ou si un événement s'y produit. Avec les composants autonomes, c'est le défaut d'Angular 22. — [architecture frontend](architecture-frontend.md), [US-04](stories/US-04.md)

**« Pourquoi un `canMatch` ? »** Il empêche la route d'être retenue, donc le code de l'espace d'être téléchargé : un candidat ne charge jamais le code de l'administration. — [architecture frontend](architecture-frontend.md)

**« Composant intelligent et composant de présentation ? »** La page connaît les services, l'URL et les données ; le composant de présentation reçoit des entrées et émet des événements, ce qui le rend facile à tester et à réutiliser. — [architecture frontend](architecture-frontend.md)

**« Reactive Forms ou Signal Forms ? »** Les Signal Forms sont plus cohérents avec les signaux. J'ai choisi les Reactive Forms typés parce qu'ils dominent les projets existants ; je sais expliquer la différence et migrer un formulaire. — [architecture frontend](architecture-frontend.md)

**« Pourquoi des intercepteurs, et dans quel ordre ? »** Pour traiter une fois ce qui est commun à tous les appels. Ordre `[correlation, error, refresh, auth]` : l'ordre de la liste pour la requête, l'inverse pour la réponse ; l'intercepteur d'erreur ne voit que l'erreur finale, après un éventuel renouvellement. — [US-04](stories/US-04.md), [US-08](stories/US-08.md)

**« Pourquoi le rejeu passe-t-il par `next(request)` et pas par `HttpClient` ? »** Pour rester dans la chaîne au même niveau : `auth` réinjecte le nouveau token, et l'erreur éventuelle du rejeu remonte sans nouveau renouvellement. — [US-08](stories/US-08.md)

**« Comment la session revient-elle au rechargement, si le token est en mémoire ? »** Un initialiseur appelle `/auth/refresh` avant le premier affichage : le cookie HttpOnly suffit à obtenir un nouvel access token. — [US-08](stories/US-08.md)

**« Comment gères-tu les fuseaux horaires ? »** Stockage en UTC, transport en ISO 8601 avec décalage, affichage dans le fuseau du navigateur. Casablanca est un bon cas de test : son décalage change, exactement le cas où stocker des heures locales casse. — [US-35](stories/US-35.md)

## 12. Tests et qualité

**« Pourquoi Testcontainers plutôt que H2 ? »** Un test sur H2 prouve que le code marche sur H2. Le projet utilise `uuidv7()`, `unaccent`, `tsvector`, JSONB, des index partiels et des déclencheurs : seul un vrai PostgreSQL 18 les valide. — [US-02](stories/US-02.md)

**« `@WebMvcTest` ou `@SpringBootTest` ? »** Le premier ne charge que la couche web avec des services simulés : rapide et ciblé. Le second charge toute l'application avec une vraie base : plus lent, mais il vérifie l'ensemble. — [architecture backend](architecture-backend.md)

**« Comment testes-tu qu'aucune transition interdite n'est possible ? »** Test paramétré sur le produit cartésien : 64 couples de statuts de candidature, 24 transitions d'offre. Chaque ligne donne le résultat attendu ; ajouter un statut oblige à compléter la table. — [US-23](stories/US-23.md), [US-32](stories/US-32.md)

**« Comment testes-tu l'absence de N+1 ? »** La `DataSource` est enveloppée dans un proxy qui compte les requêtes préparées ; on compare une page de 2 lignes et une page de 20, le nombre doit être identique. — [US-30](stories/US-30.md)

**« Comment prouves-tu qu'aucune donnée interne ne fuit vers le candidat ? »** Le test lit la réponse brute et cherche les chaînes interdites (motif de rejet, notes). Un test sur les champs attendus ne verrait pas un champ en trop. — [US-28](stories/US-28.md)

**« Comment testes-tu « après l'heure de début » sans attendre ? »** Les règles temporelles sont testées en unitaire avec une horloge fixe (`Clock` injectée partout) ; en intégration, on avance la date directement en base. — [US-37](stories/US-37.md)

**« 95 % de couverture, n'est-ce pas suspect ? »** C'est la couverture des lignes des services et des règles métier par tous les tests, intégration comprise, avec un seuil bloquant à 70 %. Elle dit qu'aucun chemin métier n'est oublié ; la qualité des assertions se voit dans les tests d'erreur, de concurrence et de sécurité. — [US-55](stories/US-55.md)

**« Que testent tes tests Playwright ? »** Les trois parcours critiques sur la vraie pile derrière Nginx : inscription puis candidature, recruteur qui fait avancer une candidature, accès refusé à l'espace d'un autre rôle. Chaque test échoue aussi sur toute erreur JavaScript ou violation CSP. — [US-57](stories/US-57.md)

**« Comment fabriques-tu des données de démonstration réalistes ? »** Par écriture SQL directe dans un module à part, qui reproduit les règles que la base ne vérifie pas seule (historique cohérent, entretien terminé avant une proposition). Les services, eux, fixent les dates à « maintenant » et ne pourraient pas fabriquer un historique de plusieurs semaines. Un test vérifie la cohérence. — [US-56](stories/US-56.md)

**« Un test d'intégration est instable : que fais-tu ? »** Je le corrige ou je le mets en quarantaine immédiatement, jamais en relançant jusqu'au vert : une pipeline rouge au hasard apprend à l'ignorer. — [US-05](stories/US-05.md)

**« As-tu trouvé des bugs grâce aux tests ? »** Oui : un test qui enchaînait déconnexion puis renouvellement depuis un second appareil a montré qu'on fermait à tort toutes les sessions. Corrigé par la migration V8 (cause de révocation) ; le cas est désormais un critère. — [US-08](stories/US-08.md)

## 13. Industrialisation

**« Que fait ta pipeline CI ? »** Tests unitaires et d'intégration, couverture JaCoCo avec seuil bloquant, SpotBugs, Trivy (dépendances, secrets, images), tests Playwright sur la pile lancée par Docker Compose, puis publication des images sur `main`. — [US-55](stories/US-55.md)

**« Pourquoi analyser à la fois le dépôt et les images ? »** Ils ne voient pas la même chose. L'analyse du dépôt lit `pom.xml` et `package-lock.json` ; l'analyse de l'image lit ce qui est réellement livré, jars et paquets du système. Au premier passage sur GitHub, l'analyse du dépôt était verte, mais celle de l'image a trouvé quatre CVE critiques dans le Tomcat embarqué : une dépendance transitive dont la version vient de la nomenclature de Spring Boot. Je les ai corrigées en passant Spring Boot de 4.1.1 à 4.1.2, et l'analyse tourne désormais à chaque push, pour bloquer la pull request plutôt que la publication. — [US-54](stories/US-54.md)

**« Pourquoi la CI dès la phase 2 ? »** Pour qu'aucune régression ne passe inaperçue dès le premier commit. Ajoutée à la fin, elle découvre d'un coup des dizaines d'erreurs accumulées. — [US-05](stories/US-05.md)

**« Comment sont construites tes images ? »** Builds multi-étapes. Backend : Maven puis JRE 25 seul, utilisateur non root, couches Spring Boot extraites pour qu'un changement de code ne repousse que la dernière couche. Frontend : Node puis Nginx non privilégié sur le port 8080. — [US-54](stories/US-54.md)

**« Différence entre liveness et readiness ? »** La liveness dit « le processus est vivant » : un échec entraîne un redémarrage. La readiness dit « je peux servir du trafic » : un échec retire l'instance du répartiteur. La base est dans la readiness, pas dans la liveness, sinon une panne de base ferait redémarrer toutes les instances en boucle. — [US-02](stories/US-02.md)

**« Pourquoi le backend ne dépend-il pas d'Ollama dans Docker Compose ? »** L'IA est facultative : le backend attend seulement que PostgreSQL soit sain. Un service ponctuel télécharge le modèle, et l'application est utilisable pendant ce temps. — [US-54](stories/US-54.md)

**« Où est le HTTPS ? »** Terminé en amont en production (reverse proxy ou répartiteur), là où on active HSTS, déjà prêt en commentaire. Le compose livré vise l'essai local en HTTP. — [ADR-004](adr/004-meme-origine-via-nginx.md)

## 14. Données personnelles et éthique

**« Pourquoi ne pas demander l'âge ou la photo ? »** Ils n'aident pas à évaluer une compétence et exposent à la discrimination à l'embauche ; le RGPD et la loi 09-08 imposent de ne collecter que le nécessaire. — [US-13](stories/US-13.md)

**« Pourquoi auditer un téléchargement de CV ? »** C'est l'accès à une donnée personnelle sensible : le journal répond à « qui a consulté mon CV ? ». — [US-31](stories/US-31.md)

**« Le candidat voit-il le motif de son rejet ? »** Non. Le motif est obligatoire pour le recruteur et conservé pour l'historique interne, mais le candidat voit un statut simplifié. Même chose pour le motif d'annulation d'un entretien : il voit « Annulé ». — [US-33](stories/US-33.md), [US-36](stories/US-36.md)

**« Que se passe-t-il si un candidat change de CV après avoir postulé ? »** La candidature garde la version envoyée : le recruteur évalue ce qu'il a reçu, et l'historique reste cohérent. — [cahier des charges](cahier-des-charges.md)

**« Qu'envoies-tu au modèle d'IA ? »** Du texte pseudonymisé : ni nom, ni coordonnées. Pour l'explication du score, seulement la décomposition (compétences, couvertures), jamais l'identité. Et le modèle tourne localement : rien ne quitte l'infrastructure. — [US-42](stories/US-42.md), [US-43](stories/US-43.md)

## 15. Points faibles et ce que je ferais autrement

Un recruteur technique cherche presque toujours la limite du projet. Mieux vaut la nommer soi-même, avec ce qu'on en a appris.

**« Qu'est-ce qui n'est pas terminé ? »**

- La pipeline n'a pas encore tourné sur GitHub : le jalon J4 n'est pas atteint.
- La construction réelle des images Docker reste à valider sur un poste avec une bonne connexion.
- Le modèle d'IA est provisoire tant que le banc d'essai n'a pas tourné, et l'image Ollama n'est pas épinglée.

Tout est écrit et vérifié autrement (hadolint, `docker compose config`, image simulée depuis ses couches, `actionlint`), mais je ne le présente pas comme validé. — [roadmap, phase 11](roadmap.md), [US-54](stories/US-54.md)

**« Qu'as-tu découvert en relisant ta documentation ? »**

- springdoc, prévu par la conception, n'avait jamais été intégré : corrigé en US-58.
- Sept diagrammes de conception ont été perdus à l'export, car ils n'étaient pas en Mermaid : la décision ADR-012 n'avait pas été appliquée partout.

Leçon : la documentation doit être vérifiée contre le code, pas seulement écrite. — [US-58](stories/US-58.md), [ADR-012](adr/012-diagrammes-en-mermaid.md)

**« Qu'est-ce qui ne tiendrait pas avec plusieurs instances ? »**

- Le compteur de tentatives de connexion est en mémoire.
- La prise des tâches IA n'est pas distribuée, même si le verrou optimiste empêche déjà deux exécuteurs de prendre la même tâche.

Les deux sont documentés, avec leur point d'évolution. — [architecture §9](architecture.md)

**« Que ferais-tu autrement ? »**

- Écrire tous les diagrammes en Mermaid dès le premier jour.
- Brancher springdoc avec le squelette, en phase 2.
- Faire tourner la pipeline sur GitHub dès la phase 2, plutôt que de la valider localement : un environnement sans accès aux registres a repoussé la vérification réelle jusqu'à la fin.

**« Pourquoi les Reactive Forms et pas les Signal Forms, s'ils sont plus modernes ? »** Choix délibéré pour coller aux projets existants ; un essai des Signal Forms sur un formulaire isolé est noté pour après J4. — [roadmap §10](roadmap.md)
