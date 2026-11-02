# Frontend SmartRecruit (US-54) : build Angular, puis Nginx non privilégié qui sert les fichiers
# et relaie /api vers le backend.

# --- 1. Build ---------------------------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /build
# Dépendances d'abord : la couche est réutilisée tant que package-lock.json ne change pas
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# --- 2. Exécution -----------------------------------------------------------------------------
# Variante non privilégiée : Nginx tourne sous l'utilisateur nginx (uid 101) et écoute sur 8080
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO /dev/null http://127.0.0.1:8080/healthz || exit 1
