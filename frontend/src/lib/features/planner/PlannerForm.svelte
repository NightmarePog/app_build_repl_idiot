<script lang="ts">
  import { untrack } from 'svelte';
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { ArrowDownUp, Clock3, Search } from '@lucide/svelte';
  import { Button } from '$lib/ui/button';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import { loadNetwork } from '$lib/features/network/lines.svelte';
  import { clock, nowMinutes } from '$lib/features/timetable/time';
  import StopPicker from './StopPicker.svelte';
  import { normalizeTime, plannerHref, type Mode, type Query } from './planner';

  /** From, to, when: the IDOS-style search form, on the planner page and the home page. */
  let {
    query = { from: null, to: null, time: null, mode: 'departure' },
    compact = false,
    headingId
  }: { query?: Query; compact?: boolean; headingId?: string } = $props();

  let from = $state<number | null>(untrack(() => query.from));
  let to = $state<number | null>(untrack(() => query.to));
  /** Always a 24 h time; the current time when the URL has none, like IDOS. */
  let time = $state(untrack(() => query.time ?? clock(nowMinutes())));
  let mode = $state<Mode>(untrack(() => query.mode));
  let errors = $state<{ from?: string; to?: string; time?: string }>({});
  let toPicker: StopPicker | undefined = $state();

  // A new URL (back button, a link) resets the form to it.
  $effect(() => {
    from = query.from;
    to = query.to;
    time = query.time ?? clock(nowMinutes());
    mode = query.mode;
    errors = {};
  });

  $effect(() => {
    void loadStops();
    void loadNetwork();
  });

  const stops = $derived(
    [...(stopsList() ?? [])].sort((a, b) => a.name.localeCompare(b.name, 'cs'))
  );

  function swap() {
    [from, to] = [to, from];
    errors = {};
  }

  function now() {
    time = clock(nowMinutes());
    mode = 'departure';
    errors.time = undefined;
  }

  function submit(event: SubmitEvent) {
    event.preventDefault();
    errors = {};
    if (from === null) errors.from = 'Vyberte zastávku, odkud jedete.';
    if (to === null) errors.to = 'Vyberte zastávku, kam jedete.';
    else if (from === to) errors.to = 'Cíl musí být jiná zastávka než start.';
    const picked = normalizeTime(time);
    if (picked === null) errors.time = 'Zadejte čas ve tvaru 07:30.';
    if (errors.from || errors.to || errors.time) {
      const field = errors.from ? 'from' : errors.to ? 'to' : 'time';
      document.getElementById(`${idBase}-${field}`)?.focus();
      return;
    }
    // The current minute stays "now", so results keep their countdowns.
    const pinned = picked === clock(nowMinutes()) && mode === 'departure' ? null : picked;
    void goto(plannerHref({ from, to, time: pinned, mode }), { keepFocus: true });
  }

  const idBase = $derived(compact ? 'hero-plan' : 'plan');
</script>

<form
  class="planner"
  class:compact
  method="GET"
  action={resolve('/spojeni')}
  onsubmit={submit}
  aria-labelledby={headingId}
  novalidate
