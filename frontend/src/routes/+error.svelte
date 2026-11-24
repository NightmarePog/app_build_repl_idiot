<script lang="ts">
  import { page } from '$app/state';
  import { resolve } from '$app/paths';
  import { linesHref } from '$lib/links';
  import { CircleAlert, MapPinOff } from '@lucide/svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import { Button } from '$lib/ui/button';

  const missing = $derived(page.status === 404);
  const title = $derived(missing ? 'Tahle stránka neexistuje' : 'Něco se pokazilo');
</script>

<svelte:head>
  <title>{missing ? 'Stránka nenalezena' : 'Chyba'} · TdA</title>
  <meta name="robots" content="noindex" />
</svelte:head>

<div class="error-page">
  <PageHeader
    kind={missing ? 'Chyba 404' : `Chyba ${page.status}`}
    icon={missing ? MapPinOff : CircleAlert}
    {title}
  >
    {#snippet meta()}
      <p>
        {#if missing}
          Odkaz je možná starý nebo v adrese chybí znak. Zkuste začít od přehledu linek nebo mapy
          zastávek.
        {:else}
          Stránku se nepodařilo zobrazit. Zkuste ji načíst znovu, nebo pokračujte jinam.
        {/if}
      </p>
    {/snippet}
  </PageHeader>

  <div class="actions">
    <Button href={resolve('/')}>Na úvod</Button>
    <Button variant="outline" href={linesHref()}>Linky</Button>
    <Button variant="outline" href={resolve('/stops')}>Zastávky</Button>
    {#if !missing}
      <Button variant="outline" onclick={() => location.reload()}>Načíst znovu</Button>
    {/if}
  </div>
</div>

<style>
  .error-page {
    max-width: 42rem;
  }
  .error-page p {
    max-width: 36rem;
    color: var(--foreground);
    font-size: var(--fs-lg);
    line-height: 1.5;
  }
  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
  }
</style>
