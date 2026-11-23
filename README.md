# `app = build $ replicate 2 Idiot`

Jsme dvoučlenný tým a náš název odkazuje na programovací jazyk **Haskell**.

## Technologie

| Vrstva | Technologie |
| --- | --- |
| Backend | Java 21, Spring Boot, Gradle |
| API | OpenAPI generované z backendu, TypeScript SDK a Zod validátory |
| Frontend | SvelteKit, TypeScript, Tailwind CSS, shadcn-svelte |
| Databáze | PostgreSQL 17, Flyway migrace |
| Nasazení | Docker Compose, Caddy jako reverzní proxy |

## Nasazení přes Docker Compose

Potřebujete Docker s pluginem Compose. Příkazy spouštějte z kořene repozitáře;
Java, Node.js ani Gradle nemusí být nainstalované na hostiteli.

### První spuštění

1. Vytvořte lokální konfiguraci, pokud ještě nemáte `.env`:

   ```sh
   cp -n .env.example .env
   ```

2. V souboru `.env` nastavte vlastní `POSTGRES_PASSWORD` místo ukázkového hesla.
   Soubor je v `.gitignore`.

3. Sestavte a spusťte všechny služby:

   ```sh
   docker compose up --build -d
   docker compose ps
   ```

Aplikace je dostupná na **[http://localhost:8080](http://localhost:8080)**.
Caddy směruje `/api` na backend a ostatní požadavky na frontend. Migrace databáze
se provedou automaticky při startu backendu. První sestavení může chvíli trvat.

### Ověření a logy

Po dokončení startu ověřte API:

```sh
curl --fail http://localhost:8080/api/v1/health
```

Očekávaná odpověď je `{"status":"ok"}`. Pro kontrolu služeb a sledování logů:

```sh
docker compose ps
docker compose logs --tail=100 -f
```

Sledování logů ukončíte pomocí `Ctrl+C`; kontejnery zůstanou spuštěné.

### Aktualizace a zastavení

Po stažení nebo úpravě zdrojového kódu znovu sestavte a spusťte služby:

```sh
docker compose up --build -d
```

Pro zastavení a odstranění kontejnerů:

```sh
docker compose down
```

Data PostgreSQL zůstávají ve svazku `postgres_data` i po odstranění kontejnerů.
**Přepínač `-v` u `docker compose down` smaže i databázová data.** Změna hesla
v `.env` sama nezmění heslo v již vytvořené databázi.

Čtení přes API (`GET`) je veřejné. Operace `POST`, `PUT` a `DELETE` vyžadují
administrátorský klíč v hlavičce `Authorization: Bearer <klíč>`; bez něj vrací API
`401 Unauthorized`. Výchozí klíč ze zadání je v `backend/src/main/resources/application.yaml`
a lze jej přepsat proměnnou prostředí `ADMIN_API_KEY`.

Compose zpřístupňuje aplikaci přes HTTP na portu `8080`; HTTPS zde není nastavené.
Konfigurace pro Tour de Cloud je v souboru [tourdeapp.yaml](tourdeapp.yaml)
a používá tajnou proměnnou `POSTGRES_PASSWORD`.

## Jízdní řády (fáze 4)

- Spoj (`trip`) odkazuje jen na trasu (`route`); linku, směr a pořadí zastávek bere z ní.
- `stop_time` odkazuje na konkrétní zastávku trasy (`route_stop_id`). Cizí klíč
  `(route_stop_id, route_id)` a `(trip_id, route_id)` zaručuje, že zastávka patří trase spoje,
  primární klíč `(trip_id, route_stop_id)` že je ve spoji nejvýš jednou, CHECK že příjezd
  není po odjezdu. Odložený trigger při commitu hlídá, že spoj má aspoň dvě zastávky, vyjíždí
  před 24:00 svého provozního dne a nejede zpátky v čase.
- `stop_time.route_id` není duplicitní údaj: je to druhá polovina obou složených cizích klíčů
  a vždy se rovná `trip.route_id` (vynucuje to cizí klíč na `trip`), takže se nemůže rozejít.
  Deklarativní cizí klíč je zvolen místo triggeru, protože ho nejde obejít.
- Externí reference mají CHECK formátu: linka `L001`, trasa `R00101`, spoj `T00101070001`.
- Časy jsou PostgreSQL `INTERVAL` (doména `timetable_time`, 0 až 47:59, celé minuty) od
  půlnoci provozního dne. `TIME` neumí 24:14, takže večerní spoj, který dojede v 00:14, by
  potřeboval další sloupec; interval drží spoj pohromadě a API z něj vrací `HH:MM` a
  `day_offset` vůči začátku spoje.
- `GET /stops/{id}/departures` vrací jen odjezdy: konečná zastávka spoje (kde autobus jen
  přijíždí) v seznamu není. Řazení je podle `(departure_day_offset, departure_time)`.
- `GET /lines/{id}/trips` řadí spoje podle odjezdu z výchozí zastávky, oba směry dohromady;
  směr nese každý spoj v poli `direction`.
- Seed (V11) načte `trips.json` a `stop_times.json` beze změn. Reference `L001`/`R00101` přiřadí
  linkám a trasám ze sítě fáze 3 výslovným seznamem (číslo linky, směr, reference); když některý spoj na existující
  trasu nebo zastávku nesedí, nasazení selže.
- Smazání zastávky (`DELETE /stops/{id}`) funguje jako ve fázi 3 (204): zmizí i její časy a
  spoj, kterému zůstane méně než dvě zastávky.

## Lokální vývoj frontendu

Potřebujete Node.js **22.22.2 nebo novější** a pnpm **10.34.5**.

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Vývojový server používá testovací API s daty z Faker.js, takže nepotřebuje běžící
backend ani databázi. Adresu aplikace vypíše do terminálu; změny testovacích dat
se při restartu serveru ztratí.


OpenAPI kontrakt a dokumentační nástroje jsou v [backend/openapi/](backend/openapi/README.md).
Kontrakt se generuje z backendu; příkazy `pnpm api:generate` a `pnpm api:check`
ve frontendu aktualizují nebo ověří kontrakt i generovaného klienta.

Aplikace sse vyvíjí v rámci soutěže Tour de App
