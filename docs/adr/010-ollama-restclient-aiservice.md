# ADR-010 — Ollama appelé par `RestClient` derrière `AIService` ; modèle choisi par test comparatif

Statut : acceptée · Décidée le 19 octobre 2026, modèle épinglé le 4 novembre 2026 · Sources : [architecture §6](../architecture.md), [US-40](../stories/US-40.md), [banc d'essai](../ai-model-benchmark.md)

## Contexte

L'IA doit être gratuite, garder les données des candidats dans l'infrastructure, et rester **facultative** : l'application doit fonctionner entièrement sans elle (RG-28). Le choix du modèle doit pouvoir se justifier par une mesure.

## Décision

- **Interface `AIService`** dans le domaine du module `ai` ; l'implémentation est choisie par configuration (`enabled`, `provider`) : `OllamaAIService`, ou `DisabledAIService` quand l'IA est coupée. Un fournisseur inconnu fait échouer le démarrage.
- **Adaptateur Ollama** : `RestClient` de Spring vers `POST /api/chat`, avec le schéma JSON passé dans le paramètre `format`, et une température de 0 pour l'extraction. Délai de lecture de 180 s (objectif 120 s, voir plus bas), une seule nouvelle tentative si la réponse est hors schéma ; le backend revalide toujours la réponse (Bean Validation).
- **Prompts versionnés** dans les ressources (`resume-extraction-v1`), version enregistrée avec chaque résultat (RG-27).
- **Santé** : un indicateur Actuator interroge Ollama et renvoie `UNKNOWN`, et non `DOWN`, quand il manque. L'application reste « UP ».
- **Modèle choisi par banc d'essai** : 10 CV fictifs, extraction attendue écrite à la main, précision et rappel des compétences, temps et mémoire. Seuils : précision ≥ 0,85, rappel ≥ 0,80, moins de 120 s par CV.

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| API cloud payante | Coût, et données de candidats envoyées à un tiers |
| Spring AI | Une seconde abstraction au-dessus d'`AIService`, qui masque l'appel réel ; bonne évolution possible |
| Profils Spring pour choisir l'implémentation | Le même binaire doit tourner partout ; la configuration suffit |
| Modèle figé sans mesure | Indéfendable en entretien |

## Conséquences

- **+** L'adaptateur est testé contre un faux serveur HTTP (le `HttpServer` du JDK) : format des requêtes, nouvelle tentative, délais et pannes, sans modèle ni GPU.
- **+** Changer de fournisseur, par exemple vers une API cloud, revient à écrire une implémentation d'`AIService` ; la pseudonymisation est déjà en place.
- **−** Un modèle local demande de la mémoire et du processeur : 16 Go de RAM sont recommandés sur le poste (cahier des charges F.3), et un portable à 2 cœurs reste trop lent pour le modèle retenu.

## Résultat du banc d'essai (4 novembre 2026)

Mesuré sur GitHub Actions (4 vCPU, sans GPU), détail dans [le banc d'essai](../ai-model-benchmark.md) :

- **Modèle retenu : `qwen3.5:4b`**, précision 0,98, rappel 0,93, piège respecté, 94 s par CV. `qwen3.5:9b` dépasse 120 s sur chaque CV ; `gemma3:4b` et `llama3.2:3b` obéissent à l'instruction cachée du CV piégé. Repli pour une machine lente : `qwen3.5:2b` (précision 0,89, 56 s).
- **Raisonnement coupé** (`think: false`, propriété `smartrecruit.ai.ollama.think`) : avec lui, aucun CV n'aboutit en 300 s.
- **Délai de lecture porté à 180 s** : l'objectif reste 120 s (ENF-PERF-05), mais sur un processeur plus ancien 4 CV sur 10 dépassaient 120 s ; l'analyse étant asynchrone, mieux vaut une analyse lente qu'un échec.
- **Image épinglée** : `ollama/ollama:0.40.14` (`OLLAMA_VERSION`).
