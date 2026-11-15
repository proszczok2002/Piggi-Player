# Audyt na urządzeniu Android

Zewnętrzny APK testowy służy do odczytu drzewa dostępności, wykonywania akcji na jego elementach oraz wstrzykiwania zdarzeń klawiszy. Nie jest częścią Piggi Player. `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` pozostawia TalkBack i inne usługi dostępności aktywne.

Budowanie wymaga JDK 17+ oraz Android SDK 37.0 i Build Tools 37.0.0:

```powershell
./Build.ps1 -Sdk 'ścieżka-do-sdk' -Jdk 'ścieżka-do-jdk'
adb install -r ../../app/build/device-audit/audit.apk
python run.py dump --sdk 'ścieżka-do-sdk'
python run.py focus --text 'Pozycja odtwarzania' --sdk 'ścieżka-do-sdk'
python run.py action --text 'Pozycja odtwarzania' --value 'Cofnij o 10 sekund' --sdk 'ścieżka-do-sdk'
```

`run.py` zapisuje pełne wyniki lokalnie w ignorowanym `app/build/device-audit/evidence/`. Odczyty TalkBack należy potwierdzać jego rzeczywistym wyjściem mowy lub dziennikiem `SpeechControllerImpl: Speaking fragment text`, a nie samą etykietą w drzewie. Po teście przywróć poprzedni poziom rejestrowania TalkBacka.

Polecenie `media` umożliwia obserwację sesji i wysyłanie przycisków multimedialnych. Zwykły APK testowy nie ma uprawnień kontrolera multimediów, dlatego bezpośrednie polecenia transportu mogą być odrzucane przez Media3. Do sprawdzenia uprawnionego kontrolera używaj:

```powershell
python transport.py next --sdk 'ścieżka-do-sdk'
python transport.py previous --sdk 'ścieżka-do-sdk'
```

Ten skrypt używa systemowego `cmd media_session monitor`. Testy zainstalowanego narzędzia do przycisków głośności należy wykonywać oddzielnie, z aktywną jego usługą i z potwierdzeniem zmiany identyfikatora utworu w sesji. Wstrzyknięcie zdarzenia klawisza nie jest fizycznym naciśnięciem przycisku ani testem innego modelu telefonu.

Po zakończeniu można usunąć wyłącznie pomocniczy pakiet: `adb uninstall pl.proszczok.piggiaudit`.
