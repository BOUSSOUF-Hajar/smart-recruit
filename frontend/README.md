# SmartRecruit — frontend

Angular 22, Angular Material 3, tests Vitest. Architecture détaillée : [`../docs/architecture-frontend.md`](../docs/architecture-frontend.md).

| Commande | Effet |
| --- | --- |
| `npm start` | Serveur de développement sur http://localhost:4200 ; `/api` relayé vers http://localhost:8080 |
| `npm run lint` | ESLint, dont les frontières entre `core`, `shared` et `features` |
| `npm run format:check` | Vérification Prettier |
| `npm run test:ci` | Tests unitaires, une seule exécution |
| `npm test` | Tests unitaires en mode surveillance |
| `npm run build` | Build de production dans `dist/` |
