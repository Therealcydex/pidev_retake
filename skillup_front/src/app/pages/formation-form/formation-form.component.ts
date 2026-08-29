import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormationService } from '../../services/formation.service';
import { CategorieService } from '../../services/categorie.service';
import { ChapitreService } from '../../services/chapitre.service';
import { Formation, Niveau } from '../../models/formation.model';
import { Categorie } from '../../models/categorie.model';
import { Chapitre } from '../../models/chapitre.model';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

@Component({
  selector: 'app-formation-form',
  templateUrl: './formation-form.component.html'
})
export class FormationFormComponent implements OnInit, OnDestroy {
  formation: Formation = {
    titre: '',
    description: '',
    descriptionDetaillee: '',
    niveau: 'DEBUTANT',
    categorieId: 0
  };
  categories: Categorie[] = [];
  niveaux: Niveau[] = ['DEBUTANT', 'INTERMEDIAIRE', 'AVANCE'];
  editId: number | null = null;
  error = '';

  /* ---- image ---- */
  uploading = false;
  imageError = '';
  /**
   * On a new formation there is no id to upload against yet, so the file is held here
   * and sent immediately after the formation is created. `pendingPreview` is a local
   * object URL so the admin still sees the picture before saving.
   */
  pendingFile: File | null = null;
  pendingPreview: string | null = null;

  /* ---- chapters ---- */
  chapitres: Chapitre[] = [];
  newChapitreTitre = '';
  newChapitreContenu = '';
  editingChapitreId: number | null = null;
  editingChapitreTitre = '';
  editingChapitreContenu = '';

  constructor(
    private formationService: FormationService,
    private categorieService: CategorieService,
    private chapitreService: ChapitreService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.categorieService.listAll().subscribe((cats) => {
      this.categories = cats;
      if (cats.length && !this.formation.categorieId) {
        this.formation.categorieId = cats[0].id!;
      }
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId = +id;
      this.formationService.getById(this.editId).subscribe((f) => (this.formation = f));
      this.loadChapitres();
    }
  }

  /* ================= image ================= */

  /** The saved image, or the not-yet-uploaded local file on a new formation. */
  get imageSrc(): string | null {
    if (this.pendingPreview) return this.pendingPreview;
    if (this.editId !== null && this.formation.hasImage) {
      return this.formationService.imageUrl(this.editId, this.formation.imageVersion);
    }
    return null;
  }

  onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.imageError = '';

    if (!/\.(png|jpe?g|webp)$/i.test(file.name)) {
      this.imageError = 'Formats acceptés : PNG, JPG et WebP.';
      input.value = '';
      return;
    }

    // Mirrors spring.servlet.multipart.max-file-size on the Formation service, so an
    // oversized image fails instantly instead of after a round-trip for a 413.
    if (file.size > MAX_IMAGE_BYTES) {
      this.imageError = 'Le fichier dépasse la taille maximale autorisée (5 Mo).';
      input.value = '';
      return;
    }

    // No formation id yet — keep the file and show it locally until save.
    if (this.editId === null) {
      this.setPending(file);
      input.value = '';
      return;
    }

    this.uploading = true;
    this.formationService.uploadImage(this.editId, file).subscribe({
      next: (updated) => {
        this.formation = updated;
        this.uploading = false;
        input.value = '';
      },
      error: (err) => {
        this.imageError = err?.error?.message || "Échec du téléversement de l'image.";
        this.uploading = false;
        input.value = '';
      }
    });
  }

  private setPending(file: File): void {
    this.clearPending();
    this.pendingFile = file;
    this.pendingPreview = URL.createObjectURL(file);
  }

  private clearPending(): void {
    if (this.pendingPreview) {
      URL.revokeObjectURL(this.pendingPreview);
    }
    this.pendingFile = null;
    this.pendingPreview = null;
  }

  ngOnDestroy(): void {
    this.clearPending();
  }

  removeImage(): void {
    // A pending file was never uploaded — dropping it is purely local.
    if (this.editId === null || this.pendingFile) {
      this.clearPending();
      return;
    }
    if (!confirm('Supprimer l\'image de cette formation ?')) return;

    this.formationService.deleteImage(this.editId).subscribe({
      next: () => {
        this.formation.hasImage = false;
        this.formation.imageFilename = undefined;
      },
      error: () => (this.imageError = 'Suppression impossible.')
    });
  }

  /* ================= chapters ================= */

  loadChapitres(): void {
    if (this.editId === null) return;
    this.chapitreService.listByFormation(this.editId).subscribe((list) => (this.chapitres = list));
  }

  addChapitre(): void {
    if (this.editId === null || !this.newChapitreTitre.trim()) return;

    this.chapitreService.create({
      titre: this.newChapitreTitre.trim(),
      contenu: this.newChapitreContenu.trim(),
      formationId: this.editId
    }).subscribe(() => {
      this.newChapitreTitre = '';
      this.newChapitreContenu = '';
      this.loadChapitres();
    });
  }

  startEditChapitre(c: Chapitre): void {
    this.editingChapitreId = c.id!;
    this.editingChapitreTitre = c.titre;
    this.editingChapitreContenu = c.contenu || '';
  }

  cancelEditChapitre(): void {
    this.editingChapitreId = null;
  }

  saveChapitre(): void {
    if (this.editingChapitreId === null || this.editId === null) return;
    if (!this.editingChapitreTitre.trim()) return;

    this.chapitreService.update(this.editingChapitreId, {
      titre: this.editingChapitreTitre.trim(),
      contenu: this.editingChapitreContenu.trim(),
      formationId: this.editId
    }).subscribe(() => {
      this.cancelEditChapitre();
      this.loadChapitres();
    });
  }

  deleteChapitre(c: Chapitre): void {
    if (!confirm('Supprimer le chapitre "' + c.titre + '" ?')) return;
    this.chapitreService.delete(c.id!).subscribe(() => this.loadChapitres());
  }

  /* ================= save ================= */

  submit(): void {
    this.error = '';
    const request$ = this.editId
      ? this.formationService.update(this.editId, this.formation)
      : this.formationService.create(this.formation);

    request$.subscribe({
      next: (saved) => {
        // The image could not be uploaded before the formation existed; do it now.
        if (this.pendingFile && saved.id) {
          this.uploading = true;
          this.formationService.uploadImage(saved.id, this.pendingFile).subscribe({
            next: () => {
              this.clearPending();
              this.uploading = false;
              this.router.navigate(['/formations']);
            },
            // The formation itself saved — say what failed rather than losing the save.
            error: (err) => {
              this.uploading = false;
              this.error = (err?.error?.message || "Échec du téléversement de l'image.")
                + ' La formation a bien été enregistrée.';
              this.editId = saved.id!;
              this.formation = saved;
              this.clearPending();
            }
          });
          return;
        }
        this.router.navigate(['/formations']);
      },
      error: (err) => (this.error = err?.error?.message || 'Save failed')
    });
  }

  downloadPdf(): void {
    if (!this.editId) return;
    this.formationService.downloadPdf(this.editId).subscribe((blob) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'formation-' + this.formation.titre + '.pdf';
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }
}
