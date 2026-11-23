<script lang="ts">
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import type { ResolvedPathname } from '$app/types';
  import { ArrowLeftRight } from '@lucide/svelte';
  import type { Attachment } from 'svelte/attachments';
  import { LINES, LINE_IDS, linesOf, type LineId } from './lines.svelte';
  import BusIllustration from './BusIllustration.svelte';
  import StreetScenery from './StreetScenery.svelte';

  let {
    compact = false,
    highlight = 0,
    onhover
  }: {
    compact?: boolean;
    /** Pointer or focus on a card (null when it leaves), e.g. to light the line up on a map. */
    onhover?: (id: LineId | null) => void;
    /** Bump to make the cards pulse one after another, e.g. after jumping to the list. */
    highlight?: number;
  } = $props();

  let flashing = $state(false);
  $effect(() => {
    if (!highlight) return;
    flashing = false;
    const frame = requestAnimationFrame(() => (flashing = true));
    const done = setTimeout(() => (flashing = false), 1800);
    return () => {
      cancelAnimationFrame(frame);
      clearTimeout(done);
    };
  });

  /**
   * Street scenes: parallax seed and a bob offset so the buses differ. Lines come from the API,
   * so each card takes a scene by its position; the bus livery is always the line's own colour.
   */
  const SCENES = [
    { seed: 11, delay: '0s' },
    { seed: 23, delay: '-0.25s' },
    { seed: 37, delay: '-0.5s' }
  ];

  const CRUISE = 1;
  const HURRY = 2.8;
  const DEPART = 7;

  const lines = $derived(
    LINE_IDS.map((id, index) => {
      const stops = LINES[id].stops;
      const transfers = [...new Set(stops.flatMap(linesOf))].filter((other) => other !== id).sort();
      return {
        id,
        apiId: LINES[id].apiId,
        from: stops[0] ?? '',
        to: stops[stops.length - 1] ?? '',
        count: stops.length,
        transfers,
        stripe: LINES[id].color,
        scene: index % SCENES.length,
        ...SCENES[index % SCENES.length]
      };
    })
  );

  let leaving = $state<LineId | null>(null);

  const reducedMotion = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

  /**
   * Eases the playback rate of every animation inside the card. Changing the rate (instead of the
   * CSS duration) keeps each layer at its current position, so the street speeds up in place.
   */
  function rateControl(node: HTMLElement) {
    let rate = CRUISE;
    let frame = 0;
    const apply = () => {
      for (const animation of node.getAnimations({ subtree: true })) {
        if (!(animation instanceof CSSTransition)) animation.playbackRate = rate;
      }
    };
    return {
      to(target: number, ms = 450) {
        cancelAnimationFrame(frame);
        const from = rate;
        const start = performance.now();
        const step = (now: number) => {
          const t = Math.min(1, (now - start) / ms);
          rate = from + (target - from) * (1 - (1 - t) ** 3);
          apply();
          if (t < 1) frame = requestAnimationFrame(step);
        };
        frame = requestAnimationFrame(step);
      },
      stop: () => cancelAnimationFrame(frame)
    };
  }

  const speedOnHover: Attachment<HTMLElement> = (node) => {
    const control = rateControl(node);
    const hurry = () => control.to(HURRY);
    const cruise = () => {
      if (!node.classList.contains('leaving')) control.to(CRUISE);
    };
    const depart = () => control.to(DEPART, 350);
    node.addEventListener('pointerenter', hurry);
    node.addEventListener('pointerleave', cruise);
    node.addEventListener('focusin', hurry);
    node.addEventListener('focusout', cruise);
    node.addEventListener('depart', depart);
    return () => {
      control.stop();
      node.removeEventListener('pointerenter', hurry);
      node.removeEventListener('pointerleave', cruise);
      node.removeEventListener('focusin', hurry);
      node.removeEventListener('focusout', cruise);
      node.removeEventListener('depart', depart);
    };
  };

  /** The bus drives off the card before the stops page opens; plain navigation otherwise. */
  function drive(event: MouseEvent, id: LineId, href: ResolvedPathname) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey)
      return;
    if (reducedMotion() || leaving) return;
    event.preventDefault();
    leaving = id;
    (event.currentTarget as HTMLElement).dispatchEvent(new Event('depart'));
    setTimeout(() => void goto(href).finally(() => (leaving = null)), 520);
  }
