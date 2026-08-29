import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormationService } from '../../services/formation.service';
import { ChapitreService } from '../../services/chapitre.service';
import { AuthService } from '../../services/auth.service';
import { Formation, Niveau } from '../../models/formation.model';
import { Chapitre } from '../../models/chapitre.model';

@Component({
  selector: 'app-formation-detail',
  templateUrl: './formation-detail.component.html'
})
export class FormationDetailComponent implements OnInit {
  formation: Formation | null = null;
  chapitres: Chapitre[] = [];
  loading = false;
  notFound = false;

  constructor(
    private route: ActivatedRoute,
    private formationService: FormationService,
    private chapitreService: ChapitreService,
    private auth: AuthService
  ) {}

  ngOnInit(): void {
    // The id travels in the URL (/formations/:id), so the page is linkable and the
    // back button works — that is the point of routing rather than a popup.
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loading = true;

    this.formationService.getById(id).subscribe({
      next: (f) => {
        this.formation = f;
        this.loading = false;
      },
      // getById answers 404 for an unknown id; show a message, not a blank page.
      error: () => {
        this.notFound = true;
        this.loading = false;
      }
    });

    this.chapitreService.listByFormation(id).subscribe({
      next: (list) => (this.chapitres = list),
      error: () => (this.chapitres = [])
    });
  }

  /** Editing stays admin-only, matching adminGuard on the edit route. */
  get canManage(): boolean {
    return this.auth.getUser()?.role === 'ADMIN';
  }

  get imageSrc(): string {
    return this.formationService.imageUrl(this.formation!.id!, this.formation!.imageVersion);
  }

  /** Sentence-case French labels, matching the catalogue cards. */
  niveauLabel(niveau: Niveau): string {
    const labels: Record<Niveau, string> = {
      DEBUTANT: 'Débutant',
      INTERMEDIAIRE: 'Intermédiaire',
      AVANCE: 'Avancé'
    };
    return labels[niveau] || niveau;
  }

  /** Same palette as the catalogue placeholder, keyed on the id for a stable colour. */
  coverColor(f: Formation): string {
    const palette = ['#334155', '#1e3a8a', '#0f766e', '#7c2d12', '#3f3f46', '#4c1d95'];
    return palette[(f.id ?? 0) % palette.length];
  }

  downloadPdf(): void {
    if (!this.formation?.id) return;
    const titre = this.formation.titre;
    this.formationService.downloadPdf(this.formation.id).subscribe((blob) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'formation-' + titre + '.pdf';
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }
}
