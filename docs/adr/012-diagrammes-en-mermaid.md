# ADR-012 — Diagrammes en Mermaid versionnés avec le code

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [analyse fonctionnelle §0](../analyse-fonctionnelle.md)

## Contexte

Le projet s'appuie sur une vingtaine de diagrammes : cas d'utilisation, séquences, machines à états, classes, modules, C4. Des images exportées d'un outil de dessin vieillissent sans qu'on le voie, et personne ne les met à jour dans une pull request.

## Décision

Les diagrammes sont écrits en **Mermaid**, directement dans les fichiers Markdown de `docs/`. GitHub les affiche tels quels, et ils se relisent et se modifient comme du code, dans la même pull request que la fonctionnalité.

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| Images exportées (draw.io, Lucidchart) | Source séparée du dépôt, diff illisible, vite périmées |
| PlantUML | Demande un rendu externe : GitHub ne l'affiche pas nativement |
| Structurizr (C4 « as code ») | Très bon pour le C4, mais un outil de plus pour une poignée de diagrammes |

## Conséquences

- **+** Un diagramme faux se corrige dans la même pull request que le code.
- **+** Aucune dépendance à un outil propriétaire.
- **−** Mermaid limite la mise en page : les grands diagrammes demandent d'être découpés.

## Ce que le développement a confirmé ou changé

- 17 diagrammes Mermaid sont dans le dépôt : 9 dans l'analyse fonctionnelle, 6 dans le diagramme de classes, 1 dans l'architecture (modules), 1 dans le modèle de données.
- **Écart constaté pendant la phase 12** : 7 diagrammes de la conception d'origine n'ont pas été écrits en Mermaid et ont été perdus à l'export de `docs/`. Il ne reste à leur place qu'un texte de remplacement `[embedded content: …]` :
  - C4 niveaux 1 et 2 ([architecture §1, §2](../architecture.md)) ;
  - modules et clés étrangères ([modèle de données §1](../modele-de-donnees.md)) ;
  - phases et jalons ([roadmap §1](../roadmap.md)) ;
  - dépendances entre épopées ([user stories](../user-stories.md)) ;
  - workflow de candidature ([cahier des charges](../cahier-des-charges.md)) ;
  - cas d'utilisation ([analyse fonctionnelle §1](../analyse-fonctionnelle.md)).

  Ils ont été redessinés en Mermaid pendant US-58, d'après le code quand il s'écartait de la conception : le schéma des modules du modèle de données suit les migrations V1 à V14. La décision s'applique désormais à tous les diagrammes du dépôt (24 au total).
