<script lang="ts">
  import { onMount, type Snippet } from 'svelte';
  import { afterNavigate, onNavigate } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { ChevronDown, Moon, Search, Sun } from '@lucide/svelte';
  import { Button } from '$lib/ui/button';
  import { prefersReducedMotion } from '$lib/motion';
  import { cn } from '$lib/utils';
  import SiteFooter from './SiteFooter.svelte';

  let {
    children,
    footer,
    menus,
    paletteOpen = $bindable(false)
  }: {
    children: Snippet;
    footer?: Snippet;
    /** Dropdown content for the header; the shell only owns open/close state. */
    menus?: Snippet<[kind: 'stops' | 'lines', close: () => void]>;
    /** Whether the search palette (rendered by the layout) is open; the shell owns shortcuts. */
    paletteOpen?: boolean;
  } = $props();
  // Hover opens a menu for pointer users; a click keeps it open until clicked again or closed.
  let pinned = $state(false);
  // Hover intent: a pointer passing over a trigger should not flash a menu open.
  let hoverTimer: ReturnType<typeof setTimeout> | undefined;
  const cancelHover = () => clearTimeout(hoverTimer);
  const closeMenu = () => {
    cancelHover();
    menu = null;
    pinned = false;
  };
  function hoverMenu(kind: 'stops' | 'lines') {
    cancelHover();
    if (menu === kind) return;
    hoverTimer = setTimeout(() => {
      menu = kind;
      pinned = false;
    }, 150);
  }
  function clickMenu(kind: 'stops' | 'lines') {
    cancelHover();
    if (menu === kind && pinned) closeMenu();
    else {
      menu = kind;
      pinned = true;
    }
  }
  let dark = $state(false);
  let isMac = $state(false);
  let transition: ViewTransition | undefined;
  let menu = $state<'stops' | 'lines' | null>(null);

  const systemDark = () => window.matchMedia('(prefers-color-scheme: dark)').matches;
  function storedTheme() {
    try {
      return localStorage.getItem('theme');
    } catch {
      return null;
    }
  }
  function applyTheme(stored: string | null) {
    document.documentElement.classList.toggle(
      'dark',
      stored === 'dark' || (stored !== 'light' && systemDark())
    );
    syncTheme();
  }
  function syncTheme() {
    dark = document.documentElement.classList.contains('dark');
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', dark ? '#141210' : '#fcfbf9');
  }

  function isTyping(target: EventTarget | null) {
    return (
      target instanceof HTMLElement &&
      (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))
    );
  }
  function handleShortcut(event: KeyboardEvent) {
    if (event.defaultPrevented || event.altKey) return;
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      paletteOpen = !paletteOpen;
    } else if (
      event.key === '/' &&
      !event.metaKey &&
      !event.ctrlKey &&
      !paletteOpen &&
      !isTyping(event.target)
    ) {
      event.preventDefault();
      paletteOpen = true;
    }
  }

  onMount(() => {
    syncTheme();
    isMac = /Mac|iPhone|iPad/.test(navigator.platform);
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const handleSystem = () => applyTheme(storedTheme());
    function handleStorage(event: StorageEvent) {
      if (event.key === 'theme') applyTheme(event.newValue);
    }
    media.addEventListener('change', handleSystem);
    window.addEventListener('storage', handleStorage);
    return () => {
      media.removeEventListener('change', handleSystem);
      window.removeEventListener('storage', handleStorage);
      cancelHover();
      transition?.skipTransition();
    };
  });

  afterNavigate(closeMenu);

  function toggleTheme() {
    document.documentElement.classList.toggle('dark');
    syncTheme();
    try {
      localStorage.setItem('theme', dark ? 'dark' : 'light');
    } catch {
      // The current theme still works when storage is unavailable.
    }
  }

  onNavigate((navigation) => {
    transition?.skipTransition();
    if (
      !document.startViewTransition ||
      prefersReducedMotion() ||
      navigation.type === 'popstate' ||
      navigation.from?.url.pathname === navigation.to?.url.pathname
    )
      return;

    return new Promise<void>((resume) => {
      document.documentElement.dataset.routeTransition = 'true';
      const current = document.startViewTransition(async () => {
        resume();
        await navigation.complete;
      });
      transition = current;
      // Navigation must remain usable even if a browser declines to capture a snapshot.
      void current.ready.catch(resume);
      void current.finished
        .finally(() => {
          if (transition === current) delete document.documentElement.dataset.routeTransition;
        })
        .catch(() => {});
    });
  });

  const onHome = $derived(page.url.pathname === '/');
  const onStops = $derived(page.url.pathname.startsWith('/stops'));
  /** The landing and the map page manage their own width; everything else gets the column. */
  const fullBleed = $derived(onHome || page.url.pathname === '/stops');
