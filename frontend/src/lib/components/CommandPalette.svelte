<script lang="ts" module>
  /** A search result; callers build `href` with resolve(). */
  export type PaletteItem = {
    key: string;
    label: string;
    sub: string;
    href: string;
    /** Shown when the query is empty. */
    suggested?: boolean;
  };

  /** Diacritics- and case-insensitive form used for matching. */
  export function fold(value: string) {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLocaleLowerCase('cs');
  }

  /**
   * Subsequence match scored for prefix, word starts and contiguity; null when no match.
   * Higher is better.
   */
  export function fuzzyScore(text: string, query: string): number | null {
    const hay = fold(text);
    const needle = fold(query).replace(/\s+/g, ' ').trim();
    if (!needle) return 0;
    const direct = hay.indexOf(needle);
    if (direct >= 0) return 1000 - direct * 4 + (direct === 0 || hay[direct - 1] === ' ' ? 200 : 0);
    let score = 0;
    let from = 0;
    let previous = -2;
    for (const char of needle) {
      if (char === ' ') continue;
      const index = hay.indexOf(char, from);
      if (index < 0) return null;
      score += index === previous + 1 ? 8 : 1;
      if (index === 0 || hay[index - 1] === ' ') score += 6;
      previous = index;
      from = index + 1;
    }
    return score - hay.length * 0.1;
  }
</script>

