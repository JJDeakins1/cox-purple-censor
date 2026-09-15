# Dev-mode Jagex login

RuneLite stores a local login token so the plugin **dev client** (`./gradlew run`) can log in without the Jagex launcher.

**File name:** `credentials.properties`  
**Path:** `~/.runelite/credentials.properties`  
**On macOS:** `/Users/<you>/.runelite/credentials.properties`

Do not share this file. It is a live login credential: anyone who can read it can log in as you, without your password.

## Create the file

RuneLite writes it when you log in through the **Jagex launcher** with a one-time flag.

### 1. Open configure

This is not a menu inside the game. Quit RuneLite if it is running, then in Terminal:

```bash
/Applications/RuneLite.app/Contents/MacOS/RuneLite --configure
```

A **RuneLite configuration** window should appear (not the OSRS login screen, and not the Jagex launcher). The launcher needs to be **2.6.3 or newer**.

### 2. Add the flag

In **Client arguments**, add:

```text
--insecure-write-credentials
```

Save and close.

### 3. Log in via Jagex

Launch RuneLite through the **Jagex launcher** (not `./gradlew run`) and log in as usual. After login, RuneLite writes `~/.runelite/credentials.properties`.

### 4. Run the plugin in dev mode

```bash
./gradlew run
```

That client reads the saved file and logs in without the Jagex launcher.

## Recreate the file after deleting it

Same process as the first time.

- If `--insecure-write-credentials` is still in Client arguments: launch RuneLite through the Jagex launcher and log in. A new file is written.
- If you already removed the flag: open configure, add the flag again, save, then launch via Jagex and log in once.

## Remove the flag

Removing the flag only stops RuneLite from writing a **new** credentials file. It does not delete an existing `credentials.properties`.

1. Quit RuneLite if it is running.
2. Open configure:

```bash
/Applications/RuneLite.app/Contents/MacOS/RuneLite --configure
```

3. In **Client arguments**, delete `--insecure-write-credentials`.
4. Save and close.

You only need the flag to **create** the file. After it exists, you can remove the flag. The next time you delete the file, put the flag back to recreate it.

## Security

The file is plaintext by design (`--insecure-write-credentials`). It cannot be made theft-proof while it exists.

Practical steps:

- Restrict it to your user only: `chmod 600 ~/.runelite/credentials.properties`
- Remove `--insecure-write-credentials` after the file exists, so RuneLite does not keep rewriting it (a rewrite can restore weaker permissions)
- Do not copy it into this repo, screenshots, chat, email, or cloud sync of `~/.runelite`
- Keep it only while you are developing; delete it when you are done

If you think it leaked, delete the local file **and** use **End sessions** on the Jagex/RuneScape account settings page. Deleting the local file does not revoke a copy someone already took.
