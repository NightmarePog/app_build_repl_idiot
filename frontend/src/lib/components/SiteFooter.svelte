<script lang="ts">
  import type { Snippet } from 'svelte';
  import { resolve } from '$app/paths';
  import { ArrowUp, ArrowUpRight } from '@lucide/svelte';
  import { reveal } from '$lib/motion';

  /** Feature content comes from the layout: project status and team, and the line links. */
  let { credits, lines }: { credits?: Snippet; lines?: Snippet } = $props();

  const year = new Date().getFullYear();

  function toTop(event: MouseEvent) {
    event.preventDefault();
    const smooth = !matchMedia('(prefers-reduced-motion: reduce)').matches;
    window.scrollTo({ top: 0, behavior: smooth ? 'smooth' : 'auto' });
    document.getElementById('main')?.focus({ preventScroll: true });
  }
</script>

<footer class="site-footer" aria-label="Patička webu">
  <div class="footer-inner">
    <div class="footer-top">
      <section class="brand" aria-label="Think different Academy" {@attach reveal()}>
        <a
          href={resolve('/')}
          class="brand-mark"
          aria-label="Think different Academy, úvodní stránka"
        >
          <img
            src="/api/v1/assets/brand/tda-logo.svg"
            alt=""
            width="289"
            height="90"
            class="logo logo-light"
          />
          <img
            src="/api/v1/assets/brand/tda-logo-dark.svg"
            alt=""
            width="289"
            height="90"
            class="logo logo-dark"
          />
        </a>
        <p class="tagline">
          Autobusové zastávky a linky kampusu na jedné mapě. Bezbariérový nástup, střecha i jízdenky
          u každé zastávky.
        </p>
        {@render credits?.()}
      </section>

      <nav class="column" aria-labelledby="footer-explore" {@attach reveal({ delay: 0.07 })}>
        <h2 id="footer-explore">Procházet</h2>
        <ul>
          <li><a href={resolve('/')}>Úvod</a></li>
          <li><a href={resolve('/stops')}>Mapa zastávek</a></li>
          <!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- resolved root plus an in-page fragment -->
          <li><a href={`${resolve('/')}#lines`}>Přehled linek</a></li>
        </ul>
      </nav>

      <nav
        class="column lines-column"
        aria-labelledby="footer-lines"
        {@attach reveal({ delay: 0.14 })}
      >
        <h2 id="footer-lines">Linky</h2>
        <ul>
          {@render lines?.()}
        </ul>
      </nav>

      <nav class="column" aria-labelledby="footer-project" {@attach reveal({ delay: 0.21 })}>
        <h2 id="footer-project">Projekt</h2>
        <ul>
          <li>
            <a href="https://github.com/NightmarePog/2idiotz" rel="noopener noreferrer"
              >GitHub <ArrowUpRight size={14} aria-hidden="true" /></a
            >
          </li>
          <li><a href={resolve('/contact')}>Kontakt</a></li>
          <li><a href={resolve('/terms')}>Podmínky používání</a></li>
        </ul>
      </nav>
    </div>

    <div class="footer-bottom">
      <p>© {year} Think different Academy</p>
      <p class="attribution">
        Mapové podklady
        <a href="https://openfreemap.org" rel="noopener noreferrer">OpenFreeMap</a>,
        <a href="https://www.openmaptiles.org" rel="noopener noreferrer">OpenMapTiles</a>, data ©
        <a href="https://www.openstreetmap.org/copyright" rel="noopener noreferrer"
          >přispěvatelé OpenStreetMap</a
        >
      </p>
      <a href="#main" class="to-top" onclick={toTop}
        >Zpátky nahoru <ArrowUp size={15} aria-hidden="true" /></a
      >
    </div>
  </div>
</footer>

<style>
  .site-footer {
    margin-top: 4rem;
    border-top: 1px solid var(--border);
    background: color-mix(in oklch, var(--muted) 55%, var(--background));
    color: var(--muted-foreground);
    font-size: 0.875rem;
  }
  .footer-inner {
    max-width: 85rem;
    margin-inline: auto;
    padding: 3rem 2rem 1.5rem;
  }
  .footer-top {
    display: grid;
    grid-template-columns: minmax(0, 1.6fr) repeat(3, minmax(0, 1fr));
    gap: 2.5rem 2rem;
  }

  .brand {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
    max-width: 26rem;
  }
  .brand-mark {
    display: block;
    border-radius: 0.25rem;
  }
  .logo {
    display: block;
    width: 8.5rem;
    height: auto;
  }
  .logo-dark,
  :global(.dark) .logo-light {
    display: none;
  }
  :global(.dark) .logo-dark {
    display: block;
  }
  .tagline {
    line-height: 1.55;
  }

  .column h2 {
    margin-bottom: 0.75rem;
    color: var(--foreground);
    font-size: 0.8125rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }
  .column :global(ul) {
    display: flex;
    flex-direction: column;
    gap: 0.125rem;
  }
  .column :global(a) {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    min-height: 2.25rem;
    color: var(--muted-foreground);
    font-weight: 600;
    transition:
      color var(--motion-fast) var(--ease-out),
      transform var(--motion-fast) var(--ease-out);
  }
  .column :global(a:hover) {
    color: var(--foreground);
    transform: translateX(2px);
  }

  .footer-bottom {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 0.5rem 2rem;
    margin-top: 2.5rem;
    padding-top: 1.25rem;
    border-top: 1px solid var(--border);
    font-size: 0.8125rem;
  }
  .attribution a {
    color: inherit;
    text-decoration: underline;
    text-decoration-color: color-mix(in oklch, currentColor 40%, transparent);
    text-underline-offset: 0.2em;
    transition: color var(--motion-fast) var(--ease-out);
  }
  .attribution a:hover {
    color: var(--foreground);
  }
  .to-top {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    min-height: 2.75rem;
    color: var(--muted-foreground);
    font-weight: 600;
    transition: color var(--motion-fast) var(--ease-out);
  }
  .to-top :global(svg) {
    transition: transform var(--motion-normal) var(--ease-spring-out);
  }
  .to-top:hover {
    color: var(--foreground);
  }
  .to-top:hover :global(svg) {
    transform: translateY(-3px);
  }
  a:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
    border-radius: 0.25rem;
  }

  @media (max-width: 979px) {
    .footer-top {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
    .brand {
      grid-column: 1 / -1;
      max-width: none;
    }
  }
  @media (max-width: 639px) {
    .footer-inner {
      padding: 2.5rem 1.25rem 1.25rem;
    }
    .footer-top {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
    /* Line routes need the full width to stay on one line. */
    .lines-column {
      grid-column: 1 / -1;
      order: 1;
    }
    .footer-bottom {
      flex-direction: column;
      align-items: flex-start;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .column :global(a),
    .to-top :global(svg) {
      transition: none;
    }
    .column :global(a:hover),
    .to-top:hover :global(svg) {
      transform: none;
    }
  }
</style>
