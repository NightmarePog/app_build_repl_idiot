<script lang="ts">
  import type { PageProps } from './$types';
  import TripDetailPage from '$lib/features/timetable/TripDetailPage.svelte';

  let { params }: PageProps = $props();
</script>

<TripDetailPage id={params.id} />
