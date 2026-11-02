# ADR-004 — Frontend et API sur la même origine via Nginx

Statut : acceptée · Décidée le 19 octobre 2026 · Sources : [architecture §5](../architecture.md), [US-54](../stories/US-54.md)

## Contexte

L'application Angular et l'API Spring Boot sont deux artefacts distincts. Servies sur deux domaines, elles imposeraient CORS, un cookie de session `SameSite=None` et une CSP plus large.

## Décision

Un seul Nginx, seul conteneur exposé, sert les fichiers d'Angular sur `/` et relaie `/api/` vers le backend sur le réseau Docker interne. Pour le navigateur, tout vient de la même origine. Nginx ajoute aussi les en-têtes de sécurité et limite la taille des requêtes.

## Alternatives écartées

| Alternative | Pourquoi pas |
| --- | --- |
| Deux domaines et CORS | Cookie de session affaibli (`SameSite=None`), configuration CORS à maintenir en production |
| Spring Boot sert aussi les fichiers Angular | Couple les deux cycles de livraison ; Nginx sert les fichiers statiques bien plus efficacement que la JVM |

## Conséquences

- **+** Le cookie du refresh token reste `SameSite=Strict` ([ADR-003](003-jwt-et-refresh-token.md)).
- **+** Aucune configuration CORS en production : elle n'existe que dans le profil `dev`, où Angular tourne sur le port 4200.
- **+** La CSP peut se limiter à `'self'`.
- **−** Un composant de plus à configurer et à tester : en-têtes répétés dans chaque `location`, retour à `index.html` pour les routes Angular, délais du relais.

## Ce que le développement a confirmé ou changé

- Image `nginx-unprivileged` : Nginx tourne sous un utilisateur non root et écoute sur **8080** au lieu de 80 et 443.
- **HTTPS est terminé en amont en production** : le `docker-compose.yml` livré vise l'essai local, en HTTP. HSTS est prêt, en commentaire dans `security-headers.conf`, à activer là où TLS est terminé.
- CSP effective : `script-src 'self'` sans script en ligne (`inlineCritical` désactivé au build). `style-src` garde `'unsafe-inline'`, car Angular injecte les styles des composants dans des balises `<style>`. Les tests Playwright (US-57) échouent si une violation CSP apparaît.
- `client_max_body_size 6m` : un CV de 5 Mo plus les en-têtes multipart, refusé au-delà avant d'atteindre le backend.
- Health check du conteneur sur `/healthz`, qui ne dépend pas du backend.
