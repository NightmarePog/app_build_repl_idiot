<script lang="ts">
  /**
   * A time as split-flap tiles, the way a Solari station board shows it. Each digit flips in
   * when the value changes; the wrapper carries the readable value for assistive tech.
   */
  let {
    time,
    dayOffset = 0,
    size = 'md',
    delay = 0,
    tone = 'board',
    showDay = true
  }: {
    time: string;
    dayOffset?: number;
    size?: 'sm' | 'md' | 'lg';
    /** Stagger in ms so a board fills row by row. */
    delay?: number;
    tone?: 'board' | 'paper';
    /** Word the next-day shift under the tiles; off where the context already says it. */
    showDay?: boolean;
  } = $props();

  const chars = $derived(time.split(''));
</script>

<span
  class="flap {size} {tone}"
  class:shifted={dayOffset > 0 && showDay}
  role="img"
  aria-label={dayOffset > 0 ? `${time}, následující den` : time}
>
  {#each chars as char, index (index)}
    {#if char === ':'}
      <span class="colon" aria-hidden="true">:</span>
    {:else}
      {#key char}
        <span class="tile" aria-hidden="true" style:animation-delay={`${delay + index * 45}ms`}
          >{char}</span
        >
      {/key}
    {/if}
  {/each}
  {#if dayOffset > 0 && showDay}
    <span class="next-day" aria-hidden="true">po půlnoci</span>
  {/if}
</span>

<style>
  .flap {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 0.125em;
    font-variant-numeric: tabular-nums;
    font-weight: var(--weight-strong);
    line-height: 1;
    perspective: 400px;
  }
  .shifted {
    margin-bottom: 0.9em;
  }
  .sm {
    font-size: var(--fs-base);
  }
  .md {
    font-size: var(--fs-lg);
  }
  .lg {
    font-size: var(--fs-2xl);
  }
  .tile {
    position: relative;
    display: inline-grid;
    place-items: center;
    width: 1.05em;
    height: 1.45em;
    border-radius: var(--radius-sm);
    transform-origin: 50% 50%;
    animation: flip var(--motion-slow) var(--ease-spring-out) both;
  }
  /* The hinge: a hairline across the middle of every tile. */
  .tile::after {
    content: '';
    position: absolute;
    inset: 50% 0 auto;
    height: 1px;
  }
  .board .tile {
    background: linear-gradient(var(--board-tile-top) 0 50%, var(--board-tile-bottom) 50% 100%);
    color: var(--board-fg);
    box-shadow: 0 1px 0 var(--board-shadow);
  }
  .board .tile::after {
    background: var(--board-shadow);
  }
  .paper .tile {
    background: linear-gradient(var(--card) 0 50%, var(--secondary) 50% 100%);
    color: var(--foreground);
    box-shadow: inset 0 0 0 1px var(--border);
  }
  .paper .tile::after {
    background: var(--border);
  }
  .colon {
    width: 0.3em;
    text-align: center;
    opacity: 0.7;
  }
  /* A date-style caption under the tiles, not a badge: it reads as "the next day". */
  .next-day {
    position: absolute;
    top: 100%;
    right: 0;
    margin-top: 0.2em;
    font-size: max(0.4em, var(--fs-2xs));
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
    white-space: nowrap;
    opacity: 0.75;
  }
  @keyframes flip {
    0% {
      transform: rotateX(-90deg);
      filter: brightness(1.6);
    }
    60% {
      transform: rotateX(12deg);
    }
    100% {
      transform: rotateX(0);
      filter: none;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .tile {
      animation: none;
    }
  }
</style>
