import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormationService } from '../../services/formation.service';
import { CategorieService } from '../../services/categorie.service';
import { Formation, Niveau } from '../../models/formation.model';
import { Categorie } from '../../models/categorie.model';

@Component({
  selector: 'app-formation-form',
  templateUrl: './formation-form.component.html'
})
export class FormationFormComponent implements OnInit {
  formation: Formation = {
    titre: '',
    description: '',
    prix: 0,
    niveau: 'DEBUTANT',
    categorieId: 0
  };
  categories: Categorie[] = [];
  niveaux: Niveau[] = ['DEBUTANT', 'INTERMEDIAIRE', 'AVANCE'];
  editId: number | null = null;
  error = '';

  constructor(
    private formationService: FormationService,
    private categorieService: CategorieService,
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
    }
  }

  submit(): void {
    this.error = '';
    const request$ = this.editId
      ? this.formationService.update(this.editId, this.formation)
      : this.formationService.create(this.formation);

    request$.subscribe({
      next: () => this.router.navigate(['/formations']),
      error: (err) => (this.error = err?.error?.message || 'Save failed')
    });
  }
}
