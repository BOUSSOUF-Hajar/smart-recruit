import { CreateStaffRequest, StaffRole } from './admin.api';

export interface StaffFormValue {
  email: string;
  firstName: string;
  lastName: string;
  role: StaffRole;
  companyId: string | null;
  jobTitle: string;
}

/**
 * Corps envoyé à POST /admin/users. L'entreprise et l'intitulé ne concernent que les recruteurs :
 * pour un admin, ils sont retirés même s'ils avaient été saisis avant de changer de rôle
 * (le serveur refuserait un admin rattaché à une entreprise).
 */
export function toStaffRequest(value: StaffFormValue): CreateStaffRequest {
  const recruiter = value.role === 'RECRUITER';
  return {
    email: value.email.trim().toLowerCase(),
    firstName: value.firstName.trim(),
    lastName: value.lastName.trim(),
    role: value.role,
    companyId: recruiter ? value.companyId : null,
    jobTitle: recruiter ? value.jobTitle.trim() || null : null,
  };
}
