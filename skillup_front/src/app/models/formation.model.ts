export type Niveau = 'DEBUTANT' | 'INTERMEDIAIRE' | 'AVANCE';

export interface Formation {
  id?: number;
  titre: string;
  description: string;
  descriptionDetaillee?: string;
  niveau: Niveau;
  categorieId: number;
  categorieNom?: string;
  hasImage?: boolean;
  imageFilename?: string;
  chapitreCount?: number;
  imageVersion?: number;
}

export interface FormationStats {
  totalFormations: number;
  countByCategorie: { [key: string]: number };
  countByNiveau: { [key: string]: number };
}
