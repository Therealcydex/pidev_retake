import { Component, OnInit } from '@angular/core';
import { FormationService } from '../../services/formation.service';
import { FormationStats } from '../../models/formation.model';

@Component({
  selector: 'app-stats',
  templateUrl: './stats.component.html'
})
export class StatsComponent implements OnInit {
  stats: FormationStats | null = null;
  loading = false;

  constructor(private formationService: FormationService) {}

  ngOnInit(): void {
    this.loading = true;
    this.formationService.getStats().subscribe({
      next: (data) => {
        this.stats = data;
        this.loading = false;
      },
      error: () => (this.loading = false)
    });
  }

  categorieEntries(): { key: string; value: number }[] {
    if (!this.stats) return [];
    return Object.entries(this.stats.countByCategorie).map(([key, value]) => ({ key, value }));
  }

  niveauEntries(): { key: string; value: number }[] {
    if (!this.stats) return [];
    return Object.entries(this.stats.countByNiveau).map(([key, value]) => ({ key, value }));
  }

  maxCategorie(): number {
    const values = Object.values(this.stats?.countByCategorie || {});
    return values.length ? Math.max(...values) : 1;
  }

  maxNiveau(): number {
    const values = Object.values(this.stats?.countByNiveau || {});
    return values.length ? Math.max(...values) : 1;
  }
}
