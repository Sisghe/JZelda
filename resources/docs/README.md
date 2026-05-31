# JZelda - Retro Adventure originale

JZelda è un progetto Java Swing importabile in Eclipse. È ispirato alle convenzioni storiche degli action-adventure retrò a schermata singola, ma usa codice, sprite e audio originali generati per questa consegna. Non include asset, nomi, personaggi o marchi Nintendo.

## Avvio in Eclipse

1. Importare lo ZIP come progetto Java esistente.
2. Verificare che la cartella di lavoro sia la radice del progetto `JZelda`.
3. Eseguire la classe `JZelda` con il metodo `main`.

## Comandi

- Frecce o WASD: movimento del protagonista.
- Spazio: attacco.
- E o Invio: interazione/conferma.
- P: pausa e schermo secondario.
- C: continua dopo Game Over.
- Esc: indietro/menu.
- Numeri 1-4: acquisti nella bottega.
- N nella schermata profili: crea un nuovo profilo.

## Gameplay

Il gioco contiene 8 livelli giocabili e una bottega separata. Ogni livello ha una mappa ASCII in `resources/maps`, collisioni, obiettivo di uscita, nemici, item o rupie. L'uscita si attiva quando i nemici del livello sono stati sconfitti; nei livelli pari serve anche una chiave. Le rupie sono raccoglibili e spendibili nella bottega. Il giocatore ha vite, salute, score, inventario, continua, vittoria e sconfitta.

## Architettura MVC

- Model: `jzelda.model.GameModel` gestisce stato, profili, livelli, entità, score, vite, rupie, inventario, shop, classifica e progressione.
- View: `jzelda.view.GameView` e `GamePanel` disegnano HUD, mappa, menu, effetti e schermate senza regole di gameplay.
- Controller: `jzelda.controller.GameController` traduce input in comandi e pilota il game loop tramite `javax.swing.Timer`.

## Pattern implementati

- Observer/Observable: `AbstractObservableModel`, `ObservableModel`, `ModelObserver`, `ModelEvent` notificano la view.
- Singleton: `AudioManager` e `ResourceManager`.
- Factory Method: `EntityFactory`, `ItemFactory`, `LevelFactory`.
- Strategy: `EnemyBehavior`, `ChasePatrolBehavior`, `RandomShooterBehavior`.
- State: `GameState` e stati concreti `MenuState`, `PlayingState`, `ShopState`, `PausedState`, `GameOverState`, `LeaderboardState`, `VictoryState`, `ProfileSelectState`.
- Command: `InputCommand` e comandi concreti in `jzelda.controller.commands`.
- DAO/Repository: `ProfileRepository`, `FileProfileRepository`, `LeaderboardRepository`, `FileLeaderboardRepository`.
- Facade: `GameFacade` semplifica accesso a risorse, audio e persistenza.

## Stream<T>

Gli stream sono usati in modo funzionale per filtrare nemici e pickup attivi, ordinare la classifica, selezionare offerte acquistabili, calcolare statistiche profilo, caricare livelli e listare mappe.

## Audio e asset

Tutti gli sprite PNG in `resources/images` sono placeholder originali generati proceduralmente per questo progetto. I WAV in `resources/audio` sono beep originali generati per la consegna e non provengono da asset esterni. `AudioManager` usa `javax.sound.sampled.AudioInputStream`, `Clip` e `BufferedInputStream`, compatibili con JDK moderni.

## Persistenza

I profili sono salvati in `resources/profiles/profiles.csv`. La classifica è salvata in `resources/profiles/leaderboard.csv`. I file vengono creati automaticamente alla prima scrittura.
