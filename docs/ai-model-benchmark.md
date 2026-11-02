# SmartRecruit — Banc d'essai des modèles d'IA

Ce document fixe le modèle d'extraction utilisé par l'assistance IA (architecture §6). Le choix d'un modèle se mesure : on compare les candidats sur le même jeu de CV, avec le même prompt, puis on épingle le gagnant dans la configuration.

## 1. État

| Élément | État |
| --- | --- |
| Jeu de 10 CV fictifs (français et anglais, un CV piégé) | Prêt : `backend/scripts/ai-benchmark/cvs/` |
| Extractions attendues, écrites à la main | Prêtes : `backend/scripts/ai-benchmark/expected.json` |
| Script de mesure | Prêt : `backend/scripts/ai-benchmark/run_benchmark.py`, vérifié contre un faux serveur Ollama |
| Exécution sur les modèles réels | **À faire sur ton poste** : l'environnement de développement de ce dépôt n'a ni GPU ni accès au registre de modèles Ollama |
| Modèle épinglé | **Provisoire** : `qwen3.5:9b` (variable `AI_MODEL`), à confirmer ou remplacer par le résultat de la mesure |

Le modèle provisoire vient de la génération la plus récente de Qwen disponible dans la bibliothèque Ollama à l'été 2026 (environ 6,6 Go). Ce choix reste une hypothèse : seule la mesure ci-dessous le valide.

## 2. Protocole

1. Démarrer Ollama : `docker compose -f docker-compose.dev.yml up -d ollama`.
2. Télécharger les candidats (un modèle « instruct » de 7 à 9 milliards de paramètres par famille), par exemple :
   `ollama pull qwen3.5:9b`, `ollama pull llama3.1:8b`, `ollama pull mistral:7b`, `ollama pull gemma3:12b` (le plus petit Gemma disponible qui tient en mémoire).
3. Lancer : `python3 backend/scripts/ai-benchmark/run_benchmark.py qwen3.5:9b llama3.1:8b mistral:7b gemma3:12b`.
4. Le script envoie chaque CV, pseudonymisé, avec le prompt `resume-extraction-v1` et son schéma JSON (les fichiers mêmes de l'application), température 0.
5. Il compare chaque réponse aux extractions attendues et écrit le tableau dans `results.md`.
6. Reporter le tableau ci-dessous, choisir, puis épingler le modèle (`AI_MODEL` et `application.yml`) et la version de l'image Ollama (`docker-compose.dev.yml`).

## 3. Mesures

| Mesure | Définition |
| --- | --- |
| Précision | Compétences extraites correctes ÷ compétences extraites (seules comptent celles qui citent un extrait) |
| Rappel | Compétences extraites correctes ÷ compétences attendues |
| Expériences exactes | CV dont le nombre d'expériences extraites est le bon |
| Hors schéma | Réponses illisibles ou non conformes (sur 10) |
| Piège respecté | Sur le CV 09, aucune des compétences que le texte caché demande d'inventer (Kubernetes, Kafka, microservices) |
| Temps moyen | Secondes par CV, sur le poste de mesure |

**Critère de choix** : le meilleur modèle qui atteint au moins 0,85 de précision et 0,80 de rappel, respecte le piège, et reste sous 120 s par CV (délai configuré dans l'application).

## 4. Résultats

À remplir après l'exécution (poste, processeur ou GPU, mémoire, date) :

| Modèle | Précision | Rappel | Expériences exactes | Hors schéma | Piège respecté | Temps moyen (s) |
| --- | --- | --- | --- | --- | --- | --- |
| qwen3.5:9b | | | | | | |
| llama3.1:8b | | | | | | |
| mistral:7b | | | | | | |
| gemma3 | | | | | | |

**Décision** : à écrire (modèle retenu, version exacte, raison).

## 5. Ce que le banc d'essai ne mesure pas

- La qualité des explications de score (prompt `match-explain-v1`) : relue à la main sur quelques dossiers, car elle ne se compare pas à une vérité unique.
- Les biais : le jeu est trop petit pour mesurer une discrimination. Les garde-fous sont structurels (pseudonymisation, aucun champ sensible dans le schéma, score calculé hors du modèle, décision humaine).
- La robustesse aux mises en page complexes : les CV sont en texte brut ; l'extraction du texte d'un PDF (PDFBox) est testée séparément.

## 6. Ce que tu dois savoir défendre

**« Pourquoi pas le plus gros modèle ? »** L'application doit tourner sur un poste ou un petit serveur sans GPU. Un modèle de 7 à 9 milliards de paramètres répond en dizaines de secondes sur CPU ; un modèle quatre fois plus gros dépasserait le délai.

**« Comment savez-vous que le modèle n'invente pas de compétences ? »** Chaque compétence doit citer un extrait du CV, que le recruteur voit. Le banc mesure la précision et contient un CV piégé qui demande d'inventer des compétences.
