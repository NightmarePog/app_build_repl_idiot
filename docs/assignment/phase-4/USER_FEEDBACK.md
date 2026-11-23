# Direct feedback from the product owner (top priority, overrides reviewer nitpicks)

New items are appended at the bottom. Every fixer must check the items in its area; every reviewer must deduct for any item still not solved.

1. Trip page (/trips/<id>, e.g. 22:49 B with ?od=Ada Exchange) is "really hard to orient in". Observed problems:
   - Title says "22:49 Campus Gate → Hopper Hall" but the user boards at Ada Exchange at 23:06; the header, subtitle ("Jízda 4 min · 7 zastávek") and status card mix the whole trip with the user's segment. Make it obvious: which trip this is, where I board, where I get off, when, and how long MY ride is.
   - The stop list hides 5 earlier stops behind "Zobrazit 5 předchozích zastávek", so the route looks like 2 stops; show the full route with the user's segment highlighted and the rest dimmed (like IDOS / DB trip detail).
   - "Dál z konečné Hopper Hall / Další spoj této trasy 07:00 zítra" is confusing and not useful; replace with something a passenger actually needs (onward connections from the destination, or remove).
   - Labels "přij./odj.", "tady nastupujete", "KONEČNÁ · PŘÍJEZD" are cramped and inconsistent; one clear time column, clear boarding/alighting markers.
   - Overall: one clear reading order top-to-bottom, model it on IDOS spoj detail and DB Navigator journey detail.

2. Orient the user with visual PATTERNS, not text. Labels and explanations are a last resort; the user reads shape, colour, icon and position far faster than words.
   - Each kind of thing must have its own instantly recognisable visual signature, used identically everywhere: a LINE (coloured line badge + route strip), a STOP (stop sign/pin shape + photo), a TRIP (vertical route spine with times), a CONNECTION from the planner (journey bar with legs coloured by line, like IDOS/Citymapper), a DEPARTURE BOARD (dark split-flap board). Never reuse one pattern for another kind.
   - Today too many pages look like the same generic table/list, so the user can't tell where he is. Pages must look different from each other because their content type differs, while sharing the same tokens/type/spacing.
   - Replace explanatory text, legends and hint lines with self-evident visuals (icons, position on a route line, colour, emphasis, timeline position). Keep text short.
   - Consistency rule: the SAME kind of information always uses the SAME component and look on every page (e.g. a line badge, a time, a stop name link, "boarding here" marker).

3. Remove generic "AI look" decoration. Specifically: thick coloured accent bars on one side/top of cards (border-top / border-left stripes, e.g. on "STAV SPOJE", the departure board, line cards, "Linky na zastávce", stop rows), and similar clichés (eyebrow label + short coloured underline next to every section heading, glowing pills, gradient blobs). Audit every page and drop or replace them; use line colour only where it carries meaning (the line badge, the route line itself). Cards should be separated by spacing, surface tone and typography instead.
