<script lang="ts">
  /** Side view of a low-floor city bus; `stripe` is the livery band in the line's colour. */
  let { stripe }: { stripe: string } = $props();

  // Five wheel bolts, so the spin is visible.
  const bolts = [0, 72, 144, 216, 288].map((deg) => {
    const rad = ((deg - 90) * Math.PI) / 180;
    return { x: +(5.5 * Math.cos(rad)).toFixed(2), y: +(5.5 * Math.sin(rad)).toFixed(2) };
  });
</script>

<svg class="bus" viewBox="0 0 300 124" aria-hidden="true">
  <ellipse cx="150" cy="118" rx="130" ry="5" fill="rgb(0 0 0 / 0.3)" />
  <g class="body">
    <rect x="56" y="4" width="84" height="12" rx="4" fill="#d8d6cf" />
    <rect x="176" y="6" width="44" height="10" rx="4" fill="#d8d6cf" />
    <path
      d="M16 14h246c16 0 26 10 28 26l4 58c0 6-3 9-9 9H14c-5 0-8-3-8-8V24c0-6 4-10 10-10z"
      fill="#f4f3ef"
    />
    <path d="M6 88h288l1 10c0 6-3 9-9 9H14c-5 0-8-3-8-8z" fill="#d6d4cc" />
    <rect x="6" y="66" width="288" height="9" fill={stripe} />
    <g fill="#22282d">
      <rect x="20" y="24" width="40" height="34" rx="4" />
      <rect x="64" y="24" width="40" height="34" rx="4" />
      <rect x="146" y="24" width="40" height="34" rx="4" />
      <rect x="190" y="24" width="36" height="34" rx="4" />
    </g>
    <path
      d="M28 55l16-28M72 55l16-28M154 55l16-28M198 55l16-28"
      stroke="rgb(255 255 255 / 0.14)"
      stroke-width="6"
    />
    {#each [108, 230] as x (x)}
      {@const w = x === 108 ? 34 : 30}
      <rect {x} y="22" width={w} height="85" rx="3" fill="#2b3237" />
      <rect x={x + 3} y="26" width={w / 2 - 4} height="52" rx="2" fill="#3a4349" />
      <rect x={x + w / 2 + 1} y="26" width={w / 2 - 4} height="52" rx="2" fill="#3a4349" />
    {/each}
    <path d="M264 22h10c8 0 13 5 14 13l2 29h-26z" fill="#22282d" />
    <rect x="284" y="82" width="10" height="6" rx="2" fill="#ffe08a" />
    <rect x="6" y="80" width="5" height="12" rx="1.5" fill="#d9483b" />
    <path d="M52 108a20 20 0 0 1 40 0zM216 108a20 20 0 0 1 40 0z" fill="#1d2226" />
  </g>
  {#each [72, 236] as cx (cx)}
    <g class="wheel">
      <circle {cx} cy="106" r="15" fill="#16191c" />
      <circle {cx} cy="106" r="8.5" fill="#b8bdc1" />
      <circle {cx} cy="106" r="3" fill="#5f666c" />
      {#each bolts as bolt, i (i)}
        <circle cx={cx + bolt.x} cy={106 + bolt.y} r="1.5" fill="#5f666c" />
      {/each}
    </g>
  {/each}
</svg>

<style>
  .bus {
    display: block;
    width: 100%;
    height: auto;
  }
  .body {
    animation: bob 0.7s ease-in-out var(--bus-delay, 0s) infinite;
  }
  .wheel {
    transform-box: fill-box;
    transform-origin: center;
    animation: spin var(--bus-spin, 0.7s) linear infinite;
  }
  @keyframes bob {
    50% {
      transform: translateY(-1.5px);
    }
  }
  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .body,
    .wheel {
      animation: none;
    }
  }
</style>
