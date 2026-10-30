<script lang="ts">
  /**
   * Parallax street behind a line card's bus: a far skyline, buildings with lit windows and a
   * near row of trees and lamps, each scrolling at its own speed. Shapes are generated from a
   * seed, so every line gets its own street but the same one on every render. Colours come from
   * the card (--scene-far, --scene-mid, --scene-window, --scene-near, --scene-cloud).
   * `scale` shrinks the whole street for small cards; `road` is the road height it stands on.
   */
  let { seed, scale = 1, road = 30 }: { seed: number; scale?: number; road?: number } = $props();

  const TILE = 600;

  function random(start: number) {
    let state = start >>> 0;
    return () => {
      state = (state + 0x6d2b79f5) >>> 0;
      let t = Math.imul(state ^ (state >>> 15), 1 | state);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  type Rect = { x: number; y: number; w: number; h: number };
  type Building = Rect & { roof: string | null; windows: Rect[] };
  type Prop =
    | { kind: 'tree'; x: number; crowns: { cx: number; cy: number; r: number }[] }
    | { kind: 'conifer'; x: number; h: number }
    | { kind: 'lamp'; x: number }
    | { kind: 'bush'; x: number; w: number }
    | { kind: 'sign'; x: number };

  function build(start: number) {
    const rnd = random(start);
    const between = (min: number, max: number) => min + rnd() * (max - min);
    const whole = (min: number, max: number) => Math.round(between(min, max));

    // Far skyline, 100 px tall: plain towers, some with an antenna.
    const far: Rect[] = [];
    for (let x = 0; x < TILE - 30; x += whole(24, 46)) {
      const w = whole(18, 40);
      const h = whole(42, 98);
      far.push({ x, y: 100 - h, w, h });
      if (rnd() < 0.25) far.push({ x: x + Math.round(w / 2) - 1, y: 100 - h - 12, w: 2, h: 12 });
    }

    // Middle row, 84 px tall: buildings with window grids and a roof variant.
    const mid: Building[] = [];
    for (let x = whole(0, 12); x < TILE - 40;) {
      const w = whole(34, 62);
      const h = whole(36, 74);
      const y = 84 - h;
      const style = rnd();
      const roof =
        style < 0.3
          ? `M${x - 2} ${y}L${x + w / 2} ${y - whole(10, 16)}L${x + w + 2} ${y}z`
          : style < 0.5
            ? `M${x + 6} ${y}v-8h${w - 12}v8z`
            : null;
      const windows: Rect[] = [];
      for (let wy = y + 7; wy < 84 - 12; wy += 11) {
        for (let wx = x + 6; wx < x + w - 8; wx += 10) {
          if (rnd() < 0.55) windows.push({ x: wx, y: wy, w: 4, h: 6 });
        }
      }
      mid.push({ x, y, w, h, roof, windows });
      x += w + whole(6, 18);
    }

    // Near row, 64 px tall: trees, conifers, lamps, bushes and one bus stop sign.
    const near: Prop[] = [];
    let signPlaced = false;
    for (let x = whole(10, 30); x < TILE - 30; x += whole(34, 70)) {
      const pick = rnd();
      if (!signPlaced && x > TILE / 2) {
        near.push({ kind: 'sign', x });
        signPlaced = true;
      } else if (pick < 0.38) {
        const r = whole(10, 14);
        near.push({
          kind: 'tree',
          x,
          crowns: [
            { cx: x, cy: 64 - 18 - r, r },
            { cx: x - r * 0.6, cy: 64 - 14 - r * 0.7, r: r * 0.7 },
            { cx: x + r * 0.6, cy: 64 - 15 - r * 0.7, r: r * 0.75 }
          ]
        });
      } else if (pick < 0.6) {
        near.push({ kind: 'conifer', x, h: whole(34, 50) });
      } else if (pick < 0.8) {
        near.push({ kind: 'lamp', x });
      } else {
        near.push({ kind: 'bush', x, w: whole(16, 28) });
      }
    }

    const clouds = [0, 1].map(() => ({
      x: whole(40, TILE - 120),
      y: whole(8, 40),
      s: between(0.7, 1.1)
    }));
    return { far, mid, near, clouds };
  }

  const scene = $derived(build(seed));
</script>

<div class="scenery" aria-hidden="true">
  <div class="stage" style:--scale={scale} style:--road="{road / scale}px">
    <!-- The sky fades out on the left so clouds never pass behind the card's text. -->
    <div class="sky">
      <svg class="layer clouds" width={TILE * 2} height="60" viewBox="0 0 {TILE * 2} 60">
        {#each [0, TILE] as offset (offset)}
          {#each scene.clouds as cloud, i (i)}
            <path
              transform="translate({cloud.x + offset} {cloud.y}) scale({cloud.s})"
              d="M14 26a12 12 0 0 1 2-24 16 16 0 0 1 30 2 12 12 0 0 1 22 6 9 9 0 0 1 8 16z"
            />
          {/each}
        {/each}
      </svg>
    </div>

    <svg class="layer far" width={TILE * 2} height="100" viewBox="0 0 {TILE * 2} 100">
      {#each [0, TILE] as offset (offset)}
        <g transform="translate({offset} 0)">
          {#each scene.far as r, i (i)}
            <rect x={r.x} y={r.y} width={r.w} height={r.h} />
          {/each}
        </g>
      {/each}
    </svg>

    <svg class="layer mid" width={TILE * 2} height="84" viewBox="0 0 {TILE * 2} 84">
      {#each [0, TILE] as offset (offset)}
        <g transform="translate({offset} 0)">
          {#each scene.mid as b, i (i)}
            <rect x={b.x} y={b.y} width={b.w} height={b.h} />
            {#if b.roof}<path d={b.roof} />{/if}
            {#each b.windows as w, j (j)}
              <rect class="window" x={w.x} y={w.y} width={w.w} height={w.h} />
            {/each}
          {/each}
        </g>
      {/each}
    </svg>

    <svg class="layer near" width={TILE * 2} height="64" viewBox="0 0 {TILE * 2} 64">
      {#each [0, TILE] as offset (offset)}
        <g transform="translate({offset} 0)">
          {#each scene.near as p, i (i)}
            {#if p.kind === 'tree'}
              <rect x={p.x - 1.5} y="40" width="3" height="24" />
              {#each p.crowns as c, j (j)}<circle cx={c.cx} cy={c.cy} r={c.r} />{/each}
            {:else if p.kind === 'conifer'}
              <rect x={p.x - 1.5} y="54" width="3" height="10" />
              <path
                d="M{p.x} {64 - p.h}l{p.h * 0.22} {p.h * 0.45}h-{p.h * 0.1}l{p.h * 0.18} {p.h *
                  0.4}h-{p.h * 0.6}l{p.h * 0.18} -{p.h * 0.4}h-{p.h * 0.1}z"
              />
            {:else if p.kind === 'lamp'}
              <rect x={p.x - 1} y="16" width="2" height="48" />
              <path d="M{p.x} 17h9v3h-9z" />
              <rect class="light" x={p.x + 7} y="20" width="5" height="3" rx="1" />
            {:else if p.kind === 'bush'}
              <ellipse cx={p.x} cy="58" rx={p.w / 2} ry="8" />
              <ellipse cx={p.x + p.w / 3} cy="60" rx={p.w / 3} ry="6" />
            {:else}
              <rect x={p.x - 1} y="18" width="2" height="46" />
              <rect x={p.x - 8} y="8" width="16" height="16" rx="3" />
              <path
                class="window"
                d="M{p.x - 4} 12h8v6h-8zM{p.x - 3} 19h2v2h-2zM{p.x + 1} 19h2v2h-2z"
              />
            {/if}
          {/each}
        </g>
      {/each}
    </svg>
  </div>
</div>

<style>
  .scenery {
    position: absolute;
    inset: 0;
    overflow: hidden;
    pointer-events: none;
  }
  /* Scaled from the bottom-left corner, so the street always stands on the road. */
  .stage {
    position: absolute;
    left: 0;
    bottom: 0;
    width: calc(100% / var(--scale));
    height: calc(100% / var(--scale));
    transform: scale(var(--scale));
    transform-origin: bottom left;
  }
  .layer {
    position: absolute;
    left: 0;
    animation: scroll linear infinite;
  }
  .sky {
    position: absolute;
    inset: 0;
    mask-image: linear-gradient(90deg, transparent 50%, #000 80%);
  }
  .clouds {
    top: 12px;
    fill: var(--scene-cloud);
    animation-duration: 90s;
  }
  .far {
    bottom: var(--road);
    fill: var(--scene-far);
    animation-duration: 60s;
  }
  .mid {
    bottom: var(--road);
    fill: var(--scene-mid);
    animation-duration: 26s;
  }
  .near {
    bottom: var(--road);
    fill: var(--scene-near);
    animation-duration: 9s;
  }
  .window {
    fill: var(--scene-window);
  }
  .light {
    fill: #ffe08a;
  }
  @keyframes scroll {
    to {
      transform: translateX(-50%);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .layer {
      animation: none;
    }
  }
</style>
