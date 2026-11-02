# ADR-011 — Énumérations stockées en texte avec contrainte CHECK

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [modèle de données §0](../modele-de-donnees.md)

## Contexte

Une vingtaine de colonnes prennent leurs valeurs dans une liste fermée : statut de candidature, statut d'offre, rôle, type d'entretien, cause de révocation d'un jeton, état d'une tâche IA… Il faut à la fois une base qui refuse une valeur inconnue et un schéma facile à faire évoluer avec Flyway.

## Décision

- En base : `VARCHAR` avec une contrainte `CHECK (colonne IN (...))` qui liste les valeurs.
- En Java : `enum` mappée par `@Enumerated(EnumType.STRING)`.

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| Type `ENUM` de PostgreSQL | Retirer ou renommer une valeur demande de recréer le type ; mapping JPA moins direct |
| `@Enumerated(ORDINAL)` (entier) | Réordonner l'énumération Java corromprait silencieusement les données |
| Table de référence avec clé étrangère | Justifiée pour des listes que l'admin modifie (catégories, compétences), pas pour des statuts figés dans le code |

## Conséquences

- **+** La base refuse une valeur inconnue, même écrite hors de l'application (script SQL, données de démonstration).
- **+** Les données sont lisibles directement en SQL (`status = 'INTERVIEW'`).
- **+** Ajouter une valeur revient à écrire une migration qui remplace la contrainte, sans toucher aux données.
- **−** Deux sources à garder alignées, l'énumération Java et la contrainte : un test d'intégration sur PostgreSQL réel (Testcontainers) le révèle si elles divergent.

## Ce que le développement a confirmé ou changé

- La migration V8 a ajouté `refresh_token.revocation_reason` de cette manière, avec en plus une contrainte qui lie la cause à `revoked_at` ([ADR-003](003-jwt-et-refresh-token.md)).
