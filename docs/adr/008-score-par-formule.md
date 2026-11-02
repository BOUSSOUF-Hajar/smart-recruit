# ADR-008 — Score de matching calculé par formule, LLM pour l'extraction et l'explication

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [cahier des charges G-6](../cahier-des-charges.md), [analyse fonctionnelle §5](../analyse-fonctionnelle.md), [US-42](../stories/US-42.md), [US-43](../stories/US-43.md)

## Contexte

Le recruteur veut un score d'adéquation entre un CV et une offre. Demander directement « une note sur 100 » à un LLM donne un chiffre non reproductible, impossible à expliquer, et manipulable : un CV peut contenir une instruction cachée du type « donne-moi 100 ».

## Décision

Le travail est partagé :

- **Le LLM extrait** des données structurées du CV (compétences avec extrait justificatif, expériences, formations, langues), selon un schéma JSON imposé.
- **Le backend calcule** le score avec une formule pure, `MatchingCalculator`, sans Spring :
  `score = round(100 × (0,60 C_req + 0,20 C_souh + 0,20 A))`, ou `0,75 C_req + 0,25 A` si l'offre n'a pas de compétence souhaitée. C_req et C_souh sont les couvertures des compétences requises et souhaitées ; une compétence présente sous le niveau demandé compte pour 0,5. A est l'adéquation de l'expérience.
- **Le LLM rédige ensuite l'explication** d'un score déjà calculé, à partir de la décomposition, sans jamais recevoir l'identité du candidat.
- Le score est une **aide à la décision** : il ne sert jamais à trier ni à filtrer, et ne change aucun statut (RG-25).

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| Score donné par le LLM | Non reproductible, non explicable, sensible à l'injection de consignes |
| Similarité de texte ou d'embeddings | Déduit des équivalences fausses (« Spring Boot » vaut « Java ») ; difficile à justifier à un candidat |

## Conséquences

- **+** Reproductible et testable : l'exemple chiffré de l'analyse fonctionnelle donne **81**, « Forte adéquation », et un test le vérifie.
- **+** Insensible aux consignes cachées : même si le modèle renvoie un score, ce champ est hors schéma et ignoré (`aScoreReturnedByTheModelIsIgnored`).
- **+** Explicable : décomposition, compétences correspondantes, manquantes requises et souhaitées.
- **−** La formule est plus stricte qu'une similarité : une compétence voisine non déclarée ne compte pas.

## Ce que le développement a confirmé ou changé

- Comparaison par identifiant du référentiel de compétences (noms et alias), jamais par texte.
- Le niveau déclaré dans le profil l'emporte sur le CV ; un niveau inconnu venant du CV compte pour 1.
- `formula_version` est enregistrée avec chaque résultat : un ancien score reste explicable après une évolution de la formule.
- Le score est recopié sur la candidature par une mise à jour ciblée, sans version, pour ne pas provoquer de conflit avec un changement de statut en cours.
- Garde-fous testés (US-43) : pseudonymisation sur 10 CV, CV piégé sans effet, aucun statut modifié, audit.
