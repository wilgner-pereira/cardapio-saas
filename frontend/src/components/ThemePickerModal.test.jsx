import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
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
    expect(within(screen.getByRole('radiogroup', { name: 'Temas disponíveis' })).getAllByRole('radio')).toHaveLength(2);
    expect(screen.getByRole('radio', { name: 'Quadradas' })).toHaveAttribute('aria-checked', 'true');
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

  it('calls onSubmit with selected theme and image shape when saving appearance', async () => {
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
    fireEvent.click(screen.getByRole('radio', { name: 'Redondas' }));

    const saveButton = screen.getByRole('button', { name: 'Salvar aparência' });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith('grafite', true);
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

    const saveButton = screen.getByRole('button', { name: 'Salvar aparência' });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Erro ao salvar no servidor');
    });
  });

  it('previews the image shape independently of the theme and initializes the saved shape', () => {
    const onSelectImageShapePreview = vi.fn();
    const onSelectPreview = vi.fn();
    render(<ThemePickerModal currentRoundImages onSelectImageShapePreview={onSelectImageShapePreview} onSelectPreview={onSelectPreview} />);
    expect(screen.getByRole('radio', { name: 'Redondas' })).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(screen.getByRole('radio', { name: 'Quadradas' }));
    expect(onSelectImageShapePreview).toHaveBeenCalledWith(false);
    expect(onSelectPreview).not.toHaveBeenCalled();
  });

  it('keeps the selected draft when the parent updates saved props or the focus reference', () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const { rerender } = render(<ThemePickerModal currentTheme="artesanal" currentRoundImages={false} onSubmit={onSubmit} triggerRef={{ current: null }} />);
    fireEvent.click(screen.getByRole('radio', { name: 'Redondas' }));
    expect(screen.getByRole('radio', { name: 'Redondas' })).toHaveAttribute('aria-checked', 'true');
    rerender(<ThemePickerModal currentTheme="grafite" currentRoundImages={false} onSubmit={onSubmit} triggerRef={{ current: null }} />);
    expect(screen.getByRole('radio', { name: 'Redondas' })).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(screen.getByRole('button', { name: 'Salvar aparência' }));
    expect(onSubmit).toHaveBeenCalledWith('artesanal', true);
  });
});
