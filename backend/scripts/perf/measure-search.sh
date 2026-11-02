#!/usr/bin/env bash
# Mesure la recherche publique d'offres (critère de US-25 : 95 % des réponses sous 800 ms).
# Usage : ./scripts/perf/measure-search.sh [URL de l'API] [nombre de requêtes]
# Prérequis : l'API tourne et seed-2000-jobs.sql a été appliqué.
set -euo pipefail
API="${1:-http://localhost:8080}"
COUNT="${2:-200}"
QUERIES=(
  "q=developpeur"
  "q=développeur&city=Casablanca"
  "workMode=REMOTE,HYBRID&contractType=CDI"
  "experienceLevel=SENIOR,LEAD&salaryMin=20000"
  "q=data&city=Rabat&workMode=REMOTE"
  "city=Tanger&contractType=CDI,CDD&sort=salaryMax,desc"
  "q=ingenieur&experienceLevel=MID&page=2"
  ""
)
TIMES=$(mktemp)
for ((i = 0; i < COUNT; i++)); do
  query="${QUERIES[$((i % ${#QUERIES[@]}))]}"
  curl -s -o /dev/null -w "%{time_total}\n" "$API/api/v1/jobs?$query" >> "$TIMES"
done
sort -n "$TIMES" | awk -v n="$COUNT" '
  { t[NR] = $1 * 1000 }
  END {
    p50 = t[int(n * 0.50)]; p95 = t[int(n * 0.95)]; p99 = t[int(n * 0.99)];
    printf "requêtes : %d\nmédiane : %.1f ms\np95 : %.1f ms\np99 : %.1f ms\nmax : %.1f ms\n", n, p50, p95, p99, t[n];
    if (p95 < 800) print "OK : p95 sous 800 ms"; else print "ÉCHEC : p95 au-dessus de 800 ms";
  }'
rm -f "$TIMES"
