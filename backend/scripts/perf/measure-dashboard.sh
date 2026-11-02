#!/usr/bin/env bash
# Mesure des requêtes du tableau de bord recruteur (US-44 : moins d'une seconde) sur le volume de
# démonstration. Exécute chaque requête de DashboardQueries 20 fois et affiche le temps médian
# d'exécution côté PostgreSQL (EXPLAIN ANALYZE), puis le total des six requêtes.
# Usage : PGHOST=localhost PGUSER=smartrecruit PGDATABASE=smartrecruit scripts/perf/measure-dashboard.sh
set -euo pipefail

COMPANY=$(psql -tAc "select id from company where name = 'Mesure Performance'")
declare -A QUERIES=(
  [jobsByStatus]="SELECT status, count(*) FROM job_offer WHERE company_id = '$COMPANY' GROUP BY status"
  [applicationCounts]="SELECT count(*) FILTER (WHERE a.submitted_at >= now() - interval '7 days'), count(*) FILTER (WHERE a.submitted_at >= now() - interval '30 days'), count(*) FILTER (WHERE a.status = 'SCREENING'), count(*) FILTER (WHERE a.status = 'SHORTLISTED') FROM application a JOIN job_offer j ON j.id = a.job_offer_id WHERE j.company_id = '$COMPANY'"
  [hires]="SELECT count(*) FROM application_event e JOIN application a ON a.id = e.application_id JOIN job_offer j ON j.id = a.job_offer_id WHERE j.company_id = '$COMPANY' AND e.event_type = 'STATUS_CHANGED' AND e.to_status = 'HIRED' AND e.occurred_at >= now() - interval '30 days'"
  [upcomingInterviews]="SELECT i.id, i.starts_at, u.first_name, j.title FROM interview i JOIN application a ON a.id = i.application_id JOIN app_user u ON u.id = a.candidate_id JOIN job_offer j ON j.id = i.job_offer_id WHERE i.company_id = '$COMPANY' AND i.status = 'SCHEDULED' AND i.starts_at >= now() AND i.starts_at < now() + interval '7 days' ORDER BY i.starts_at, i.id LIMIT 10"
  [perJob]="WITH jobs AS (SELECT id, title, status, updated_at FROM job_offer WHERE company_id = '$COMPANY' AND status <> 'ARCHIVED' ORDER BY updated_at DESC, id DESC LIMIT 50) SELECT j.id, a.status, count(a.id) FROM jobs j LEFT JOIN application a ON a.job_offer_id = j.id GROUP BY j.id, j.title, j.status, j.updated_at, a.status"
)
total=0
for name in jobsByStatus applicationCounts hires upcomingInterviews perJob; do
  times=()
  for _ in $(seq 20); do
    times+=("$(psql -tAc "EXPLAIN (ANALYZE, FORMAT JSON) ${QUERIES[$name]}" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["Execution Time"])')")
  done
  median=$(printf '%s\n' "${times[@]}" | sort -g | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}')
  total=$(python3 -c "print(round($total + $median, 2))")
  printf '%-20s médiane %8.2f ms\n' "$name" "$median"
done
echo "Total des requêtes (médianes) : $total ms"
