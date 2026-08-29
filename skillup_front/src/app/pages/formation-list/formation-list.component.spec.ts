import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { FormationListComponent } from './formation-list.component';
import { Formation } from '../../models/formation.model';

/** Puts a logged-in user in localStorage, the way AuthService reads it. */
function loginAs(id: number, role: string): void {
  localStorage.setItem('token', 'fake-jwt');
  localStorage.setItem('user', JSON.stringify({ token: 'fake-jwt', id, username: 'u', email: 'u@x.tn', role }));
}

function formation(over: Partial<Formation> = {}): Formation {
  return {
    id: 1,
    titre: 'Spring Boot',
    description: 'Backend',
    niveau: 'DEBUTANT',
    categorieId: 2,
    categorieNom: 'IT',
    ...over
  };
}

describe('FormationListComponent', () => {
  let component: FormationListComponent;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      declarations: [FormationListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    // Built directly rather than through a fixture: these are logic tests, and
    // createComponent would trigger ngOnInit's HTTP calls.
    component = TestBed.createComponent(FormationListComponent).componentInstance;
  });

  afterEach(() => localStorage.clear());

  describe('canEdit — ownership', () => {
    it('lets an admin edit a formation owned by someone else', () => {
      loginAs(1, 'ADMIN');
      expect(component.canEdit(formation({ ownerId: 99 }))).toBeTrue();
    });

    it('lets a trainer edit their own formation', () => {
      loginAs(7, 'TRAINER');
      expect(component.canEdit(formation({ ownerId: 7 }))).toBeTrue();
    });

    it('stops a trainer editing another trainer\'s formation', () => {
      loginAs(7, 'TRAINER');
      expect(component.canEdit(formation({ ownerId: 8 }))).toBeFalse();
    });

    it('stops a trainer editing an unowned formation', () => {
      loginAs(7, 'TRAINER');
      expect(component.canEdit(formation({ ownerId: undefined }))).toBeFalse();
    });

    it('stops a trainee editing anything', () => {
      loginAs(7, 'TRAINEE');
      expect(component.canEdit(formation({ ownerId: 7 }))).toBeFalse();
    });
  });

  describe('role flags', () => {
    it('allows admins and trainers to create, but not trainees', () => {
      loginAs(1, 'ADMIN');
      expect(component.canCreate).toBeTrue();
      loginAs(1, 'TRAINER');
      expect(component.canCreate).toBeTrue();
      loginAs(1, 'TRAINEE');
      expect(component.canCreate).toBeFalse();
    });

    it('marks only trainees as able to enrol', () => {
      loginAs(1, 'TRAINEE');
      expect(component.isTrainee).toBeTrue();
      loginAs(1, 'TRAINER');
      expect(component.isTrainee).toBeFalse();
    });
  });

  describe('filtering and sorting', () => {
    beforeEach(() => {
      component.formations = [
        formation({ id: 1, titre: 'Angular', categorieNom: 'IT', niveau: 'AVANCE' }),
        formation({ id: 2, titre: 'Docker', categorieNom: 'DevOps', niveau: 'DEBUTANT' }),
        formation({ id: 3, titre: 'Scrum', categorieNom: 'Management', niveau: 'DEBUTANT' })
      ];
    });

    it('filters by title, case-insensitively', () => {
      component.search = 'ang';
      expect(component.filtered.map((f) => f.titre)).toEqual(['Angular']);
    });

    it('filters by category', () => {
      component.categorieFilter = 'DevOps';
      expect(component.filtered.map((f) => f.titre)).toEqual(['Docker']);
    });

    it('filters by level', () => {
      component.niveauFilter = 'DEBUTANT';
      expect(component.filtered.map((f) => f.titre)).toEqual(['Docker', 'Scrum']);
    });

    it('sorts by title and reverses on toggle', () => {
      expect(component.filtered.map((f) => f.titre)).toEqual(['Angular', 'Docker', 'Scrum']);
      component.toggleSort();
      expect(component.filtered.map((f) => f.titre)).toEqual(['Scrum', 'Docker', 'Angular']);
    });

    it('lists the distinct categories present', () => {
      expect(component.categories).toEqual(['DevOps', 'IT', 'Management']);
      expect(component.categorieCount).toBe(3);
    });
  });

  describe('presentation helpers', () => {
    it('labels levels in sentence-case French', () => {
      expect(component.niveauLabel('DEBUTANT')).toBe('Débutant');
      expect(component.niveauLabel('INTERMEDIAIRE')).toBe('Intermédiaire');
      expect(component.niveauLabel('AVANCE')).toBe('Avancé');
    });

    it('gives a formation the same placeholder colour every time', () => {
      const f = formation({ id: 4 });
      expect(component.coverColor(f)).toBe(component.coverColor(f));
    });
  });

  describe('enrolment state', () => {
    it('reports whether a formation is in the enrolled set', () => {
      component.enrolledIds = new Set([1, 5]);
      expect(component.isEnrolled(formation({ id: 1 }))).toBeTrue();
      expect(component.isEnrolled(formation({ id: 2 }))).toBeFalse();
    });
  });
});
