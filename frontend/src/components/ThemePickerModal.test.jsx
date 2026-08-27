import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ThemePickerModal, THEMES } from './ThemePickerModal';

describe('ThemePickerModal', () => {
  it('renders modal with correct title, description and all 5 themes', () => {
    render(
      <ThemePickerModal
        isOpen={true}
        currentTheme="artesanal"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Aparência do cardápio')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Escolha um estilo para o seu cardápio. As cores foram combinadas para manter boa leitura e consistência visual.'
      )
    ).toBeInTheDocument();

    expect(THEMES.map(theme => theme.id)).toEqual([
      'artesanal',
      'brasa',
      'atlantico',
      'vinho',
      'grafite'
    ]);
    expect(screen.getByText('Artesanal')).toBeInTheDocument();
    expect(screen.getByText('Brasa Suave')).toBeInTheDocument();
    expect(screen.getByText('Atlântico')).toBeInTheDocument();
    expect(screen.getByText('Vinho & Marfim')).toBeInTheDocument();
    expect(screen.getByText('Grafite & Âmbar')).toBeInTheDocument();
  });

  it('marks current theme as selected', () => {
    render(
      <ThemePickerModal
        isOpen={true}
        currentTheme="brasa"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const brasaCard = screen.getByRole('radio', { name: /Tema Brasa Suave, selecionado/i });
    expect(brasaCard).toHaveClass('active');
    expect(brasaCard).toHaveAttribute('aria-checked', 'true');
  });

  it('triggers onSelectPreview when another theme is selected', () => {
    const onSelectPreview = vi.fn();
    render(
      <ThemePickerModal
        isOpen={true}
        currentTheme="artesanal"
        onSelectPreview={onSelectPreview}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const atlanticoCard = screen.getByRole('radio', { name: /Tema Atlântico/i });
    fireEvent.click(atlanticoCard);

    expect(onSelectPreview).toHaveBeenCalledWith('atlantico');
    expect(atlanticoCard).toHaveClass('active');
    expect(atlanticoCard).toHaveAttribute('aria-checked', 'true');
  });

  it('calls onClose when Cancelar or X button is clicked', () => {
    const onClose = vi.fn();
    render(
      <ThemePickerModal
        isOpen={true}
        currentTheme="artesanal"
        onClose={onClose}
        onSubmit={vi.fn()}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(onClose).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'Fechar' }));
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it('calls onClose when Escape key is pressed', () => {
    const onClose = vi.fn();
    render(
      <ThemePickerModal
        isOpen={true}
        currentTheme="artesanal"
        onClose={onClose}
        onSubmit={vi.fn()}
      />
    );

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls onSubmit with selected theme when Salvar tema is clicked', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ThemePickerModal
        isOpen={true}
        currentTheme="artesanal"
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />
    );

    const grafiteCard = screen.getByRole('radio', { name: /Tema Grafite & Âmbar/i });
    fireEvent.click(grafiteCard);

    const saveButton = screen.getByRole('button', { name: 'Salvar tema' });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith('grafite');
    });
  });

  it('displays error message when onSubmit fails', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('Erro ao salvar no servidor'));
    render(
      <ThemePickerModal
        isOpen={true}
        currentTheme="artesanal"
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />
    );

    const saveButton = screen.getByRole('button', { name: 'Salvar tema' });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Erro ao salvar no servidor');
    });
  });
});
