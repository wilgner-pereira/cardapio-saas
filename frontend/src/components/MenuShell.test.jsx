import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MenuShell } from './MenuShell';

describe('MenuShell', () => {
  it('uses the same sequence for sections and navigation after reorder and keeps anchors', () => {
    const entries = [
      { id: 1, nome: 'Suco', categoria: 'Bebidas', ativo: true, categoriaOrdem: 0 },
      { id: 2, nome: 'Batata', categoria: 'Entradas', ativo: true, categoriaOrdem: 1 }
    ];
    const { rerender } = render(<MenuShell username="test" products={entries} />);
    expect(screen.getAllByRole('heading', { level: 2 }).map(node => node.textContent)).toEqual(['Bebidas', 'Entradas']);
    expect([...screen.getByRole('navigation').querySelectorAll('button')].map(node => node.textContent)).toEqual(['Bebidas', 'Entradas']);
    rerender(<MenuShell username="test" products={[entries[1], entries[0]]} />);
    expect(screen.getAllByRole('heading', { level: 2 }).map(node => node.textContent)).toEqual(['Entradas', 'Bebidas']);
    fireEvent.click(screen.getByRole('button', { name: 'Bebidas' }));
    expect(document.getElementById('category-bebidas').scrollIntoView).toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: /Subir seção/ })).not.toBeInTheDocument();
  });

  it('moves categories and disables first/last boundaries and pending requests', () => {
    const onMoveCategory = vi.fn();
    const entries = [
      { id: 1, nome: 'Suco', categoria: 'Bebidas', ativo: true },
      { id: 2, nome: 'Batata', categoria: 'Entradas', ativo: true }
    ];
    const { rerender } = render(<MenuShell username="test" mode="admin" products={entries} onMoveCategory={onMoveCategory} />);
    expect(screen.getByRole('button', { name: 'Subir seção Bebidas' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Descer seção Entradas' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Subir seção Entradas' }));
    expect(onMoveCategory).toHaveBeenCalledWith('Entradas', -1);
    fireEvent.click(screen.getByRole('button', { name: 'Descer seção Bebidas' }));
    expect(onMoveCategory).toHaveBeenCalledWith('Bebidas', 1);
    rerender(<MenuShell username="test" mode="admin" products={entries} categoryMoving onMoveCategory={onMoveCategory} />);
    expect(screen.getByRole('button', { name: 'Subir seção Entradas' })).toBeDisabled();
  });
  it('renders the empty state when products is omitted', () => {
    render(<MenuShell username="test" />);
    expect(screen.getByText(/cardapio indisponivel/i)).toBeInTheDocument();
    expect(screen.queryByText('〽')).not.toBeInTheDocument();
  });

  beforeEach(() => {
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
    document.body.removeAttribute('data-theme');
  });

  const mockProducts = [
    { id: 1, nome: 'Burger', preco: 15, categoria: 'Lanches', ativo: true },
    { id: 2, nome: 'Fries', preco: 10, categoria: 'Lanches', ativo: false },
  ];

  function renderScrollableTabs() {
    render(<MenuShell username="test" products={mockProducts} />);
    const tabs = screen.getByRole('navigation');
    Object.defineProperties(tabs, {
      scrollWidth: { value: 1000 },
      clientWidth: { value: 400 },
    });
    return tabs;
  }

  it('scrolls horizontally with the wheel and releases page scrolling at the edge', () => {
    const tabs = renderScrollableTabs();
    expect(fireEvent.wheel(tabs, { deltaY: 100, cancelable: true })).toBe(false);
    expect(tabs.scrollLeft).toBe(100);
    tabs.scrollLeft = 600;
    expect(fireEvent.wheel(tabs, { deltaY: 100, cancelable: true })).toBe(true);
    expect(tabs.scrollLeft).toBe(600);
  });

  it('drags the bar without activating a category and preserves the next normal click', () => {
    const tabs = renderScrollableTabs();
    const category = screen.getByRole('button', { name: 'Lanches' });
    fireEvent.mouseDown(category, { button: 0, clientX: 200 });
    fireEvent.mouseMove(window, { clientX: 100 });
    expect(tabs.scrollLeft).toBe(100);
    expect(tabs).toHaveClass('is-dragging');
    fireEvent.mouseUp(window);
    fireEvent.click(category, { detail: 1 });
    expect(window.HTMLElement.prototype.scrollIntoView).not.toHaveBeenCalled();
    expect(tabs).not.toHaveClass('is-dragging');
    fireEvent.mouseDown(category, { button: 0, clientX: 100 });
    fireEvent.mouseUp(window);
    fireEvent.click(category, { detail: 1 });
    expect(window.HTMLElement.prototype.scrollIntoView).toHaveBeenCalled();
  });

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

  it('triggers product actions in admin mode with product-specific labels and titles', () => {
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

    const editButton = screen.getByLabelText('Editar Burger');
    expect(editButton).toHaveAttribute('title', 'Editar Burger');
    fireEvent.click(editButton);
    expect(onEdit).toHaveBeenCalledWith(mockProducts[0]);

    const deleteButton = screen.getByLabelText('Remover Fries');
    expect(deleteButton).toHaveAttribute('title', 'Remover Fries');
    fireEvent.click(deleteButton);
    expect(onDelete).toHaveBeenCalledWith(mockProducts[1]);

    expect(screen.getByLabelText('Pausar Burger')).toBeInTheDocument();
    expect(screen.getByLabelText('Ativar Fries')).toBeInTheDocument();
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
