# ADR-005 — Clés primaires UUID v7

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [modèle de données §0](../modele-de-donnees.md)

## Contexte

Les identifiants apparaissent dans les URL (`/applications/{id}`). Un entier séquentiel est devinable et révèle le volume (« candidature 1 042 »). Un UUID v4 aléatoire résout ce problème, mais disperse les insertions dans l'index B-tree.

## Décision

Toutes les clés primaires sont des `UUID` **version 7** : 48 bits d'horodatage suivis de bits aléatoires.

- En base : `id UUID DEFAULT uuidv7()`, fonction native depuis PostgreSQL 18.
- Dans les entités JPA : `@GeneratedValue` avec `@UuidGenerator(style = VERSION_7)` de Hibernate, pour que l'identifiant existe avant l'insertion (égalité fondée sur l'identifiant, événements de domaine).

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| `BIGSERIAL` | Devinable, révèle le volume ; facilite l'énumération des ressources |
| UUID v4 | Insertions dispersées dans l'index, pages fragmentées |
| ULID ou Snowflake | Même idée que l'UUID v7, mais sans type ni fonction natifs dans PostgreSQL |

## Conséquences

- **+** Identifiants non devinables, sans fuite de volume, mais ordonnés dans le temps : insertions en fin d'index.
- **+** Un identifiant connu côté Java avant l'insertion simplifie `equals`/`hashCode` et la publication d'événements.
- **−** 16 octets au lieu de 8 ; négligeable au volume visé.
- **−** L'horodatage est lisible dans l'identifiant : on peut déduire la date de création d'une ressource. Ce n'est pas un secret dans ce domaine.
- **−** Un identifiant non devinable n'est pas un contrôle d'accès : chaque service vérifie quand même la propriété (404 si la ressource appartient à une autre entreprise).
