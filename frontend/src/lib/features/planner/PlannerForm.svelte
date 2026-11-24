<script lang="ts">
  import { untrack } from 'svelte';
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { ArrowDownUp, Clock3, MapPin, Search } from '@lucide/svelte';
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
    headingId,
    onchange
  }: {
    query?: Query;
    compact?: boolean;
    headingId?: string;
    /** The stops as picked right now, before searching: the map follows them live. */
    onchange?: (picked: { from: number | null; to: number | null }) => void;
  } = $props();

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

  $effect(() => onchange?.({ from, to }));

  $effect(() => {
    void loadStops();
    void loadNetwork();
  });

  const stops = $derived(
    [...(stopsList() ?? [])].sort((a, b) => a.name.localeCompare(b.name, 'cs'))
  );

  /** The swap button turns half a turn per press, always the same way. */
  let turns = $state(0);
  function swap() {
    [from, to] = [to, from];
    turns += 1;
    errors = {};
  }

  function now() {
    time = clock(nowMinutes());
    mode = 'departure';
    errors.time = undefined;
  }

  /** "+15 min", "+1 h": moves the chosen time (or now), wrapping past midnight. */
  function later(minutes: number) {
    const picked = normalizeTime(time) ?? clock(nowMinutes());
    const [h, m] = picked.split(':').map(Number);
    time = clock((((h * 60 + m + minutes) % 1440) + 1440) % 1440);
    errors.time = undefined;
  }
  const isNow = $derived(mode === 'departure' && normalizeTime(time) === clock(nowMinutes()));

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
  method="GET"
  action={resolve('/connections')}
  onsubmit={submit}
  aria-labelledby={headingId}
  novalidate
