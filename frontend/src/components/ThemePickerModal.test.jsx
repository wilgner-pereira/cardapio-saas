import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ThemePickerModal, THEMES } from './ThemePickerModal';

describe('ThemePickerModal', () => {
  it('renders modal with the light and dark themes only', () => {
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

    expect(THEMES.map(theme => theme.id)).toEqual(['artesanal', 'grafite']);
    expect(screen.getByText('Claro')).toBeInTheDocument();
    expect(screen.getByText('Escuro')).toBeInTheDocument();
    expect(screen.getAllByRole('radio')).toHaveLength(2);
  });

  it('marks current theme as selected', () => {
    render(
      <ThemePickerModal
        isOpen={true}
        currentTheme="grafite"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    const darkCard = screen.getByRole('radio', { name: /Tema Escuro, selecionado/i });
    expect(darkCard).toHaveClass('active');
    expect(darkCard).toHaveAttribute('aria-checked', 'true');
  });

  it('falls back to the light option for a legacy saved theme', () => {
    render(
      <ThemePickerModal
        isOpen={true}
        currentTheme="brasa"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    expect(screen.getByRole('radio', { name: /Tema Claro, selecionado/i }))
      .toHaveAttribute('aria-checked', 'true');
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

    const darkCard = screen.getByRole('radio', { name: /Tema Escuro/i });
    fireEvent.click(darkCard);

    expect(onSelectPreview).toHaveBeenCalledWith('grafite');
    expect(darkCard).toHaveClass('active');
    expect(darkCard).toHaveAttribute('aria-checked', 'true');
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

    const grafiteCard = screen.getByRole('radio', { name: /Tema Escuro/i });
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
