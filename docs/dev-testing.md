# Dev testing

Collection-log test commands work only in the RuneLite **developer client** (`./gradlew run`, which runs with `--developer-mode`). They do not work in the normal Jagex launcher client, and they do not appear in plugin settings.

Log in first (see [dev-mode Jagex login](dev-mode-login.md)), then type the commands in game chat.

## Commands

### Queue a hidden unique

Mimics a CoX unique arriving while you are still waiting on the reward chest: tracks the loot and queues a delayed popup. Nothing is shown yet.

```text
::clogtest Dragon claws
::clogtest Twisted bow
```

Known CoX unique names are matched even if you type a shorter fragment that still contains the name. Any other text is used as the item name as-is, which is useful for layout tests.

### Simulate opening the chest

Runs the same path as opening the CoX reward chest: restores censored chat, opens Game chat if that option is on, shows the queued collection-log popup, and takes a screenshot.

```text
::clogopen
```

Use this after `::clogtest` when you want to control the wait yourself.

### Queue and auto-open

Same as `::clogtest`, then waits the given number of seconds and runs `::clogopen`. Handy for testing a 2–120 second chest delay without a raid.

```text
::clogtest Dragon claws --auto 5
::clogtest Twisted bow --auto 30
```

`--auto` must be an integer number of seconds (`0` is allowed). Invalid values print a usage line in game chat.

## What you should see

1. After `::clogtest`, a game message: `Queued hidden collection-log popup for <item>. Use ::clogopen to simulate the chest.`
2. With `--auto`, a second message: `Simulating chest open in <n>s.`
3. On open: the custom collection-log popup (if **Delay collection log popup until chest opens** is on) and a screenshot (if **Take collection log screenshot** is on).

Screenshots land in the usual RuneLite folder:

```text
.runelite/screenshots/<character>/Collection Log/
Collection log (Dragon claws) <timestamp>.png
```

## CoX unique names

These are the names the plugin treats as raid uniques:

```text
Dexterous prayer scroll
Arcane prayer scroll
Twisted buckler
Dragon hunter crossbow
Dinh's bulwark
Ancestral hat
Ancestral robe top
Ancestral robe bottom
Dragon claws
Elder maul
Kodai insignia
Twisted bow
```
