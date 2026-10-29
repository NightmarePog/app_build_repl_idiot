<script lang="ts">
  import type { Snippet } from 'svelte';
  import { resolve } from '$app/paths';
  import { ArrowUpRight } from '@lucide/svelte';

  let { credits }: { credits?: Snippet } = $props();
</script>

<footer class="site-footer" aria-label="Patička webu">
  <div class="footer-inner">
    <div class="footer-credits">
      {#if credits}
        {@render credits()}
      {:else}
        <p>Zastávky kampusu Think different Academy.</p>
      {/if}
    </div>
    <nav aria-label="O projektu" class="footer-links">
      <a href="https://github.com/NightmarePog/2idiotz"
        >GitHub <ArrowUpRight class="size-4" aria-hidden="true" /></a
      >
      <a href={resolve('/contact')}>Kontakt</a>
      <a href={resolve('/terms')}>Podmínky používání</a>
      <a href="#main">Zpátky nahoru <span aria-hidden="true">↑</span></a>
    </nav>
  </div>
</footer>

<style>
  .site-footer {
    margin-top: 4rem;
    border-top: 1px solid var(--border);
  }
  .footer-inner {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem 2rem;
    max-width: 85rem;
    margin-inline: auto;
    padding: 1.25rem 2rem;
    font-size: 0.875rem;
    color: var(--muted-foreground);
  }
  .footer-credits {
    min-width: 0;
  }
  .footer-links {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem 1.5rem;
  }
  .footer-links a {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    min-height: 2.75rem;
    color: var(--muted-foreground);
    font-weight: 600;
    transition: color var(--motion-fast) var(--ease-out);
  }
  .footer-links a:hover {
    color: var(--foreground);
    text-decoration: underline;
    text-underline-offset: 0.25em;
  }
  @media (max-width: 639px) {
    .footer-inner {
      padding-inline: 1.25rem;
    }
  }
</style>