</script>

<svelte:window onkeydown={handleShortcut} />

<a href="#main" class="sr-only z-50 bg-background p-4 focus:not-sr-only focus:absolute"
  >Přejít na obsah</a
>
<div class="flex min-h-dvh flex-col">
  <header class="site-header">
    <div class="header-inner">
      <a href={resolve('/')} class="brand-mark" aria-label="Think different Academy — úvod">
        <img
          src="/api/v1/assets/brand/tda-logo.svg"
          alt="Think different Academy"
          width="289"
          height="90"
          class="brand-logo brand-logo-light"
        />
        <img
          src="/api/v1/assets/brand/tda-logo-dark.svg"
          alt="Think different Academy"
          width="289"
          height="90"
          class="brand-logo brand-logo-dark"
        />
      </a>
      <!-- Hover-out only closes a dropdown; keyboard users close it with Escape on the trigger. -->
      <nav aria-label="Hlavní navigace" class="main-nav" onmouseleave={closeMenu}>
        <a
          href={resolve('/')}
          aria-current={onHome ? 'page' : undefined}
          class={cn('nav-link', onHome && 'is-active')}>Domů</a
        >
        <div class="nav-item">
          <a
            href={resolve('/stops')}
            aria-current={onStops ? 'page' : undefined}
            class={cn('nav-link only-small', onStops && 'is-active')}>Zastávky</a
          >
          <button
            type="button"
            class={cn('nav-link only-large', onStops && 'is-active')}
            aria-expanded={menu === 'stops'}
            aria-controls="menu-stops"
            onclick={() => clickMenu('stops')}
            onmouseenter={() => hoverMenu('stops')}
            onmouseleave={cancelHover}
            onkeydown={(event) => event.key === 'Escape' && closeMenu()}
            >Zastávky <ChevronDown class="size-4" aria-hidden="true" /></button
          >
          {#if menu === 'stops' && menus}
            {@render menus('stops', closeMenu)}
          {/if}
        </div>
        <div class="nav-item">
          <!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- resolved root plus an in-page fragment -->
          <a href={`${resolve('/')}#lines`} class="nav-link only-small">Linky</a>
          <button
            type="button"
            class="nav-link only-large"
            aria-expanded={menu === 'lines'}
            aria-controls="menu-lines"
            onclick={() => clickMenu('lines')}
            onmouseenter={() => hoverMenu('lines')}
            onmouseleave={cancelHover}
            onkeydown={(event) => event.key === 'Escape' && closeMenu()}
            >Linky <ChevronDown class="size-4" aria-hidden="true" /></button
          >
          {#if menu === 'lines' && menus}
            {@render menus('lines', closeMenu)}
          {/if}
        </div>
      </nav>
      <button
        type="button"
        class="search-trigger"
        aria-haspopup="dialog"
        aria-keyshortcuts="Control+K Meta+K /"
        onclick={() => (paletteOpen = true)}
      >
        <Search class="size-4" aria-hidden="true" />
        <span class="search-label">Hledat…</span>
        <kbd aria-hidden="true">{isMac ? '⌘K' : 'Ctrl K'}</kbd>
      </button>
      <Button
        variant="ghost"
        size="icon"
        onclick={toggleTheme}
        aria-label={dark ? 'Přepnout na světlý režim' : 'Přepnout na tmavý režim'}
        title={dark ? 'Světlý režim' : 'Tmavý režim'}
      >
        {#if dark}<Sun aria-hidden="true" />{:else}<Moon aria-hidden="true" />{/if}
      </Button>
    </div>
  </header>
  <main
    id="main"
    tabindex="-1"
    class={cn(
      'w-full flex-1 outline-none',
      !fullBleed && 'mx-auto max-w-[85rem] px-5 py-8 sm:px-8 sm:py-10'
    )}
  >
    {@render children()}
  </main>
  <SiteFooter credits={footer} />
</div>

<style>
  .site-header {
    position: sticky;
    top: 0;
    z-index: 30;
    height: var(--header-h);
    border-bottom: 1px solid var(--border);
    background: var(--background);
  }
  .header-inner {
    display: flex;
    align-items: center;
    gap: 0.5rem 1.75rem;
    height: 100%;
    max-width: 85rem;
    margin-inline: auto;
    padding: 0 1.25rem;
  }
  @media (min-width: 640px) {
    .header-inner {
      padding-inline: 2rem;
    }
  }
  .brand-mark {
    display: flex;
    align-items: center;
    min-height: 2.75rem;
    border-radius: 0.25rem;
    flex-shrink: 0;
  }
  .brand-logo {
    display: block;
    width: 7.5rem;
    height: auto;
  }
  .brand-logo-dark {
    display: none;
  }
  :global(.dark) .brand-logo-light {
    display: none;
  }
  :global(.dark) .brand-logo-dark {
    display: block;
  }
  .main-nav {
    display: flex;
    align-items: center;
    gap: 1.25rem;
    min-width: 0;
    height: 100%;
  }
  .nav-item {
    position: relative;
  }
  .nav-link {
    display: inline-flex;
    min-height: 2.75rem;
    align-items: center;
    gap: 0.25rem;
    padding: 0.25rem 0.125rem;
    border: 0;
    border-bottom: 2px solid transparent;
    background: transparent;
    color: var(--muted-foreground);
    font: inherit;
    font-size: 0.9375rem;
    font-weight: 600;
    cursor: pointer;
    transition:
      border-color var(--motion-fast) var(--ease-out),
      color var(--motion-fast) var(--ease-out);
  }
  .nav-link:hover,
  .nav-link[aria-expanded='true'] {
    color: var(--foreground);
  }
  .nav-link.is-active {
    color: var(--foreground);
    border-color: var(--foreground);
  }
  .nav-link :global(svg) {
    transition: transform var(--motion-fast) var(--ease-out);
  }
  .nav-link[aria-expanded='true'] :global(svg) {
    transform: rotate(180deg);
  }
  .search-trigger {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    min-height: 2.25rem;
    margin-left: auto;
    padding: 0 0.375rem 0 0.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
    background: var(--card);
    color: var(--muted-foreground);
    font: inherit;
    font-size: 0.875rem;
    font-weight: 500;
    cursor: pointer;
    transition: border-color var(--motion-fast) var(--ease-out);
  }
  .search-trigger:hover {
    border-color: var(--input);
  }
  .search-label {
    min-width: 7rem;
    text-align: left;
  }
  .only-small {
    display: none;
  }
  @media (max-width: 767px) {
    .only-large {
      display: none;
    }
    .only-small {
      display: inline-flex;
    }
    .header-inner {
      gap: 0.5rem;
    }
    .main-nav {
      gap: 0.75rem;
      overflow-x: auto;
      scrollbar-width: none;
    }
    .nav-link {
      white-space: nowrap;
    }
    .brand-logo {
      width: 6rem;
    }
    .search-trigger {
      width: 2.75rem;
      min-height: 2.75rem;
      justify-content: center;
      padding: 0;
    }
    .search-trigger kbd {
      display: none;
    }
    .search-trigger .search-label {
      position: absolute;
      width: 1px;
      height: 1px;
      min-width: 0;
      overflow: hidden;
      clip-path: inset(50%);
      white-space: nowrap;
    }
  }
</style>
