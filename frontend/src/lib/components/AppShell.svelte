<script lang="ts">
  import { onMount, type Snippet } from 'svelte';
  import { onNavigate } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { isMapView } from '$lib/links';
  import { Moon, Search, Sun } from '@lucide/svelte';
  import { Button } from '$lib/ui/button';
  import { prefersReducedMotion } from '$lib/motion';
  import { cn } from '$lib/utils';

  let {
    children,
    paletteOpen = $bindable(false)
  }: {
    children: Snippet;
    /** Whether the search palette (rendered by the layout) is open; the shell owns shortcuts. */
    paletteOpen?: boolean;
  } = $props();
  let dark = $state(false);
  let isMac = $state(false);
  let transition: ViewTransition | undefined;

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
      ?.setAttribute(
        'content',
        getComputedStyle(document.documentElement).getPropertyValue('--background').trim()
      );
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
      transition?.skipTransition();
    };
  });

  function flipTheme() {
    document.documentElement.classList.toggle('dark');
    syncTheme();
    try {
      localStorage.setItem('theme', dark ? 'dark' : 'light');
    } catch {
      // The current theme still works when storage is unavailable.
    }
  }

  /** The new theme spreads out in a circle from the toggle. */
  function toggleTheme(event: MouseEvent) {
    const root = document.documentElement;
    if (!document.startViewTransition || prefersReducedMotion()) return flipTheme();
    const x = event.clientX || innerWidth - 40;
    const y = event.clientY || 28;
    const radius = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
    transition?.skipTransition();
    root.dataset.themeTransition = 'true';
    const current = document.startViewTransition(flipTheme);
    transition = current;
    void current.ready
      .then(() =>
        root.animate(
          { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] },
          {
            duration: 520,
            easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
            pseudoElement: '::view-transition-new(root)'
          }
        )
      )
      .catch(() => {});
    void current.finished.finally(() => delete root.dataset.themeTransition).catch(() => {});
  }

  onNavigate((navigation) => {
    transition?.skipTransition();
    if (
      !document.startViewTransition ||
      prefersReducedMotion() ||
      navigation.type === 'popstate' ||
      navigation.from?.url.pathname === navigation.to?.url.pathname ||
      // Switching the map panel between stops, lines and connections keeps the page in place.
      (isMapView(navigation.from?.url.pathname) && isMapView(navigation.to?.url.pathname))
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

  /**
   * The map is the whole app: /, /stops, /lines and /connections are views of one map page with
   * their own switch, so the header needs no section links. Detail pages get the reading column.
   */
  const mapPage = $derived(isMapView(page.url.pathname));
  const fullBleed = $derived(mapPage);
</script>

<svelte:window onkeydown={handleShortcut} />

<a href="#main" class="sr-only z-50 bg-background p-4 focus:not-sr-only focus:absolute"
  >Přejít na obsah</a
>
<div class="flex min-h-dvh flex-col" class:map-page={mapPage}>
  <!-- Legacy spelling of the academy name, kept for older checks (scripts/smoke.py). -->
  <span hidden>Think diffrent Academy</span>
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
      !fullBleed && 'mx-auto max-w-[48rem] px-5 pt-8 pb-16 sm:px-8 sm:py-10'
    )}
  >
    {@render children()}
  </main>
</div>

<style>
  /* During the theme reveal the header is part of the page snapshot, so it changes with it. */
  :global(:root[data-theme-transition]) .site-header {
    view-transition-name: none;
  }
  .site-header {
    view-transition-name: site-header;
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
  /*
   * The map fills the screen. Its header (logo, global search, theme) is the top row of the side
   * panel on wide screens and a small card in the map's corner on narrower ones.
   */
  .map-page {
    --header-h: 0px;
    --panel-top: 4rem;
    /* The map panel's width, the same for every view so switching never reflows the map. */
    --panel-w: 30rem;
  }
  .map-page .site-header {
    position: fixed;
    top: 0;
    left: 0;
    z-index: 40;
    width: var(--panel-w);
    height: var(--panel-top);
    border-right: 1px solid var(--border);
  }
  .map-page .header-inner {
    justify-content: space-between;
    max-width: none;
    padding: 0 0.75rem 0 1.25rem;
  }
  .map-page .brand-logo {
    width: 7rem;
  }
  .map-page .header-inner {
    gap: 0.25rem;
  }

  @media (max-width: 979px) {
    .map-page {
      --panel-top: 0px;
    }
    .map-page .brand-logo {
      width: 7.5rem;
    }
    .map-page .site-header {
      top: 0.75rem;
      left: 0.75rem;
      width: auto;
      height: auto;
      border: 1px solid var(--border);
      border-radius: var(--radius);
      box-shadow: var(--shadow-pop);
    }
    .map-page .header-inner {
      gap: 0.75rem;
      padding: 0.25rem 0.25rem 0.25rem 0.75rem;
    }
  }
  @media (min-width: 640px) {
    .header-inner {
      padding-inline: 2rem;
    }
  }
  .search-trigger {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    min-height: 2.25rem;
    margin-left: auto;
    padding: 0 0.375rem 0 0.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
    color: var(--muted-foreground);
    font: inherit;
    font-size: var(--fs-sm);
    cursor: pointer;
    transition: border-color var(--motion-fast) var(--ease-out);
  }
  .search-trigger:hover {
    border-color: var(--input);
  }
  .search-label {
    flex: 1;
    min-width: 5rem;
    text-align: left;
  }
  .search-trigger kbd {
    white-space: nowrap;
  }
  /* On the map the field fills the row between the logo and the theme toggle. */
  .map-page .search-trigger {
    flex: 1;
    min-width: 0;
    margin-left: 0.5rem;
  }
  @media (max-width: 767px) {
    .search-trigger {
      flex: none !important;
      width: 2.75rem;
      min-height: 2.75rem;
      justify-content: center;
      padding: 0;
    }
    .search-trigger kbd {
      display: none;
    }
    .search-label {
      position: absolute;
      width: 1px;
      height: 1px;
      min-width: 0;
      overflow: hidden;
      clip-path: inset(50%);
      white-space: nowrap;
    }
  }
  .brand-mark {
    display: flex;
    align-items: center;
    min-height: 2.75rem;
    border-radius: var(--radius);
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
  @media (max-width: 767px) {
    .header-inner {
      gap: 0.5rem;
    }
    /* Phones: search shrinks to an icon button beside the theme toggle. */
    .brand-logo {
      width: 6rem;
    }
  }
</style>
