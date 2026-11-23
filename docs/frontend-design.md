# Frontend design brief

Based on PID/IDOS line timetables, SBB and DB Navigator trip views, and TfL/Citymapper departure boards.

## One visual language
- **Paper** (light card, line-coloured top band) is for timetables you read: the line diagram and the line timetable.
- **Board** (`.board-surface`, always dark, brand green times) is for live state: stop departures and trip status.
- Line colour comes only from the API (`--line`, `--line-ink`, `--line-stroke`) and marks plates, rails, terminus tags and the active direction.

## How every page says what it is
- `PageHeader`: a type tag (LINKA / ZASTÁVKA / SPOJ), then the name in the h1. Line and trip pages put the line plate in front of the name.
- `BackLink`: the same back link above every detail page.
- Every section starts with `.section-title` (small caps and a rule): Trasa, Jízdní řád, Vybavení zastávky, Jízdní řád spoje.
- Words go where the data is, so the page needs no legend: `výchozí`/`konečná` tags next to the terminus names, `příj./odj.` on split times, `odjezd`/`příjezd` on the board, column headers above the diagram and the trip timeline.

## Pages
- **Linka**: one "Směr jízdy" switch controls the whole page. Trasa and Jízdní řád sit side by side at 1024px and wider, and stack on phones.
- **Zastávka**: title and photo, then the "Odjezdy" board (it does not repeat the name; "Nyní" clock), then equipment and lines.
- **Spoj**: the "Stav spoje" board card says in words what the bus is doing now (stands / runs between / leaves in / done), with a progress bar from boarding stop to the terminus. The timeline lists the times, so the card does not repeat them as flaps. The "Dál z konečné" section is shown only when there is somewhere to continue.

## Tokens (app.css)
- Type: `--fs-*` only. Weights: `--weight-regular`, `--weight-semibold`, `--weight-strong`, `--font-display-weight`.
- Shape and elevation: `--radius-pill`, `--shadow-sm`, `--shadow-pop`, `--shadow-raised`, `--ring-halo`.
- Motion: `--motion-fast|normal|slow|xslow`, `--motion-stagger` for list entry, `--loop-fast|pulse|slow` for loops. Everything respects `prefers-reduced-motion`.
