export type Role = 'ADMIN' | 'TRAINER' | 'TRAINEE' | 'COMPANY';

export const ROLES: Role[] = ['ADMIN', 'TRAINER', 'TRAINEE', 'COMPANY'];

export interface AppUser {
  id: number;
  username: string;
  email: string;
  role: Role;
}
