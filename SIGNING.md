# Podpis wydań Piggi Player

Klucz produkcyjny znajduje się lokalnie w `signing/proszczok-player-release.p12`, a dane dostępu w `keystore.properties`. Techniczne nazwy klucza i aliasu pochodzą z wcześniejszych wydań i pozostają bez zmian, aby zachować ten sam certyfikat aktualizacji. Obie lokalizacje są ignorowane przez Git i nie wolno ich publikować na serwerze WWW.

- alias: `proszczok-player`
- algorytm: RSA 4096 / SHA-384
- ważność: do 12 stycznia 2054
- SHA-256 certyfikatu: `86:F7:E2:08:A3:0F:45:F8:2C:D8:5D:71:79:6B:07:EE:26:AD:9F:CF:0F:8E:79:B2:8D:24:DC:A0:A5:D5:EA:51`

Utrata klucza lub hasła uniemożliwi aktualizowanie istniejących instalacji. Należy wykonać zaszyfrowaną kopię zapasową na niezależnym nośniku.
