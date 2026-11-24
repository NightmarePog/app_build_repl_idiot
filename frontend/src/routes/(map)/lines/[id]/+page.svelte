<script lang="ts">
  import type { PageProps } from './$types';
  import LineDetailPage from '$lib/features/lines/LineDetailPage.svelte';

  let { params }: PageProps = $props();
</script>

<LineDetailPage id={params.id} />
