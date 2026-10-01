import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MenuShell } from './MenuShell';

describe('MenuShell', () => {
  it('renders public cover and product images from their Supabase URLs', () => {
    const coverUrl = 'https://project.supabase.co/storage/v1/object/public/menu/cover.webp';
    const productUrl = 'https://project.supabase.co/storage/v1/object/public/menu/product%20one.webp';
    const { container } = render(<MenuShell username="test" establishment={{ nome: 'Pizzaria', heroImageUrl: coverUrl }} products={[
      { id: 1, nome: 'Pizza', descricao: 'Queijo', preco: 20, categoria: 'Pizzas', ativo: true, imageUrl: productUrl }
    ]} />);
    expect(container.querySelector('.hero-cover')).toHaveAttribute('src', coverUrl);
    expect(container.querySelector('.product-image-wrap img')).toHaveAttribute('src', productUrl);
  });

  it('uses the saved product image shape in public and admin menus and accepts a preview override', () => {
    const { container, rerender } = render(<MenuShell username="test" establishment={{ imagensRedondas: true }} />);
    expect(container.querySelector('.menu-page')).toHaveAttribute('data-image-shape', 'round');
    rerender(<MenuShell username="test" mode="admin" establishment={{ imagensRedondas: true }} roundImages={false} />);
    expect(container.querySelector('.menu-page')).toHaveAttribute('data-image-shape', 'square');
    rerender(<MenuShell username="test" />);
    expect(container.querySelector('.menu-page')).toHaveAttribute('data-image-shape', 'square');
  });
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
    expect(screen.getByRole('button', { name: 'Bebidas' })).toHaveClass('active');
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
    window.history.replaceState({}, '', '/cardapio/test?ref=menu');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('shows configured social links securely and normalizes WhatsApp without adding a country code', () => {
    render(<MenuShell username="test" establishment={{
      instagramUrl: 'https://instagram.com/pizzaria', whatsapp: '+1 (202) 555-0123'
    }} />);
    const instagram = screen.getByRole('link', { name: 'Instagram' });
    expect(instagram).toHaveAttribute('href', 'https://instagram.com/pizzaria');
    expect(instagram).toHaveAttribute('target', '_blank');
    expect(instagram).toHaveAttribute('rel', 'noopener noreferrer');
    const whatsapp = screen.getByRole('link', { name: 'WhatsApp' });
    expect(whatsapp).toHaveAttribute('href', 'https://wa.me/12025550123');
    expect(whatsapp).toHaveAttribute('target', '_blank');
    expect(whatsapp).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it.each(['', null, undefined, 'javascript:alert(1)', 'data:text/html,test', '//instagram.com/test',
    'ftp://instagram.com/test', 'not-a-url', 'https://', 'https:///perfil', 'https://instagram.com/with space',
    'https://instagram.com@evil.example/test', 'https://instagram.com\\evil.example/test'])('hides absent or unsafe Instagram URLs: %s', instagramUrl => {
    render(<MenuShell username="test" establishment={{ instagramUrl }} />);
    expect(screen.queryByRole('link', { name: 'Instagram' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Compartilhar' })).toBeInTheDocument();
  });

  it.each(['', null, undefined, '+( )-', 'no number'])('hides WhatsApp without digits: %s', whatsapp => {
    render(<MenuShell username="test" establishment={{ whatsapp }} />);
    expect(screen.queryByRole('link', { name: 'WhatsApp' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Compartilhar' })).toBeInTheDocument();
  });

  it('places social actions below the hero and categories after the restaurant information before sections', () => {
    const { container } = render(<MenuShell username="test" products={[
      { id: 1, nome: 'Batata', categoria: 'Entradas', ativo: true }
    ]} establishment={{ descricao: 'Pizzas artesanais', telefone: '(34) 3000-1002', endereco: 'Av. Principal, 450' }} />);
    const hero = screen.getByRole('region', { name: 'Topo do cardápio' });
    const actions = screen.getByRole('button', { name: 'Compartilhar' }).parentElement;
    const description = screen.getByText('Pizzas artesanais');
    const phone = screen.getByText('(34) 3000-1002');
    const address = screen.getByText('Av. Principal, 450');
    const tabs = screen.getByRole('navigation');
    const firstSection = container.querySelector('.menu-section');
    expect(container.querySelector('.paper').firstElementChild).toBe(actions);
    for (const [before, after] of [[hero, actions], [actions, description], [description, phone], [phone, address], [address, tabs], [tabs, firstSection]]) {
      expect(before.compareDocumentPosition(after) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    }
  });

  it('shares the current public URL with the native share API', async () => {
    const share = vi.fn().mockResolvedValue(undefined);
    const writeText = vi.fn();
    vi.stubGlobal('navigator', { share, clipboard: { writeText } });
    render(<MenuShell username="test" establishment={{ nome: 'Pizzaria Forno Alto' }} />);
    fireEvent.click(screen.getByRole('button', { name: 'Compartilhar' }));
    expect(share).toHaveBeenCalledWith({ title: 'Pizzaria Forno Alto', text: 'Confira o cardápio de Pizzaria Forno Alto.', url: window.location.href });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Compartilhar' })).toBeEnabled());
    expect(writeText).not.toHaveBeenCalled();
  });

  it('copies the public link when native sharing is unavailable and resets accessible feedback', async () => {
    vi.useFakeTimers();
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    render(<MenuShell username="test" />);
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Compartilhar' })));
    expect(writeText).toHaveBeenCalledWith(window.location.href);
    expect(screen.getByRole('status')).toHaveTextContent('Link copiado');
    act(() => vi.advanceTimersByTime(3000));
    expect(screen.getByRole('button', { name: 'Compartilhar' })).toBeInTheDocument();
  });

  it.each(['native', 'clipboard', 'manual'])('shares only the encoded public menu URL from admin using %s', async method => {
    window.history.replaceState({}, '', '/painel');
    const share = vi.fn().mockResolvedValue(undefined);
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', method === 'native' ? { share } : method === 'clipboard' ? { clipboard: { writeText } } : {});
    render(<MenuShell username="café centro" mode="admin" />);
    fireEvent.click(screen.getByRole('button', { name: 'Compartilhar' }));
    const publicUrl = `${window.location.origin}/cardapio/caf%C3%A9%20centro`;
    if (method === 'native') {
      await waitFor(() => expect(share).toHaveBeenCalledWith(expect.objectContaining({ url: publicUrl })));
    } else if (method === 'clipboard') {
      await waitFor(() => expect(writeText).toHaveBeenCalledWith(publicUrl));
    } else {
      expect(await screen.findByLabelText('Copie o link do cardápio:')).toHaveValue(publicUrl);
    }
  });

  it('leaves a cancelled native share without copying or showing an error', async () => {
    const writeText = vi.fn();
    vi.stubGlobal('navigator', { share: vi.fn().mockRejectedValue(new DOMException('Cancelled', 'AbortError')), clipboard: { writeText } });
    render(<MenuShell username="test" />);
    fireEvent.click(screen.getByRole('button', { name: 'Compartilhar' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Compartilhar' })).toBeEnabled());
    expect(writeText).not.toHaveBeenCalled();
    expect(screen.queryByLabelText('Copie o link do cardápio:')).not.toBeInTheDocument();
  });

  it('copies the link when native sharing fails for a reason other than cancellation', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { share: vi.fn().mockRejectedValue(new Error('Unavailable')), clipboard: { writeText } });
    render(<MenuShell username="test" />);
    fireEvent.click(screen.getByRole('button', { name: 'Compartilhar' }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(window.location.href));
    expect(screen.getByRole('status')).toHaveTextContent('Link copiado');
  });

  it.each([undefined, { writeText: vi.fn().mockRejectedValue(new Error('Denied')) }])('provides a selectable link when clipboard is missing or denied', async clipboard => {
    vi.stubGlobal('navigator', { clipboard });
    render(<MenuShell username="test" />);
    fireEvent.click(screen.getByRole('button', { name: 'Compartilhar' }));
    const input = await screen.findByLabelText('Copie o link do cardápio:');
    expect(input).toHaveValue(window.location.href);
    expect(input).toHaveAttribute('readonly');
    const select = vi.spyOn(input, 'select');
    fireEvent.focus(input);
    expect(select).toHaveBeenCalled();
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

  describe('back to top', () => {
    let notifyIntersection;
    let observer;

    beforeEach(() => {
      observer = { observe: vi.fn(), disconnect: vi.fn() };
      vi.stubGlobal('IntersectionObserver', vi.fn(function (callback) {
        notifyIntersection = callback;
        return observer;
      }));
      vi.stubGlobal('scrollTo', vi.fn());
    });

    function reportPosition(isIntersecting, top, bottom) {
      act(() => notifyIntersection([{
        isIntersecting,
        boundingClientRect: { top, bottom },
        rootBounds: { top: 0 },
      }]));
    }

    it('observes the original category position and stays hidden before it is reached, regardless of scroll pixels', () => {
      const { container } = render(<MenuShell username="test" products={mockProducts} />);
      const button = container.querySelector('.scroll-to-top-button');
      const marker = container.querySelector('.category-position-marker');
      expect(observer.observe).toHaveBeenCalledWith(marker);
      expect(marker).toHaveAttribute('aria-hidden', 'true');
      expect(marker.nextElementSibling).toBe(screen.getByRole('navigation'));
      expect(screen.queryByRole('button', { name: 'Voltar ao topo' })).not.toBeInTheDocument();
      expect(button).toBeDisabled();
      expect(button).toHaveAttribute('tabindex', '-1');
      vi.stubGlobal('scrollY', 10000);
      reportPosition(false, 1500, 1500);
      expect(button).not.toHaveClass('is-visible');
      expect(button).toHaveAttribute('aria-hidden', 'true');
    });

    it('appears when the original category position passes the viewport even while the bar stays visible, and hides on return', () => {
      const { container } = render(<MenuShell username="test" products={mockProducts} />);
      const button = container.querySelector('.scroll-to-top-button');
      const tabs = screen.getByRole('navigation');
      vi.spyOn(tabs, 'getBoundingClientRect').mockReturnValue({ top: 0, bottom: 70 });
      reportPosition(true, 300, 300);
      expect(button).not.toHaveClass('is-visible');
      reportPosition(true, 0, 0);
      expect(button).not.toHaveClass('is-visible');
      reportPosition(false, -1, -1);
      expect(tabs.getBoundingClientRect()).toEqual({ top: 0, bottom: 70 });
      expect(screen.getByRole('button', { name: 'Voltar ao topo' })).toBe(button);
      expect(button).toHaveClass('is-visible');
      expect(button).toBeEnabled();
      expect(button).toHaveAttribute('tabindex', '0');
      reportPosition(true, 1, 1);
      expect(button).not.toHaveClass('is-visible');
      expect(button).toHaveAttribute('aria-hidden', 'true');
      expect(button).toBeDisabled();
      // Keep the element mounted so CSS can animate its disappearance.
      expect(button).toBeInTheDocument();
    });

    it('scrolls smoothly to the top by click, Enter and Space', async () => {
      const user = userEvent.setup();
      render(<MenuShell username="test" products={mockProducts} />);
      reportPosition(false, -1, -1);
      const button = screen.getByRole('button', { name: 'Voltar ao topo' });
      await user.click(button);
      expect(button).not.toHaveFocus();
      button.focus();
      expect(button).toHaveFocus();
      await user.keyboard('{Enter}');
      expect(button).not.toHaveFocus();
      button.focus();
      await user.keyboard(' ');
      expect(button).not.toHaveFocus();
      expect(window.scrollTo).toHaveBeenCalledTimes(3);
      expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
    });

    it('disconnects when the public menu is unmounted', () => {
      const { unmount } = render(<MenuShell username="test" products={mockProducts} />);
      unmount();
      expect(observer.disconnect).toHaveBeenCalledTimes(1);
    });

    it.each([
      { mode: 'admin' },
      { loading: true },
      { error: 'Falha ao carregar' },
      { products: [] },
      { products: [{ ...mockProducts[0], ativo: false }] },
    ])('does not add a button or observer for admin, loading, error or unavailable menus: %o', props => {
      const { container } = render(<MenuShell username="test" products={mockProducts} {...props} />);
      expect(container.querySelector('.scroll-to-top-button')).not.toBeInTheDocument();
      expect(IntersectionObserver).not.toHaveBeenCalled();
    });

    it('supports menus that load asynchronously and disconnects when entering admin mode', () => {
      const { container, rerender } = render(<MenuShell username="test" loading />);
      expect(IntersectionObserver).not.toHaveBeenCalled();
      rerender(<MenuShell username="test" products={mockProducts} />);
      expect(observer.observe).toHaveBeenCalledWith(container.querySelector('.category-position-marker'));
      reportPosition(false, -1, -1);
      expect(screen.getByRole('button', { name: 'Voltar ao topo' })).toBeEnabled();
      rerender(<MenuShell username="test" products={mockProducts} mode="admin" />);
      expect(observer.disconnect).toHaveBeenCalledTimes(1);
      expect(screen.queryByRole('button', { name: 'Voltar ao topo' })).not.toBeInTheDocument();
    });

    it('keeps the menu functional and the button hidden without IntersectionObserver support', () => {
      vi.stubGlobal('IntersectionObserver', undefined);
      render(<MenuShell username="test" products={mockProducts} />);
      fireEvent.click(screen.getByRole('button', { name: 'Lanches' }));
      expect(window.HTMLElement.prototype.scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
      expect(screen.queryByRole('button', { name: 'Voltar ao topo' })).not.toBeInTheDocument();
    });
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
