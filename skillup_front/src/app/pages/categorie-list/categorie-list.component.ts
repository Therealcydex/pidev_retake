import { Component, OnInit } from '@angular/core';
import { CategorieService } from '../../services/categorie.service';
import { Categorie } from '../../models/categorie.model';

@Component({
  selector: 'app-categorie-list',
  templateUrl: './categorie-list.component.html'
})
export class CategorieListComponent implements OnInit {
  categories: Categorie[] = [];
  newNom = '';
  editingId: number | null = null;
  editingNom = '';

  constructor(private categorieService: CategorieService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.categorieService.listAll().subscribe((data) => (this.categories = data));
  }

  add(): void {
    if (!this.newNom.trim()) return;
    this.categorieService.create({ nom: this.newNom.trim() }).subscribe(() => {
      this.newNom = '';
      this.load();
    });
  }

  startEdit(c: Categorie): void {
    this.editingId = c.id!;
    this.editingNom = c.nom;
  }

  cancelEdit(): void {
    this.editingId = null;
    this.editingNom = '';
  }

  saveEdit(): void {
    if (!this.editingNom.trim() || this.editingId === null) return;
    this.categorieService.update(this.editingId, { nom: this.editingNom.trim() }).subscribe(() => {
      this.cancelEdit();
      this.load();
    });
  }

  delete(id: number): void {
    if (!confirm('Delete this categorie?')) return;
    this.categorieService.delete(id).subscribe(() => this.load());
  }
}
