import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { ApiError } from '../../../core/errors/api-error';
import { NotificationService } from '../../../core/errors/notification.service';
import { AdminApi, AdminCategory } from '../data-access/admin.api';
import { CategoryListPage } from './category-list.page';

describe('CategoryListPage', () => {
  const category: AdminCategory = { id: 'cat-1', name: 'Data', active: true, offerCount: 4 };
  const deleteCategory = vi.fn();
  const setCategoryActive = vi.fn();
  const open = vi.fn();

  beforeEach(() => {
    const inUse: ApiError = {
      status: 409,
      code: 'RESOURCE_IN_USE',
      title: 'Ressource utilisée',
      detail: 'Cette catégorie est utilisée par 4 offre(s) : désactivez-la plutôt.',
      errors: [],
      extensions: { usageCount: 4 },
    };
    deleteCategory.mockReset().mockReturnValue(throwError(() => inUse));
    setCategoryActive.mockReset().mockReturnValue(of({ ...category, active: false }));
    open.mockReset().mockReturnValue({ afterClosed: () => of(true) });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AdminApi, useValue: { deleteCategory, setCategoryActive } },
        { provide: MatDialog, useValue: { open } },
        { provide: NotificationService, useValue: { success: vi.fn(), error: vi.fn() } },
      ],
    });
  });

  it('propose la désactivation quand la catégorie est utilisée (RG-30)', () => {
    const fixture = TestBed.createComponent(CategoryListPage);

    fixture.componentInstance['remove'](category);

    expect(deleteCategory).toHaveBeenCalledWith('cat-1');
    expect(open).toHaveBeenCalledTimes(2);
    expect(open.mock.calls[1][1].data.message).toContain('4 offre(s)');
    expect(setCategoryActive).toHaveBeenCalledWith('cat-1', false);
  });
});