</script>

<ul class="line-cards" class:compact class:flashing>
  {#each lines as line, index (line.id)}
    {@const href = resolve('/lines/[id]', { id: String(line.apiId) })}
    <li>
      <a
        {href}
        class="card scene-{line.scene}"
        class:leaving={leaving === line.id}
        aria-label={`Autobusová linka ${line.id}, ${line.from} – ${line.to}, oba směry, ${line.count} zastávek${line.transfers.length ? `, přestup na ${line.transfers.join(' a ')}` : ''}`}
        style:--line={LINES[line.id].color}
        style:--line-ink={LINES[line.id].ink}
        style:--bus-delay={line.delay}
        style:--index={index}
        onclick={(event) => drive(event, line.id, href)}
        onpointerenter={() => onhover?.(line.id)}
        onpointerleave={() => onhover?.(null)}
        onfocus={() => onhover?.(line.id)}
        onblur={() => onhover?.(null)}
        {@attach speedOnHover}
      >
        <StreetScenery seed={line.seed} scale={compact ? 0.5 : 1} road={compact ? 16 : 30} />
        <span class="road" aria-hidden="true"><span class="lane"></span></span>
        <span class="bus"><BusIllustration stripe={line.stripe} /></span>

        <span class="head">
          <span class="letter">{line.id}</span>
          <span class="facts">
            <span class="count tabular">Autobus · {line.count} zastávek</span>
            {#if line.transfers.length}
              <span class="transfers">
                Přestup na
                {#each line.transfers as other (other)}
                  <span
                    class="mini"
                    style:background={LINES[other].color}
                    style:color={LINES[other].ink}>{other}</span
                  >
                {/each}
              </span>
            {/if}
          </span>
        </span>
        <span class="route">
          <span class="from">{line.from} <ArrowLeftRight class="size-4" aria-hidden="true" /></span>
          <span class="to">{line.to}</span>
        </span>
      </a>
    </li>
  {/each}
</ul>

<style>
  .line-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(17rem, 1fr));
    gap: 1.25rem;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  /* Illustration colours come from the line's own brand colour (--line, --line-ink), so the
     card for line A always matches its badge; shades are mixes of it, the same in both themes. */
  .card {
    --card-bg: var(--line);
    --card-ink: var(--line-ink);
    --letter-bg: var(--board-bg);
    --letter-ink: var(--line);
    --road: color-mix(in oklch, var(--line) 55%, black);
    --kerb: color-mix(in oklch, var(--line) 80%, black);
    --scene-cloud: color-mix(in oklch, white 35%, transparent);
    --scene-far: color-mix(in oklch, white 28%, transparent);
    --scene-mid: color-mix(in oklch, var(--line) 90%, black);
    --scene-window: color-mix(in oklch, white 60%, transparent);
    --scene-near: color-mix(in oklch, var(--line) 75%, black);
  }

  .card {
    --road-h: 30px;
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    height: 15.5rem;
    padding: 1.25rem;
    overflow: hidden;
    border-radius: var(--radius);
    background: var(--card-bg);
    color: var(--card-ink);
    transition: transform var(--motion-normal) var(--ease-out);
  }
  .card:hover {
    transform: translateY(-4px);
  }
  /* "Look here": each card lifts and rings in turn. */
  .flashing .card {
    animation: flash var(--motion-xslow) var(--ease-out) calc(var(--index) * var(--motion-fast))
      both;
  }
  @keyframes flash {
    30% {
      transform: translateY(-6px) scale(1.02);
      box-shadow:
        0 0 0 4px var(--background),
        0 0 0 7px var(--card-bg);
    }
  }
  .card:focus-visible {
    outline: 3px solid var(--ring);
    outline-offset: 3px;
  }

  .road {
    position: absolute;
    inset: auto 0 0;
    height: var(--road-h);
    overflow: hidden;
    background: var(--road);
    border-top: 3px solid var(--kerb);
  }
  .lane {
    position: absolute;
    top: calc(50% - 2px);
    left: 0;
    width: calc(100% + 26px);
    height: 2px;
    background: repeating-linear-gradient(
      90deg,
      rgb(255 255 255 / 0.65) 0 14px,
      transparent 14px 26px
    );
    animation: lane var(--loop-fast) linear infinite;
  }
  @keyframes lane {
    to {
      transform: translateX(-26px);
    }
  }
  .bus {
    position: absolute;
    right: 1.125rem;
    bottom: 2px;
    width: min(230px, 62%);
  }
  /* On click the bus pulls away and leaves the card to the right. */
  .leaving .bus {
    transition: transform var(--motion-slow) var(--ease-in);
    transform: translateX(calc(100% + 2rem));
  }

  .head,
  .route {
    position: relative;
  }
  .head {
    display: flex;
    align-items: center;
    gap: 0.625rem;
  }
  .letter {
    display: grid;
    place-items: center;
    width: 2.75rem;
    height: 2.75rem;
    border-radius: var(--radius);
    background: var(--letter-bg);
    color: var(--letter-ink);
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
    line-height: 1;
  }
  .facts {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .transfers {
    display: flex;
    align-items: center;
    gap: 0.3125rem;
  }
  .mini {
    display: inline-grid;
    place-items: center;
    width: 1.375rem;
    height: 1.375rem;
    border-radius: var(--radius);
    box-shadow: var(--shadow-pop);
    font-size: var(--fs-xs);
    font-weight: var(--font-display-weight);
    line-height: 1;
  }
  .route {
    display: flex;
    flex-direction: column;
    gap: 0.125rem;
  }
  .from {
    display: flex;
    align-items: center;
    gap: 0.375rem;
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
    opacity: 0.85;
  }
  .to {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
    line-height: 1.1;
  }

  /* Compact: the nav menu. Same street at half size, text on the left, bus on the right. */
  .compact {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.625rem;
  }
  .compact .card {
    --road-h: 16px;
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    grid-template-rows: auto auto;
    align-content: center;
    column-gap: 0.75rem;
    row-gap: 0.375rem;
    height: 6.75rem;
    padding: 0.75rem 0.875rem 1.375rem;
    border-radius: var(--radius);
  }
  .compact .card:hover {
    transform: none;
  }
  .compact .card :global(.scenery) {
    mask-image: linear-gradient(90deg, transparent 30%, #000 62%);
  }
  .compact .head {
    display: contents;
  }
  .compact .letter {
    grid-row: 1 / 3;
    align-self: center;
    width: 2.25rem;
    height: 2.25rem;
    font-size: var(--fs-lg);
  }
  .compact .route {
    grid-column: 2;
    grid-row: 1;
  }
  .compact .facts {
    grid-column: 2;
    grid-row: 2;
  }
  .compact .count {
    display: none;
  }
  .compact .mini {
    width: 1.125rem;
    height: 1.125rem;
    font-size: var(--fs-2xs);
  }
  .compact .from {
    font-size: var(--fs-sm);
  }
  .compact .to {
    font-size: var(--fs-base);
  }
  .compact .bus {
    right: 0.75rem;
    bottom: 1px;
    width: 128px;
  }

  @media (prefers-reduced-motion: reduce) {
    .lane {
      animation: none;
    }
    .card,
    .card:hover {
      transition: none;
      transform: none;
    }
    .flashing .card {
      animation: none;
      box-shadow:
        0 0 0 4px var(--background),
        0 0 0 7px var(--card-bg);
    }
  }
</style>
