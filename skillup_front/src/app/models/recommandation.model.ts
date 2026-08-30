import { Niveau } from './formation.model';

/** Une formation suggérée par le service de recommandation (API Python). */
export interface FormationSuggeree {
  formation_id: number;
  titre: string;
  categorie: string;
  niveau: Niveau;
  score: number;
}

export interface Suggestions {
  user_id: number;
  /** « hybride » quand l'apprenant a un historique, « populaire » sinon. */
  methode: string;
  groupe: number | null;
  deja_suivies: number;
  suggestions: FormationSuggeree[];
}
