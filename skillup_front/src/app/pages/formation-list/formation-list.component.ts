import { Component, OnInit } from '@angular/core';
import { FormationService } from '../../services/formation.service';
import { AuthService } from '../../services/auth.service';
import { InscriptionService } from '../../services/inscription.service';
import { Formation, Niveau } from '../../models/formation.model';

@Component({
  selector: 'app-formation-list',
  templateUrl: './formation-list.component.html'
})
export class FormationListComponent implements OnInit {
  formations: Formation[] = [];
  loading = false;

  search = '';
  categorieFilter = '';
  niveauFilter = '';
  sortAsc = true;

  readonly niveaux: Niveau[] = ['DEBUTANT', 'INTERMEDIAIRE', 'AVANCE'];

  constructor(
    private formationService: FormationService,
    private auth: AuthService,
    private inscriptions: InscriptionService
  ) {}

  /** Formation ids this trainee is enrolled in, fetched once for the whole grid. */
  enrolledIds = new Set<number>();
  enrollError = '';

  /** Enrolling is a trainer action. */
  get isTrainer(): boolean {
    return this.auth.getUser()?.role === 'TRAINER';
  }

  ngOnInit(): void {
    this.load();
    if (this.isTrainer) {
      this.loadEnrolments();
    }
  }

  private loadEnrolments(): void {
    this.inscriptions.myFormationIds().subscribe({
      next: (ids) => (this.enrolledIds = new Set(ids)),
      error: () => (this.enrolledIds = new Set())
    });
  }

  isEnrolled(f: Formation): boolean {
    return this.enrolledIds.has(f.id!);
  }

  toggleEnrolment(f: Formation): void {
    this.enrollError = '';
    const done = () => this.loadEnrolments();
    const fail = (err: any) =>
      (this.enrollError = err?.error?.message || "L'inscription a échoué.");

    if (this.isEnrolled(f)) {
      this.inscriptions.unenroll(f.id!).subscribe({ next: done, error: fail });
    } else {
      this.inscriptions.enroll(f.id!).subscribe({ next: done, error: fail });
    }
  }

  load(): void {
    this.loading = true;
    this.formationService.listAll().subscribe({
      next: (data) => {
        this.formations = data;
        this.loading = false;
      },
      error: () => (this.loading = false)
    });
  }

  /** Distinct category names present in the catalogue, for the filter dropdown. */
  get categories(): string[] {
    const names = this.formations
      .map((f) => f.categorieNom)
      .filter((n): n is string => !!n);
    return Array.from(new Set(names)).sort();
  }

  get categorieCount(): number {
    return this.categories.length;
  }

  get filtered(): Formation[] {
    const term = this.search.trim().toLowerCase();

    return this.formations
      .filter((f) => !term || f.titre.toLowerCase().includes(term))
      .filter((f) => !this.categorieFilter || f.categorieNom === this.categorieFilter)
      .filter((f) => !this.niveauFilter || f.niveau === this.niveauFilter)
      .sort((a, b) =>
        this.sortAsc ? a.titre.localeCompare(b.titre) : b.titre.localeCompare(a.titre));
  }

  toggleSort(): void {
    this.sortAsc = !this.sortAsc;
  }

  /** The design labels levels in sentence case French rather than the enum constant. */
  niveauLabel(niveau: Niveau): string {
    const labels: Record<Niveau, string> = {
      DEBUTANT: 'Débutant',
      INTERMEDIAIRE: 'Intermédiaire',
      AVANCE: 'Avancé'
    };
    return labels[niveau] || niveau;
  }

  /**
   * Placeholder cover colour for a formation with no image, from the palette the design
   * uses. Keyed on the id so a card keeps the same colour between loads.
   */
  coverColor(f: Formation): string {
    const palette = ['#334155', '#1e3a8a', '#0f766e', '#7c2d12', '#3f3f46', '#4c1d95'];
    return palette[(f.id ?? 0) % palette.length];
  }

  /**
   * Creating, editing and deleting a formation is open to admins and trainers —
   * mirrors staffGuard on the form route, so the catalogue never offers a link that
   * would bounce the user.
   */
  get canManage(): boolean {
    const role = this.auth.getUser()?.role;
    return role === 'ADMIN' || role === 'TRAINER';
  }

  imageSrc(f: Formation): string {
    return this.formationService.imageUrl(f.id!, f.imageVersion);
  }

  delete(id: number): void {
    if (!confirm('Delete this formation?')) return;
    this.formationService.delete(id).subscribe(() => this.load());
  }

  downloadPdf(id: number, titre: string): void {
    this.formationService.downloadPdf(id).subscribe((blob) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'formation-' + titre + '.pdf';
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }
}
