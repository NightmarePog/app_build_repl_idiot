<script lang="ts">
  import { page } from '$app/state';
  import TripDetailPage from '$lib/features/timetable/TripDetailPage.svelte';
</script>

<TripDetailPage id={page.params.id!} />