<script lang="ts">
  import { tick, type Snippet } from 'svelte';
  import { goto } from '$app/navigation';
  import { CornerDownLeft, Search } from '@lucide/svelte';

  let {
    open = $bindable(false),
    items,
    onopen,
    onchoose,
    icon
  }: {
    open?: boolean;
    items: PaletteItem[];
    /** Called each time the palette opens, e.g. to start loading data. */
    onopen?: () => void;
    onchoose?: (item: PaletteItem) => void;
    /** Leading visual for a result (line badges, page icon). Decorative. */
    icon?: Snippet<[item: PaletteItem]>;
  } = $props();

  let dialog: HTMLDialogElement | undefined = $state();
  let input: HTMLInputElement | undefined = $state();
  let query = $state('');
  let active = $state(0);
  let returnFocus: HTMLElement | null = null;

  const results = $derived.by(() => {
    if (!query.trim()) return items.filter((item) => item.suggested);
    return items
      .map((item) => ({ item, score: fuzzyScore(`${item.label} ${item.sub}`, query) }))
      .filter((entry): entry is { item: PaletteItem; score: number } => entry.score !== null)
      .sort((a, b) => b.score - a.score || a.item.label.localeCompare(b.item.label, 'cs'))
      .slice(0, 12)
      .map((entry) => entry.item);
  });

  $effect(() => {
    void query;
    active = 0;
  });

  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) {
      returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      query = '';
      onopen?.();
      dialog.showModal();
      void tick().then(() => input?.focus());
    } else if (!open && dialog.open) {
      dialog.close();
    }
  });

  function onclose() {
    open = false;
    returnFocus?.focus();
    returnFocus = null;
  }

  function choose(item: PaletteItem) {
    onchoose?.(item);
    open = false;
  }

  async function onkeydown(event: KeyboardEvent) {
    const count = results.length;
    if (event.key === 'ArrowDown' && count) {
      event.preventDefault();
      active = (active + 1) % count;
    } else if (event.key === 'ArrowUp' && count) {
      event.preventDefault();
      active = (active - 1 + count) % count;
    } else if (event.key === 'Enter' && event.target === input && results[active]) {
      event.preventDefault();
      const item = results[active];
      choose(item);
      returnFocus = null;
      // eslint-disable-next-line svelte/no-navigation-without-resolve -- callers pass resolved hrefs
      await goto(item.href);
    } else if (event.key === 'Tab') {
      // Keep focus inside the palette even where the browser lets Tab reach the chrome.
      const focusable = [
        ...(dialog?.querySelectorAll<HTMLElement>('input, a[href], button') ?? [])
      ];
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  }

  $effect(() => {
    if (!open) return;
    document.getElementById(`palette-item-${active}`)?.scrollIntoView({ block: 'nearest' });
  });
</script>

<dialog
  bind:this={dialog}
  class="palette"
  aria-label="Hledat v aplikaci"
  {onclose}
  {onkeydown}
  onclick={(event) => event.target === dialog && (open = false)}
>
  <div class="palette-inner">
    <label class="palette-search">
      <Search size={18} aria-hidden="true" />
      <span class="sr-only">Hledat zastávku, linku nebo stránku</span>
      <input
        bind:this={input}
        bind:value={query}
        type="text"
        role="combobox"
        aria-expanded="true"
        aria-controls="palette-results"
        aria-autocomplete="list"
        aria-activedescendant={results.length ? `palette-item-${active}` : undefined}
        placeholder="Zastávka, linka nebo stránka…"
        autocomplete="off"
        spellcheck="false"
      />
      <kbd aria-hidden="true">Esc</kbd>
    </label>
    {#if results.length}
      <ul id="palette-results" role="listbox" aria-label="Výsledky">
        {#each results as item, index (item.key)}
          <li role="presentation">
            <!-- eslint-disable svelte/no-navigation-without-resolve -- callers pass resolved hrefs -->
            <a
              id={`palette-item-${index}`}
              role="option"
              aria-selected={index === active}
              href={item.href}
              tabindex="-1"
              class="result"
              onclick={() => choose(item)}
              onmousemove={() => (active = index)}
            >
              <span class="result-icon" aria-hidden="true">
                {#if icon}{@render icon(item)}{:else}<span class="plain-dot"></span>{/if}
              </span>
              <span class="result-text">
                <span class="result-label">{item.label}</span>
                <span class="result-sub">{item.sub}</span>
              </span>
              {#if index === active}
                <CornerDownLeft class="result-enter" size={14} aria-hidden="true" />
              {/if}
            </a>
            <!-- eslint-enable svelte/no-navigation-without-resolve -->
          </li>
        {/each}
      </ul>
    {:else}
      <p class="palette-empty" role="status">Nic takového nemáme.</p>
    {/if}
    <p class="palette-hints" aria-hidden="true">
      <span><kbd>↑</kbd><kbd>↓</kbd> výběr</span>
      <span><kbd>↵</kbd> otevřít</span>
      <span><kbd>Esc</kbd> zavřít</span>
    </p>
  </div>
</dialog>

<style>
  .palette {
    width: min(36rem, calc(100vw - 2rem));
    max-height: min(32rem, calc(100dvh - 6rem));
    margin: 12vh auto auto;
    padding: 0;
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    background: var(--popover);
    color: var(--popover-foreground);
    box-shadow: var(--shadow-pop);
    overflow: hidden;
  }
  .palette[open] {
    animation: palette-in 160ms var(--ease-spring-out) both;
  }
  .palette::backdrop {
    background: oklch(0.2 0.01 70 / 0.28);
  }
  @keyframes palette-in {
    from {
      opacity: 0;
      transform: scale(0.97);
    }
    to {
      opacity: 1;
      transform: none;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .palette[open] {
      animation: none;
    }
  }
  .palette-inner {
    display: flex;
    flex-direction: column;
    max-height: inherit;
  }
  .palette-search {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    min-height: 3.25rem;
    padding: 0 0.875rem;
    border-bottom: 1px solid var(--border);
    color: var(--muted-foreground);
  }
  .palette-search input {
    flex: 1;
    min-width: 0;
    border: 0;
    outline: 0;
    background: transparent;
    color: var(--foreground);
    font: inherit;
    font-size: 1.0625rem;
    font-weight: 600;
  }
  ul {
    flex: 1;
    overflow-y: auto;
    padding: 0.375rem;
  }
  .result {
    display: grid;
    grid-template-columns: 3.25rem minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.5rem;
    min-height: 2.75rem;
    padding: 0.375rem 0.625rem;
    border-radius: var(--radius-md);
    color: var(--foreground);
  }
  .result[aria-selected='true'] {
    background: var(--accent);
  }
  .result-icon {
    display: flex;
    align-items: center;
    color: var(--muted-foreground);
  }
  .plain-dot {
    width: 0.625rem;
    height: 0.625rem;
    margin-left: 0.375rem;
    border: 2px solid var(--muted-foreground);
    border-radius: 50%;
  }
  .result-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .result-label {
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .result-sub {
    font-size: 0.8125rem;
    color: var(--muted-foreground);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .result :global(.result-enter) {
    color: var(--muted-foreground);
  }
  .palette-empty {
    padding: 1.25rem 1rem;
    color: var(--muted-foreground);
  }
  .palette-hints {
    display: flex;
    gap: 1rem;
    padding: 0.5rem 0.875rem;
    border-top: 1px solid var(--border);
    font-size: 0.75rem;
    color: var(--muted-foreground);
  }
  .palette-hints span {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
  }
  @media (max-width: 639px) {
    .palette {
      margin-top: 1rem;
    }
    .palette-hints {
      display: none;
    }
  }
</style>
