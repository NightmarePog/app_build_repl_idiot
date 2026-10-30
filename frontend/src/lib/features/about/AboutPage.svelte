<script lang="ts">
  import { reveal } from '$lib/motion';
  import { resolve } from '$app/paths';
  import { ArrowLeft, ArrowUpRight } from '@lucide/svelte';
  import { Button } from '$lib/ui/button';
  let { kind }: { kind: 'contact' | 'terms' } = $props();
</script>

<svelte:head>
  <title>{kind === 'contact' ? 'Kontakt' : 'Podmínky používání'} — Think different Academy</title>
</svelte:head>

<article class="about-page">
  <Button href={resolve('/')} variant="ghost" class="-ml-4"
    ><ArrowLeft data-icon="inline-start" aria-hidden="true" /> Zpět na úvod</Button
  >
  {#if kind === 'contact'}
    <h1>Ozvěte se.</h1>
    <p class="intro">Chyby a návrhy na zlepšení přijímáme přes GitHub.</p>
    <section {@attach reveal()}>
      <h2>Hlášení chyb</h2>
      <p>
        Založte issue v repozitáři projektu. Uveďte stránku, co se stalo a co jste čekali. Pomůže i
        snímek obrazovky.
      </p>
      <Button href="https://github.com/NightmarePog/2idiotz/issues/new"
        >Napsat týmu <ArrowUpRight data-icon="inline-end" aria-hidden="true" /></Button
      >
      <p class="note">
        Potřebujete účet na GitHubu. Issues jsou veřejné, nepište do nich osobní údaje.
      </p>
    </section>
    <section {@attach reveal()}>
      <h2>Zdrojový kód</h2>
      <p>
        Kód je veřejný v <a href="https://github.com/NightmarePog/2idiotz">repozitáři projektu</a>.
      </p>
    </section>
  {:else}
    <h1>Podmínky.</h1>
    <p class="intro">Pravidla používání studentského projektu.</p>
    <p class="note">Nejde o právní smlouvu, jen o shrnutí, jak aplikaci používat.</p>
    <section {@attach reveal()}>
      <h2>Co aplikace umí</h2>
      <p>
        Ukazuje zastávky v kampusu, linky, které jimi projíždějí, a jejich vybavení. Jízdní řády ani
        odjezdy neobsahuje.
      </p>
    </section>
    <section {@attach reveal()}>
      <h2>Přesnost dat</h2>
      <p>
        Schéma linek je zjednodušené a neodpovídá přesné poloze zastávek. Spoje a odjezdy si ověřte
        u dopravce. Vybavení zastávek se může změnit.
      </p>
    </section>
    <section {@attach reveal()}>
      <h2>Hlášení chyb</h2>
      <p>
        Když najdete chybu, <a href={resolve('/contact')}>napište nám</a>. Uveďte stránku a postup,
        jak chybu zopakovat.
      </p>
    </section>
    <section {@attach reveal()}>
      <h2>Technologie</h2>
      <p>
        S HTML jsme se posunuli do budoucnosti. Layout už neskládáme z tabulek a
        <code>&lt;marquee&gt;</code> jsme vypnuli. Skoro bez slz.
      </p>
    </section>
    <section {@attach reveal()}>
      <h2>Lokalita</h2>
      <p>
        Mapa ukazuje město, které je MOŽNÁ Praha. Možná ne. Kdo ví. Zastávky jsou vymyšlené, ulice
        podezřele povědomé.
      </p>
    </section>
    <section {@attach reveal()}>
      <h2>Pozdrav</h2>
      <p>Ahoj Herniku :333</p>
    </section>
  {/if}
</article>

<style>
  .about-page {
    max-width: 42rem;
    padding-bottom: 2rem;
  }
  h1 {
    margin-top: 2rem;
    font-size: clamp(2.5rem, 5vw, 3.5rem);
    line-height: 1.1;
    font-weight: var(--font-display-weight, 650);
    letter-spacing: var(--tracking-display, -0.02em);
  }
  .intro {
    font-size: 1.25rem;
    margin-block: 1.25rem;
  }
  section {
    margin-top: 2.5rem;
    padding-top: 2rem;
    border-top: 1px solid var(--border);
  }
  h2 {
    font-size: 1.25rem;
    font-weight: 600;
    margin-bottom: 0.75rem;
  }
  p {
    line-height: 1.65;
    color: var(--muted-foreground);
    margin-bottom: 1rem;
  }
  .note {
    font-size: 0.875rem;
    margin-top: 1rem;
  }
  p code {
    padding: 0.0625rem 0.3125rem;
    border-radius: 0.25rem;
    background: var(--muted);
    color: var(--foreground);
    font-size: 0.875em;
  }
  p a {
    color: var(--primary);
    text-decoration: underline;
    text-underline-offset: 0.2em;
  }
</style>
