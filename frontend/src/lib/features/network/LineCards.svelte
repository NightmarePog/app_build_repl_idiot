<script lang="ts">
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { ArrowRight } from '@lucide/svelte';
  import type { Attachment } from 'svelte/attachments';
  import { reveal } from '$lib/motion';
  import { LINES, LINE_IDS, linesOf, type LineId } from './lines';
  import BusIllustration from './BusIllustration.svelte';
  import StreetScenery from './StreetScenery.svelte';

  let {
    compact = false,
    highlight = 0
  }: {
    compact?: boolean;
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

  /** Per-line street: bus livery, parallax seed and a bob offset so the buses differ. */
  const SCENE: Record<LineId, { seed: number; stripe: string; delay: string }> = {
    A: { seed: 11, stripe: '#0070bb', delay: '0s' },
    B: { seed: 23, stripe: '#2f9e5a', delay: '-0.25s' },
    C: { seed: 37, stripe: '#23898a', delay: '-0.5s' }
  };

  const CRUISE = 1;
  const HURRY = 2.8;
  const DEPART = 7;

  const lines = LINE_IDS.map((id) => {
    const stops = LINES[id].stops;
    const transfers = [...new Set(stops.flatMap(linesOf))].filter((other) => other !== id).sort();
    return { id, from: stops[0], to: stops[stops.length - 1], count: stops.length, transfers };
  });

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
  function drive(event: MouseEvent, id: LineId, href: string) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey)
      return;
    if (reducedMotion() || leaving) return;
    event.preventDefault();
    leaving = id;
    (event.currentTarget as HTMLElement).dispatchEvent(new Event('depart'));
    // eslint-disable-next-line svelte/no-navigation-without-resolve -- href is resolved above.
    setTimeout(() => void goto(href).finally(() => (leaving = null)), 520);
  }
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -- The route is resolved; only a query is appended. -->
<ul class="line-cards" class:compact class:flashing>
  {#each lines as line, index (line.id)}
    {@const href = `${resolve('/stops')}?line=${line.id}`}
    <li {@attach !compact && reveal({ delay: index * 0.09 })}>
      <a
        {href}
        class="card line-{line.id.toLowerCase()}"
        class:leaving={leaving === line.id}
        aria-label={`Linka ${line.id}, ${line.from} do ${line.to}, ${line.count} zastávek, přestup na ${line.transfers.join(' a ')}`}
        style:--bus-delay={SCENE[line.id].delay}
        style:--index={index}
        onclick={(event) => drive(event, line.id, href)}
        {@attach speedOnHover}
      >
        <StreetScenery
          seed={SCENE[line.id].seed}
          scale={compact ? 0.5 : 1}
          road={compact ? 16 : 30}
        />
        <span class="road" aria-hidden="true"><span class="lane"></span></span>
        <span class="bus"><BusIllustration stripe={SCENE[line.id].stripe} /></span>

        <span class="head">
          <span class="letter">{line.id}</span>
          <span class="facts">
            <span class="count tabular">{line.count} zastávek</span>
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
          </span>
        </span>
        <span class="route">
          <span class="from"
            >{line.from} <ArrowRight size={15} strokeWidth={3} aria-hidden="true" /></span
          >
          <span class="to">{line.to}</span>
        </span>
      </a>
    </li>
  {/each}
</ul>

<!-- eslint-enable svelte/no-navigation-without-resolve -->

<style>
  .line-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(17rem, 1fr));
    gap: 1.25rem;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  /* Fixed illustration colours: the cards look the same in both themes. */
  .line-a {
    --card-bg: #0070bb;
    --card-ink: #ffffff;
    --letter-bg: #121413;
    --letter-ink: #2b90d9;
    --road: #0b3f66;
    --kerb: #3d86c0;
    --scene-cloud: rgb(255 255 255 / 0.16);
    --scene-far: rgb(255 255 255 / 0.15);
    --scene-mid: #0a62a3;
    --scene-window: rgb(255 214 120 / 0.55);
    --scene-near: #0b4f80;
  }
  .line-b {
    --card-bg: #a4f8bc;
    --card-ink: #121413;
    --letter-bg: #121413;
    --letter-ink: #a4f8bc;
    --road: #35503f;
    --kerb: #6fc48a;
    --scene-cloud: rgb(255 255 255 / 0.6);
    --scene-far: rgb(255 255 255 / 0.45);
    --scene-mid: #8ee3a6;
    --scene-window: rgb(255 255 255 / 0.7);
    --scene-near: #5fb97c;
  }
  .line-c {
    --card-bg: #5cc8c9;
    --card-ink: #121413;
    --letter-bg: #121413;
    --letter-ink: #5cc8c9;
    --road: #24585a;
    --kerb: #3a9fa0;
    --scene-cloud: rgb(255 255 255 / 0.45);
    --scene-far: rgb(255 255 255 / 0.3);
    --scene-mid: #4bb3b4;
    --scene-window: rgb(255 255 255 / 0.6);
    --scene-near: #2f8d8e;
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
    border-radius: 1rem;
    background: var(--card-bg);
    color: var(--card-ink);
    transition: transform var(--motion-normal) var(--ease-out);
  }
  .card:hover {
    transform: translateY(-4px);
  }
  /* "Look here": each card lifts and rings in turn. */
  .flashing .card {
    animation: flash 0.9s var(--ease-out) calc(var(--index) * 140ms) both;
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
    animation: lane 0.39s linear infinite;
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
    transition: transform 0.5s cubic-bezier(0.55, 0, 0.9, 0.4);
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
    border-radius: 0.625rem;
    background: var(--letter-bg);
    color: var(--letter-ink);
    font-size: 1.625rem;
    font-weight: 800;
    line-height: 1;
  }
  .facts {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    font-size: 0.8125rem;
    font-weight: 600;
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
    border-radius: 0.25rem;
    box-shadow: 0 0 0 1.5px rgb(0 0 0 / 0.18);
    font-size: 0.75rem;
    font-weight: 800;
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
    font-size: 0.9375rem;
    font-weight: 600;
    opacity: 0.85;
  }
  .to {
    font-size: 1.3125rem;
    font-weight: 800;
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
    border-radius: 0.75rem;
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
    font-size: 1.25rem;
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
    font-size: 0.6875rem;
  }
  .compact .from {
    font-size: 0.8125rem;
  }
  .compact .to {
    font-size: 1.0625rem;
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
