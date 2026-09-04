import { Component, OnInit } from '@angular/core';
import { FormationService } from '../../services/formation.service';
import { AuthService } from '../../services/auth.service';
import { InscriptionService } from '../../services/inscription.service';
import { RecommandationService } from '../../services/recommandation.service';
import { Formation, Niveau } from '../../models/formation.model';
import { FormationSuggeree } from '../../models/recommandation.model';

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
    private inscriptions: InscriptionService,
    private recommandations: RecommandationService
  ) {}

  /** Formation ids this trainee is enrolled in, fetched once for the whole grid. */
  enrolledIds = new Set<number>();
  enrollError = '';

  /** Enrolling is a trainee action. */
  get isTrainee(): boolean {
    return this.auth.getUser()?.role === 'TRAINEE';
  }

  /* ---- suggestions du service de recommandation ---- */

  suggestions: FormationSuggeree[] = [];
  /** « hybride » (basé sur le profil) ou « populaire » (repli démarrage à froid). */
  methodeSuggestions = '';
  suggestionsVisibles = false;
  chargementSuggestions = false;
  erreurSuggestions = '';

  get libelleMethode(): string {
    return this.methodeSuggestions === 'hybride'
      ? "D'après les formations que vous suivez"
      : 'Les formations les plus suivies';
  }

  ngOnInit(): void {
    this.load();
    if (this.isTrainee) {
      this.loadEnrolments();
    }
  }

  /**
   * Déclenché par l'apprenant. Le chargement n'est pas automatique : l'appel au modèle
   * devient une action visible, et le catalogue reste identique pour qui ne la demande
   * pas.
   */
  chargerSuggestions(): void {
    if (this.suggestionsVisibles) {          // deuxième clic : on referme
      this.suggestionsVisibles = false;
      return;
    }

    const id = this.auth.getUser()?.id;
    if (!id) return;

    this.chargementSuggestions = true;
    this.erreurSuggestions = '';

    this.recommandations.pourApprenant(id, 5).subscribe({
      next: (r) => {
        this.suggestions = r.suggestions;
        this.methodeSuggestions = r.methode;
        this.suggestionsVisibles = true;
        this.chargementSuggestions = false;
      },
      // Le service de recommandation est un service Python distinct. S'il est arrêté,
      // on le dit — l'apprenant a cliqué, il attend une réponse.
      error: () => {
        this.erreurSuggestions =
          "Le service de recommandation n'est pas disponible pour le moment.";
        this.chargementSuggestions = false;
      }
    });
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

    const liste = this.formations
      .filter((f) => !term || f.titre.toLowerCase().includes(term))
      .filter((f) => !this.categorieFilter || f.categorieNom === this.categorieFilter)
      .filter((f) => !this.niveauFilter || f.niveau === this.niveauFilter)
      .sort((a, b) =>
        this.sortAsc ? a.titre.localeCompare(b.titre) : b.titre.localeCompare(a.titre));

    if (!this.suggestionsVisibles || !this.suggestions.length) {
      return liste;
    }

    // Les formations recommandées remontent, dans l'ordre donné par le modèle ; les
    // autres suivent. Le tri de JavaScript est stable, donc le classement par titre
    // ci-dessus est conservé à l'intérieur de chaque bloc.
    const rang = new Map(this.suggestions.map((s, i) => [s.formation_id, i]));
    const dernier = Number.MAX_SAFE_INTEGER;

    return [...liste].sort(
      (a, b) => (rang.get(a.id!) ?? dernier) - (rang.get(b.id!) ?? dernier));
  }

  /** Vrai si la formation fait partie des suggestions actuellement affichées. */
  estSuggeree(f: Formation): boolean {
    return this.suggestionsVisibles
      && this.suggestions.some((s) => s.formation_id === f.id);
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

  /** Card preview: the description is capped at 100, the card shows the first 50. */
  apercu(description: string): string {
    return description.length > 50
      ? description.slice(0, 50).trimEnd() + '…'
      : description;
  }

  /**
   * Placeholder cover colour for a formation with no image, from the palette the design
   * uses. Keyed on the id so a card keeps the same colour between loads.
   */
  coverColor(f: Formation): string {
    const palette = ['#334155', '#1e3a8a', '#0f766e', '#7c2d12', '#3f3f46', '#4c1d95'];
    return palette[(f.id ?? 0) % palette.length];
  }

  /** Any staff member may create; ownership only matters once a formation exists. */
  get canCreate(): boolean {
    const role = this.auth.getUser()?.role;
    return role === 'ADMIN' || role === 'TRAINER';
  }

  /**
   * An admin manages anything; a trainer only what they created. Mirrors
   * FormationAccessService.requireCanEdit on the server, so the catalogue never offers
   * a button the API would refuse.
   */
  canEdit(f: Formation): boolean {
    const u = this.auth.getUser();
    if (u?.role === 'ADMIN') return true;
    return u?.role === 'TRAINER' && f.ownerId != null && f.ownerId === u.id;
  }

  imageSrc(f: Formation): string {
    return this.formationService.imageUrl(f.id!);
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
