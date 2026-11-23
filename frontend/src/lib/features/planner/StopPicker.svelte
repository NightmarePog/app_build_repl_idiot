<script lang="ts">
  import { untrack } from 'svelte';
  import type { Stop } from '$lib/api/generated';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import { linesOf } from '$lib/features/network/lines.svelte';
  import { fold } from './planner';

  /**
   * Stop autocomplete (ARIA 1.2 combobox with a listbox popup). Typing filters the stops,
   * ignoring diacritics and case; arrows move, Enter picks, Escape closes. The chosen stop id is
   * also posted as a hidden field, so the form works without JavaScript too.
   */
  let {
    id,
    name,
    label,
    placeholder,
    stops,
    value = $bindable(null),
    error = null,
    onpick
  }: {
    id: string;
    name: string;
    label: string;
    placeholder: string;
    stops: Stop[];
    value?: number | null;
    error?: string | null;
    onpick?: () => void;
  } = $props();

  let input: HTMLInputElement | undefined = $state();
  let text = $state(untrack(() => stops.find((s) => s.id === value)?.name ?? ''));
  let open = $state(false);
  let active = $state(0);
  let typed = $state(false);

  // Follow outside changes (swap, URL, stops arriving) unless the user is mid-typing.
  $effect(() => {
    const picked = stops.find((s) => s.id === value);
    if (!untrack(() => typed)) text = picked?.name ?? (value === null ? '' : untrack(() => text));
  });

  const matches = $derived.by(() => {
    const query = fold(text);
    const picked = stops.find((s) => s.id === value);
    // A picked stop shows the whole list again when reopened, so changing it is one click.
    if (!query || (picked && !typed)) return stops;
    const starts = stops.filter((s) => fold(s.name).startsWith(query));
    const contains = stops.filter(
      (s) => !fold(s.name).startsWith(query) && fold(s.name).includes(query)
    );
    return [...starts, ...contains];
  });

  function pick(stop: Stop) {
    value = stop.id;
    text = stop.name;
    typed = false;
    open = false;
    onpick?.();
  }

  function commit() {
    // Leaving the field with an exact (or the only) match picks it; anything else clears it.
    const query = fold(text);
    if (!typed) return;
    const exact = stops.find((s) => fold(s.name) === query);
    const only = matches.length === 1 ? matches[0] : null;
    const chosen = exact ?? only;
    if (chosen && query) pick(chosen);
    else if (!query) value = null;
    typed = false;
  }

  function keydown(event: KeyboardEvent) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) {
        open = true;
        active = 0;
        return;
      }
      const step = event.key === 'ArrowDown' ? 1 : -1;
      active = (active + step + matches.length) % Math.max(matches.length, 1);
    } else if (event.key === 'Enter' && open && matches[active]) {
      event.preventDefault();
      pick(matches[active]);
    } else if (event.key === 'Escape' && open) {
      event.preventDefault();
      open = false;
    }
  }

  $effect(() => {
    if (!open) return;
    document.getElementById(`${id}-opt-${active}`)?.scrollIntoView({ block: 'nearest' });
  });

  export function focus() {
    input?.focus();
  }
</script>

<div class="picker" class:invalid={!!error}>
  <label for={id} class="picker-label">{label}</label>
  <input
    bind:this={input}
    {id}
    type="text"
    role="combobox"
    autocomplete="off"
    spellcheck="false"
    aria-autocomplete="list"
    aria-expanded={open}
    aria-controls={`${id}-list`}
    aria-activedescendant={open && matches[active] ? `${id}-opt-${active}` : undefined}
    aria-invalid={error ? true : undefined}
    aria-describedby={error ? `${id}-error` : undefined}
    {placeholder}
    bind:value={text}
    oninput={() => {
      typed = true;
      open = true;
      active = 0;
    }}
    onfocus={(event) => {
      open = true;
      active = Math.max(
        0,
        matches.findIndex((s) => s.id === value)
      );
      event.currentTarget.select();
    }}
    onblur={() => {
      // Let a click on an option land before the list closes.
      setTimeout(() => {
        open = false;
        commit();
      }, 120);
    }}
    onkeydown={keydown}
  />
  <input type="hidden" {name} value={value ?? ''} />
  {#if open}
    <ul class="options" id={`${id}-list`} role="listbox" aria-label={label}>
      {#each matches as stop, index (stop.id)}
        <li
          id={`${id}-opt-${index}`}
          role="option"
          aria-selected={index === active}
          class:active={index === active}
          onpointerdown={(event) => {
            event.preventDefault();
            pick(stop);
          }}
          onpointermove={() => (active = index)}
        >
          <span class="option-name">{stop.name}</span>
          <span class="option-lines" aria-hidden="true">
            {#each linesOf(stop.name) as line (line)}<LineBadge id={line} size="sm" />{/each}
          </span>
        </li>
      {:else}
        <li class="empty" role="option" aria-selected="false" aria-disabled="true">
          Žádná zastávka neodpovídá „{text}“
        </li>
      {/each}
    </ul>
  {/if}
  {#if error}<p class="error" id={`${id}-error`}>{error}</p>{/if}
</div>

<style>
  .picker {
    position: relative;
    display: grid;
    gap: 0.25rem;
    min-width: 0;
  }
  .picker-label {
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  input[type='text'] {
    width: 100%;
    min-height: 3rem;
    padding: 0 0.875rem;
    border: 1px solid var(--input);
    border-radius: var(--radius-md);
    background: var(--background);
    color: var(--foreground);
    font: inherit;
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
  }
  input[type='text']::placeholder {
    color: var(--muted-foreground);
    font-weight: var(--weight-regular);
  }
  input[type='text']:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 1px;
  }
  .invalid input[type='text'] {
    border-color: var(--destructive);
  }
  .error {
    color: var(--destructive);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .options {
    position: absolute;
    top: calc(100% + 0.25rem);
    left: 0;
    right: 0;
    z-index: 20;
    max-height: 18rem;
    overflow-y: auto;
    padding: 0.25rem;
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
    background: var(--popover);
    color: var(--popover-foreground);
    box-shadow: var(--shadow-pop);
  }
  li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem;
    min-height: 2.75rem;
    padding: 0.375rem 0.625rem;
    border-radius: var(--radius-sm);
    cursor: pointer;
  }
  li.active {
    background: var(--accent);
    color: var(--accent-foreground);
  }
  .option-name {
    font-weight: var(--weight-strong);
  }
  .option-lines {
    display: inline-flex;
    gap: 0.25rem;
  }
  .empty {
    color: var(--muted-foreground);
    cursor: default;
  }
</style>
