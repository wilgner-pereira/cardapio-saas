import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MenuShell } from './MenuShell';

describe('MenuShell', () => {
  beforeEach(() => {
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
    document.body.removeAttribute('data-theme');
  });

  const mockProducts = [
    { id: 1, nome: 'Burger', preco: 15, categoria: 'Lanches', ativo: true },
    { id: 2, nome: 'Fries', preco: 10, categoria: 'Lanches', ativo: false },
  ];

  it('renders loading state', () => {
    render(<MenuShell username="test" products={[]} loading={true} />);
    expect(screen.getByText('Carregando cardapio')).toBeInTheDocument();
  });

  it('renders store name from username', () => {
    render(<MenuShell username="my-store" products={[]} />);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('My Store');
  });

  it('renders store name from establishment', () => {
    render(<MenuShell username="test" establishment={{ nome: 'Cool Store' }} products={[]} />);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Cool Store');
  });

  it('renders active products in public mode', () => {
    render(<MenuShell username="test" products={mockProducts} mode="public" />);
    expect(screen.getByText('Burger')).toBeInTheDocument();
    expect(screen.queryByText('Fries')).not.toBeInTheDocument();
  });

  it('renders all products in admin mode and shows paused pill', () => {
    render(<MenuShell username="test" products={mockProducts} mode="admin" />);
    expect(screen.getByText('Burger')).toBeInTheDocument();
    expect(screen.getByText('Fries')).toBeInTheDocument();
    expect(screen.getByText('Pausado')).toBeInTheDocument();
  });

  it('triggers admin actions in hero and nav', () => {
    const onEditEstablishment = vi.fn();
    const onRefresh = vi.fn();
    const onLogout = vi.fn();
    const onCreate = vi.fn();
    const onEditTheme = vi.fn();

    render(
      <MenuShell
        username="test"
        products={[]}
        mode="admin"
        onEditEstablishment={onEditEstablishment}
        onRefresh={onRefresh}
        onLogout={onLogout}
        onCreate={onCreate}
        onEditTheme={onEditTheme}
      />
    );

    fireEvent.click(screen.getByLabelText('Alterar tema do cardápio'));
    expect(onEditTheme).toHaveBeenCalled();

    fireEvent.click(screen.getByLabelText('Editar estabelecimento'));
    expect(onEditEstablishment).toHaveBeenCalled();

    fireEvent.click(screen.getByLabelText('Atualizar'));
    expect(onRefresh).toHaveBeenCalled();

    fireEvent.click(screen.getByLabelText('Sair'));
    expect(onLogout).toHaveBeenCalled();

    fireEvent.click(screen.getByText('Novo'));
    expect(onCreate).toHaveBeenCalled();
  });

  it('triggers product actions in admin mode', () => {
    const onEdit = vi.fn();
    const onDelete = vi.fn();

    render(
      <MenuShell
        username="test"
        products={mockProducts}
        mode="admin"
        onEdit={onEdit}
        onDelete={onDelete}
      />
    );

    const editButtons = screen.getAllByLabelText('Editar produto');
    fireEvent.click(editButtons[0]);
    expect(onEdit).toHaveBeenCalledWith(mockProducts[0]);

    const deleteButtons = screen.getAllByLabelText('Remover produto');
    fireEvent.click(deleteButtons[1]);
    expect(onDelete).toHaveBeenCalledWith(mockProducts[1]);
  });

  it('applies data-theme attribute on menu-page and document.body', () => {
    const { container } = render(
      <MenuShell
        username="test"
        establishment={{ tema: 'brasa' }}
        products={[]}
      />
    );

    const menuPage = container.querySelector('.menu-page');
    expect(menuPage).toHaveAttribute('data-theme', 'brasa');
    expect(document.body).toHaveAttribute('data-theme', 'brasa');
  });

  it('falls back to artesanal when theme is not provided or empty', () => {
    const { container } = render(
      <MenuShell
        username="test"
        establishment={{ tema: null }}
        products={[]}
      />
    );

    const menuPage = container.querySelector('.menu-page');
    expect(menuPage).toHaveAttribute('data-theme', 'artesanal');
    expect(document.body).toHaveAttribute('data-theme', 'artesanal');
  });

  it('renders palette button only in admin mode as the first button in admin strip', () => {
    const { rerender } = render(
      <MenuShell username="test" products={[]} mode="public" />
    );
    expect(screen.queryByLabelText('Alterar tema do cardápio')).not.toBeInTheDocument();

    rerender(
      <MenuShell username="test" products={[]} mode="admin" />
    );
    const paletteBtn = screen.getByLabelText('Alterar tema do cardápio');
    expect(paletteBtn).toBeInTheDocument();
    expect(paletteBtn).toHaveAttribute('title', 'Alterar tema');
  });
});