>
  <!-- Route card: start ring, dashed rail, destination pin; swap sits on the seam. -->
  <div class="route" class:invalid={errors.from || errors.to}>
    <div class="rail" aria-hidden="true">
      <span class="start"></span>
      <span class="dash"></span>
      <MapPin class="end" size={18} strokeWidth={2.5} />
    </div>
    <div class="fields">
      <StopPicker
        id={`${idBase}-from`}
        name="z"
        label="Odkud"
        placeholder="Výchozí zastávka"
        inline
        {stops}
        bind:value={from}
        error={errors.from}
        onpick={() => {
          errors.from = undefined;
          if (to === null) toPicker?.focus();
        }}
      />
      <div class="seam"></div>
      <StopPicker
        bind:this={toPicker}
        id={`${idBase}-to`}
        name="do"
        label="Kam"
        placeholder="Cílová zastávka"
        inline
        {stops}
        bind:value={to}
        error={errors.to}
        onpick={() => (errors.to = undefined)}
      />
    </div>
    <button
      type="button"
      class="swap"
      onclick={swap}
      aria-label="Prohodit odkud a kam"
      title="Prohodit odkud a kam"
      style:rotate={`${turns * 180}deg`}
    >
      <ArrowDownUp size={18} aria-hidden="true" />
    </button>
  </div>

  <div class="when">
    <fieldset class="mode">
      <legend class="sr-only">Čas platí pro</legend>
      <span class="mode-pill" class:arrival={mode === 'arrival'} aria-hidden="true"></span>
      <label class:checked={mode === 'departure'}>
        <input type="radio" name="podle" value="departure" bind:group={mode} />
        <span>Odjezd</span>
      </label>
      <label class:checked={mode === 'arrival'}>
        <input type="radio" name="podle" value="arrival" bind:group={mode} />
        <span>Příjezd</span>
      </label>
    </fieldset>
    <label class="time" class:invalid={!!errors.time}>
      <span class="sr-only">Čas {mode === 'arrival' ? 'příjezdu' : 'odjezdu'}</span>
      <Clock3 size={16} aria-hidden="true" />
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
  </div>
  <div class="chips" role="group" aria-label="Rychlá volba času">
    <button type="button" class="chip" aria-pressed={isNow} onclick={now}>Teď</button>
    <button type="button" class="chip" onclick={() => later(15)}>+15 min</button>
    <button type="button" class="chip" onclick={() => later(60)}>+1 h</button>
  </div>
  {#if errors.time}
    <p id={`${idBase}-time-error`} class="time-error" role="alert">{errors.time}</p>
  {/if}

  <Button type="submit" size="lg" class="submit">
    <Search aria-hidden="true" /> Vyhledat spojení
  </Button>
</form>

<style>
  .planner {
    display: grid;
    gap: 0.75rem;
  }

  /* --- Route card --- */
  .route {
    position: relative;
    display: grid;
    grid-template-columns: 2.25rem minmax(0, 1fr);
    border: 1px solid var(--input);
    border-radius: calc(var(--radius) + 2px);
    background: var(--card);
    box-shadow: var(--shadow-sm);
    transition:
      border-color var(--motion-fast) var(--ease-out),
      box-shadow var(--motion-fast) var(--ease-out);
  }
  .route:focus-within {
    border-color: var(--ring);
    box-shadow: 0 0 0 3px color-mix(in oklch, var(--ring) 22%, transparent);
  }
  .route.invalid {
    border-color: var(--destructive);
  }
  .rail {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 1.6rem 0 1.45rem 0.75rem;
  }
  .start {
    flex: none;
    width: 0.75rem;
    height: 0.75rem;
    border: 2.5px solid var(--foreground);
    border-radius: 50%;
  }
  .dash {
    flex: 1;
    width: 0;
    margin: 0.3rem 0;
    border-left: 2px dashed var(--muted-foreground);
  }
  .rail :global(.end) {
    flex: none;
    color: var(--primary);
  }
  /* The right edge is the swap button's column: fields, clear buttons and lists end before it. */
  .fields {
    display: grid;
    min-width: 0;
    margin-right: 3.5rem;
  }
  .seam {
    height: 1px;
    background: var(--border);
  }
  .swap {
    position: absolute;
    top: 50%;
    right: 0.625rem;
    display: grid;
    place-items: center;
    width: 2.5rem;
    height: 2.5rem;
    margin-top: -1.25rem;
    border: 1px solid var(--input);
    border-radius: 50%;
    background: var(--background);
    color: var(--foreground);
    box-shadow: var(--shadow-sm);
    cursor: pointer;
    transition:
      rotate var(--motion-slow) var(--ease-spring-out, var(--ease-out)),
      border-color var(--motion-fast) var(--ease-out),
      background-color var(--motion-fast) var(--ease-out);
  }
  .swap:hover {
    border-color: var(--ring);
    background: var(--muted);
  }
  .swap:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }

  /* --- When --- */
  .when {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 0.5rem;
  }
  .mode {
    position: relative;
    display: grid;
    grid-template-columns: 1fr 1fr;
    padding: 0.25rem;
    border-radius: var(--radius);
    background: var(--muted);
  }
  .mode-pill {
    position: absolute;
    inset: 0.25rem auto 0.25rem 0.25rem;
    width: calc(50% - 0.25rem);
    border-radius: calc(var(--radius) - 2px);
    background: var(--background);
    box-shadow: var(--shadow-sm);
    transition: translate var(--motion-normal) var(--ease-out);
  }
  .mode-pill.arrival {
    translate: 100% 0;
  }
  .mode label {
    position: relative;
    display: grid;
    place-items: center;
    min-height: 2.5rem;
    padding: 0 0.75rem;
    color: var(--muted-foreground);
    font-weight: var(--weight-strong);
    cursor: pointer;
    transition: color var(--motion-fast) var(--ease-out);
  }
  .mode label.checked {
    color: var(--foreground);
  }
  .mode input {
    position: absolute;
    inset: 0;
    opacity: 0;
    cursor: pointer;
  }
  .mode label:has(input:focus-visible) {
    border-radius: calc(var(--radius) - 2px);
    outline: 2px solid var(--ring);
  }
  .time {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    min-height: 3rem;
    padding: 0 0.875rem;
    border: 1px solid var(--input);
    border-radius: var(--radius);
    background: var(--card);
    color: var(--muted-foreground);
    cursor: text;
    transition: border-color var(--motion-fast) var(--ease-out);
  }
  .time:focus-within {
    border-color: var(--ring);
    box-shadow: 0 0 0 3px color-mix(in oklch, var(--ring) 22%, transparent);
  }
  .time.invalid {
    border-color: var(--destructive);
  }
  .time input {
    width: 4.5ch;
    border: 0;
    background: transparent;
    color: var(--foreground);
    font: inherit;
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
    font-variant-numeric: tabular-nums;
  }
  .time input:focus {
    outline: none;
  }
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 0.375rem;
  }
  .chip {
    min-height: 2rem;
    padding: 0 0.75rem;
    border: 1px solid var(--border);
    border-radius: 999px;
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    font-variant-numeric: tabular-nums;
    cursor: pointer;
    transition:
      background-color var(--motion-fast) var(--ease-out),
      border-color var(--motion-fast) var(--ease-out),
      color var(--motion-fast) var(--ease-out);
  }
  .chip:hover {
    border-color: var(--input);
    color: var(--foreground);
  }
  .chip[aria-pressed='true'] {
    border-color: color-mix(in oklch, var(--primary) 45%, transparent);
    background: color-mix(in oklch, var(--primary) 14%, transparent);
    color: var(--foreground);
  }
  .chip:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }
  .time-error {
    color: var(--destructive);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .planner :global(.submit) {
    width: 100%;
    min-height: 3rem;
    margin-top: 0.25rem;
  }
  @media (prefers-reduced-motion: reduce) {
    .swap,
    .mode-pill {
      transition: none;
    }
  }
</style>
