-- Line A used an off-brand blue; use the brand's primary blue (#0070BB) unless an admin changed it.
UPDATE line SET color = '#0070BB' WHERE id = 1 AND upper(color) = '#2B90D9';
