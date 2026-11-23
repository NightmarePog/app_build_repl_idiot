<script lang="ts">
  /** International Symbol of Access (ISO 7001), as on PID timetables: white on blue. */
  let { size = 16, label }: { size?: number; label?: string } = $props();
</script>

<svg
  class="isa"
  width={size}
  height={size}
  viewBox="0 0 24 24"
  role={label ? 'img' : undefined}
  aria-label={label}
  aria-hidden={label ? undefined : 'true'}
>
  <rect width="24" height="24" rx="2.5" fill="var(--brand)" />
  <g fill="var(--brand-foreground)">
    <circle cx="10.2" cy="4.6" r="1.9" />
    <path
      d="M9 7.6h2.4l.4 4.2h4.4l2.6 5.4 1.3-.6.8 1.6-2.9 1.4-2.8-5.8H10a1.2 1.2 0 0 1-1.2-1.1z"
    />
    <path d="M7.6 10.4l.3 2.1a4.4 4.4 0 1 0 6.4 5.2l1.1 2.2A6.6 6.6 0 1 1 7.6 10.4z" />
  </g>
</svg>

<style>
  .isa {
    display: inline-block;
    flex-shrink: 0;
    vertical-align: middle;
  }
</style>
