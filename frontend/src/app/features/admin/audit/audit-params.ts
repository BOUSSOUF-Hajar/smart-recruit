/** Filtres du journal tels qu'ils vivent dans l'URL (dates locales au format yyyy-MM-dd). */
export interface AuditFilters {
  from?: string;
  to?: string;
  action?: string;
  result?: string;
  actorId?: string;
  page?: string;
}

/** Début du jour local `yyyy-MM-dd`, en instant ISO (RG-32 : l'API ne parle qu'en UTC). */
export function startOfLocalDay(date: string, plusDays = 0): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day + plusDays).toISOString();
}

/**
 * Paramètres de l'API : `from` au début du premier jour, `to` (exclu) au début du lendemain du
 * dernier jour, pour que « du 1er au 3 » inclue toute la journée du 3 dans le fuseau de l'admin.
 */
export function auditParams(filters: AuditFilters): Record<string, string> {
  const params: Record<string, string> = { size: '50' };
  if (filters.from) params['from'] = startOfLocalDay(filters.from);
  if (filters.to) params['to'] = startOfLocalDay(filters.to, 1);
  if (filters.action) params['action'] = filters.action;
  if (filters.result) params['result'] = filters.result;
  if (filters.actorId) params['actorId'] = filters.actorId;
  if (filters.page) params['page'] = filters.page;
  return params;
}
