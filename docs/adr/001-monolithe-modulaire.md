# ADR-001 — Monolithe modulaire vérifié par Spring Modulith

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [architecture §0, §3](../architecture.md), [architecture backend §3](../architecture-backend.md)

## Contexte

Une seule développeuse, un seul domaine métier (le recrutement), un volume modeste. Le projet doit pourtant montrer qu'on sait structurer une application qui pourrait grandir : un monolithe où n'importe quelle classe appelle n'importe quelle autre deviendrait vite impossible à découper.

## Décision

Une seule application Spring Boot, découpée en modules dont les frontières sont vérifiées par un test :

- chaque module expose une API publique dans un sous-paquet `api`, déclaré `@NamedInterface("api")` dans son `package-info.java` ; tout le reste est interne ;
- aucun module n'accède aux entités ni aux dépôts d'un autre ; les agrégats se référencent par identifiant ([ADR-006](006-references-par-identifiant.md)) ;
- quand deux modules ont besoin l'un de l'autre, le plus « haut » déclare une interface que l'autre implémente (inversion de dépendance, par exemple `CompletedInterviewChecker`) ;
- les effets de bord passent par des événements de domaine (`ApplicationStatusChanged`, `CompanyDeactivated`…) ;
- `ApplicationModules.of(...).verify()` échoue au build si une règle est violée ou si un cycle apparaît.

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| Microservices | Réseau, déploiements multiples et cohérence distribuée, sans équipe ni charge qui le justifie |
| Monolithe en couches classique | Aucune frontière entre domaines : découpage futur impossible, dépendances invisibles |
| Modules Maven séparés | Frontières vérifiées par le compilateur, mais builds plus lourds et pas de vérification des événements ni des cycles fonctionnels |
| ArchUnit seul | Possible, mais il faut écrire soi-même les règles que Spring Modulith fournit déjà |

## Conséquences

- **+** Un seul déploiement, une seule base, des transactions locales quand plusieurs modules sont concernés (RG-31).
- **+** Les frontières sont vérifiées à chaque build et se voient dans le code (`api` contre le reste).
- **+** Chaque module est un candidat naturel à l'extraction si une mesure le justifie (architecture §9).
- **−** Deux exceptions assumées et documentées : le module `reporting` et `RecruiterApplicationSearch` (US-30) lisent en SQL des tables d'autres modules, pour éviter de charger des agrégats entiers juste pour compter ou filtrer.
- **−** Discipline nécessaire : un raccourci « juste pour cette fois » casse le build, ce qui est voulu mais demande de concevoir l'interface proprement.

## Ce que le développement a confirmé ou changé

- Le backend compte **12 modules** : 9 modules métier (`identity`, `audit`, `company`, `referential`, `candidate`, `job`, `application`, `interview`, `ai`), le noyau `shared` (module ouvert), `reporting` (lecture seule) et `demo` (données de démonstration, US-56, qui n'est appelé par personne et n'appelle aucun service métier).
- Spring Modulith 2.1.0, sorti avec Spring Boot 4.1, a été utilisé sans difficulté.
- Le cycle entre candidatures et entretiens a été cassé par `CompletedInterviewChecker`, déclaré par `application` et implémenté par `interview` (US-32, puis phase 7).
