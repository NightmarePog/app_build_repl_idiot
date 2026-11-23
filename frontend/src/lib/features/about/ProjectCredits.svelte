<script lang="ts">
  import { onMount } from 'svelte';
  import { getHealth, getTeam, type TeamResponse } from '$lib/api/generated';
  let team = $state<TeamResponse>();
  let teamError = $state(false);

  let status = $state('ověřujeme…');

  onMount(() => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10_000);

    async function checkHealth() {
      try {
        const { data } = await getHealth({
          signal: controller.signal,
          cache: 'no-store',
          throwOnError: true
        });
        status = data.status.toLowerCase() === 'ok' ? 'v provozu' : data.status;
      } catch {
        status = 'nedostupná';
      }
    }

    async function loadTeam() {
      try {
        const { data } = await getTeam({
          signal: controller.signal,
          cache: 'no-store',
          throwOnError: true
        });
        team = data;
      } catch {
        teamError = true;
      }
    }

    void Promise.all([checkHealth(), loadTeam()]).finally(() => clearTimeout(timeout));
    return () => {
      controller.abort();
      clearTimeout(timeout);
    };
  });
</script>

<div class="project-credits" aria-label="Informace o projektu">
  <div aria-label="Autoři" aria-busy={!team && !teamError}>
    {#if team}
      <p class="team-name">{team.name}</p>
      <p>{team.members.join(' · ')}</p>
    {:else if teamError}
      <p>Údaje o týmu nejsou k dispozici.</p>
    {:else}
      <p>Načítáme údaje o týmu…</p>
    {/if}
  </div>
  <p role="status" class="system-status" class:down={status === 'nedostupná'}>
    <span class="dot" aria-hidden="true"></span>Data jízdních řádů: {status}
  </p>
</div>

<style>
  .project-credits {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem 1.5rem;
    min-width: 0;
  }
  [aria-label='Autoři'] {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem 0.75rem;
    min-width: 0;
    overflow-wrap: anywhere;
  }
  .team-name {
    font-weight: var(--weight-strong);
  }
  .system-status {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    font-family: ui-monospace, monospace;
    font-size: var(--fs-xs);
    color: var(--muted-foreground);
  }
  .dot {
    width: 0.5rem;
    height: 0.5rem;
    border-radius: var(--radius-pill);
    background: var(--board-led);
  }
  .down .dot {
    background: var(--destructive);
  }
</style>