>
  <div class="places">
    <StopPicker
      id={`${idBase}-from`}
      name="z"
      label="Odkud"
      placeholder="Výchozí zastávka"
      {stops}
      bind:value={from}
      error={errors.from}
      onpick={() => {
        errors.from = undefined;
        if (to === null) toPicker?.focus();
      }}
    />
    <button
      type="button"
      class="swap"
      onclick={swap}
      aria-label="Prohodit odkud a kam"
      title="Prohodit odkud a kam"
    >
      <ArrowDownUp class="size-5" aria-hidden="true" />
    </button>
    <StopPicker
      bind:this={toPicker}
      id={`${idBase}-to`}
      name="do"
      label="Kam"
      placeholder="Cílová zastávka"
      {stops}
      bind:value={to}
      error={errors.to}
      onpick={() => (errors.to = undefined)}
    />
  </div>

  <div class="when">
    <fieldset class="mode">
      <legend class="sr-only">Čas platí pro</legend>
      <label class:checked={mode === 'departure'}>
        <input type="radio" name="podle" value="departure" bind:group={mode} />
        <span>Odjezd</span>
      </label>
      <label class:checked={mode === 'arrival'}>
        <input type="radio" name="podle" value="arrival" bind:group={mode} />
        <span>Příjezd</span>
      </label>
    </fieldset>
    <label class="time">
      <span class="sr-only">Čas {mode === 'arrival' ? 'příjezdu' : 'odjezdu'}</span>
      <Clock3 class="size-4" aria-hidden="true" />
      <input
        id={`${idBase}-time`}
        type="text"
        name="cas"
        inputmode="numeric"
        autocomplete="off"
        maxlength="5"
        placeholder="07:30"
        pattern="([01]?[0-9]|2[0-3]):[0-5][0-9]"
        bind:value={time}
        aria-invalid={errors.time ? 'true' : undefined}
        aria-describedby={errors.time ? `${idBase}-time-error` : undefined}
        oninput={() => (errors.time = undefined)}
      />
    </label>
    <Button type="button" variant="outline" class="now" onclick={now}>Teď</Button>
    {#if errors.time}
      <span id={`${idBase}-time-error`} class="time-error" role="alert">{errors.time}</span>
    {/if}
  </div>

  <Button type="submit" size="lg" class="submit">
    <Search aria-hidden="true" /> Vyhledat spojení
  </Button>
</form>

<style>
  .planner {
    display: grid;
    gap: 1rem;
    padding: 1.25rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
    box-shadow: var(--shadow-sm);
  }
  .places {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    align-items: start;
    gap: 0.75rem;
  }
  .swap {
    display: grid;
    place-items: center;
    width: 2.75rem;
    height: 2.75rem;
    margin-top: 1.5rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--background);
    color: var(--foreground);
    cursor: pointer;
    transition: transform var(--motion-normal) var(--ease-spring-out);
  }
  .swap :global(svg) {
    transform: rotate(90deg);
  }
  .swap:hover {
    border-color: var(--input);
  }
  .swap:active {
    transform: rotate(180deg);
  }
  .swap:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }
  .when {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem 0.75rem;
  }
  .mode {
    display: inline-flex;
    padding: 0.2rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--muted);
  }
  .mode label {
    position: relative;
    display: inline-flex;
    align-items: center;
    min-height: 2.5rem;
    padding: 0 0.875rem;
    border-radius: calc(var(--radius) - 2px);
    color: var(--muted-foreground);
    font-weight: var(--weight-strong);
    cursor: pointer;
  }
  .mode label.checked {
    background: var(--background);
    color: var(--foreground);
    box-shadow: var(--shadow-sm);
  }
  .mode input {
    position: absolute;
    opacity: 0;
    inset: 0;
    cursor: pointer;
  }
  .mode label:has(input:focus-visible) {
    outline: 2px solid var(--ring);
    outline-offset: 1px;
  }
  .time {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    min-height: 2.75rem;
    padding: 0 0.75rem;
    border: 1px solid var(--input);
    border-radius: var(--radius);
    background: var(--background);
    color: var(--muted-foreground);
  }
  .time input {
    border: 0;
    background: transparent;
    color: var(--foreground);
    font: inherit;
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
    font-variant-numeric: tabular-nums;
  }
  .time:focus-within {
    outline: 2px solid var(--ring);
    outline-offset: 1px;
  }
  .time input:focus {
    outline: none;
  }
  .when :global(.now) {
    min-height: 2.75rem;
  }
  .time input {
    width: 4.5ch;
  }
  .time-error {
    flex-basis: 100%;
    color: var(--destructive);
    font-size: var(--fs-sm);
  }
  .planner :global(.submit) {
    justify-self: start;
  }
  @media (max-width: 639px) {
    .planner {
      padding: 1rem;
    }
    .places {
      grid-template-columns: minmax(0, 1fr) auto;
      grid-template-areas: 'from swap' 'to swap';
      column-gap: 0.5rem;
    }
    .places > :global(:first-child) {
      grid-area: from;
    }
    .places > :global(:last-child) {
      grid-area: to;
    }
    .swap {
      grid-area: swap;
      align-self: center;
      margin-top: 1.25rem;
    }
    .swap :global(svg) {
      transform: none;
    }
    .planner :global(.submit) {
      justify-self: stretch;
    }
    .when {
      gap: 0.375rem;
    }
    .mode label {
      padding: 0 0.5rem;
    }
    .time {
      padding: 0 0.5rem;
    }
    .when :global(.now) {
      padding-inline: 0.625rem;
    }
  }
  .compact {
    padding: 1rem;
  }
  .compact .places {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas: 'from swap' 'to swap';
    column-gap: 0.5rem;
  }
  .compact .places > :global(:first-child) {
    grid-area: from;
  }
  .compact .places > :global(:last-child) {
    grid-area: to;
  }
  .compact .swap {
    grid-area: swap;
    align-self: center;
    margin-top: 1.25rem;
  }
  .compact .swap :global(svg) {
    transform: none;
  }
</style>
