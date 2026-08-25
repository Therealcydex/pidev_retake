export type Niveau = 'DEBUTANT' | 'INTERMEDIAIRE' | 'AVANCE';

export interface Formation {
  id?: number;
  titre: string;
  description: string;
  prix: number;
  niveau: Niveau;
  categorieId: number;
  categorieNom?: string;
}

export interface FormationStats {
  totalFormations: number;
  averagePrix: number;
  minPrix: number;
  maxPrix: number;
  countByCategorie: { [key: string]: number };
  countByNiveau: { [key: string]: number };
}
