import { Component, OnInit } from '@angular/core';
import { FormationService } from '../../services/formation.service';
import { Formation } from '../../models/formation.model';

@Component({
  selector: 'app-formation-list',
  templateUrl: './formation-list.component.html'
})
export class FormationListComponent implements OnInit {
  formations: Formation[] = [];
  loading = false;

  constructor(private formationService: FormationService) {}

  ngOnInit(): void {
    this.load();
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
