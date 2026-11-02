# ADR-006 — Références par identifiant entre agrégats

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [diagramme de classes §0](../diagrams/classes.md), [US-30](../stories/US-30.md)

## Contexte

Les 25 tables deviennent 17 agrégats répartis dans plusieurs modules. Des associations JPA entre modules (`Application` → `JobOffer` → `Company`) créeraient des chargements en chaîne non voulus et lieraient les modules au niveau des entités, ce qui rendrait [ADR-001](001-monolithe-modulaire.md) impossible à respecter.

## Décision

- **À l'intérieur d'un agrégat**, associations JPA classiques : `@OneToMany(cascade = ALL, orphanRemoval = true)`, chargement LAZY (un profil candidat et ses expériences).
- **Entre agrégats**, uniquement l'identifiant : `Application` connaît `jobOfferId` et `candidateId`, pas des objets `JobOffer` ou `CandidateProfile`. La clé étrangère existe en base, mais pas l'association en Java.
- Une transaction modifie un seul agrégat ; les réactions des autres modules passent par des événements.
- Les écrans qui affichent des données de plusieurs agrégats passent par des **requêtes de projection** qui renvoient directement des DTO.

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| Associations JPA partout | Chargements en chaîne, problèmes N+1, modules liés par leurs entités |
| Aucune clé étrangère en base | On perdrait l'intégrité référentielle, qui reste la dernière ligne de défense |

## Conséquences

- **+** Pas de chargement accidentel ; chaque agrégat se charge et se teste seul.
- **+** Les modules restent découpables : seule l'API publique les relie.
- **−** Les listes qui croisent plusieurs modules demandent une requête de lecture dédiée.

## Ce que le développement a confirmé ou changé

- La liste des candidatures du recruteur (US-30) filtre sur des données d'autres modules : nom du candidat, compétences, offre. Elle est devenue une requête SQL de lecture, `RecruiterApplicationSearch`, avec un nombre de requêtes fixe quelle que soit la taille de la page. C'est l'une des deux exceptions documentées aux frontières, avec le module `reporting`.
