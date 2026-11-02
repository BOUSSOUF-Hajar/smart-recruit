# ADR-010 — Ollama appelé par `RestClient` derrière `AIService` ; modèle choisi par test comparatif

Statut : acceptée, modèle **provisoire** · Décidée le 19 octobre 2026 · Sources : [architecture §6](../architecture.md), [US-40](../stories/US-40.md), [banc d'essai](../ai-model-benchmark.md)

## Contexte

L'IA doit être gratuite, garder les données des candidats dans l'infrastructure, et rester **facultative** : l'application doit fonctionner entièrement sans elle (RG-28). Le choix du modèle doit pouvoir se justifier par une mesure.

## Décision

- **Interface `AIService`** dans le domaine du module `ai` ; l'implémentation est choisie par configuration (`enabled`, `provider`) : `OllamaAIService`, ou `DisabledAIService` quand l'IA est coupée. Un fournisseur inconnu fait échouer le démarrage.
- **Adaptateur Ollama** : `RestClient` de Spring vers `POST /api/chat`, avec le schéma JSON passé dans le paramètre `format`, et une température de 0 pour l'extraction. Délai de 120 s, une seule nouvelle tentative si la réponse est hors schéma ; le backend revalide toujours la réponse (Bean Validation).
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
- **−** Un modèle de 7 à 9 milliards de paramètres demande de la mémoire : 16 Go de RAM sont recommandés sur le poste (cahier des charges F.3).

## Ce qui reste ouvert

- Le modèle par défaut, `qwen3.5:9b`, est **provisoire** : le banc d'essai (`backend/scripts/ai-benchmark/`) n'a pas encore pu tourner faute de poste équipé. Tant qu'il n'a pas tourné, l'image Docker d'Ollama n'est pas épinglée non plus (`OLLAMA_VERSION`, `latest` par défaut).
- Une fois le banc d'essai exécuté, cette fiche sera mise à jour avec le modèle retenu, sa version exacte et les mesures.
