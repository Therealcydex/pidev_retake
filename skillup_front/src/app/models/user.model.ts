export type Role = 'ADMIN' | 'TRAINER' | 'TRAINEE';

export const ROLES: Role[] = ['ADMIN', 'TRAINER', 'TRAINEE'];

export interface AppUser {
  id: number;
  username: string;
  email: string;
  role: Role;
}
