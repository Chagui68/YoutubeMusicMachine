# YouTube Music Machine

Play YouTube music on your Minecraft server with 3D audio (Simple Voice Chat) or regular chat.

## Requirements

- **Server**: Spigot/Paper 1.20.x+
- **Java**: 21+
- **Required dependency**: Slimefun4
- **Optional dependency**: Simple Voice Chat (for 3D audio)

## Installation

1. Place `youtube-music-machine.jar` in `plugins/`
2. Make sure Slimefun4 is installed
3. (Optional) Install Simple Voice Chat for 3D audio
4. Restart the server

### YouTube Setup (required)

The plugin uses **yt-dlp** (auto-downloaded) to extract audio from YouTube. YouTube requires authentication, you need a `cookies.txt` file:

1. Install the [cookies.txt](https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc) extension in Chrome/Firefox
2. Visit [youtube.com](https://youtube.com) (signed in to your Google account)
3. Click the extension → Export cookies (Netscape format)
4. Upload the file to `plugins/YoutubeMusicMachine/cookies.txt`
5. Restart the server

> Cookies expire periodically. If it stops working, export fresh cookies.

### Lavalink Setup (optional)

If you have a Lavalink v4 server, configure it in `config.yml`:

```yaml
lavalink:
  nodes:
    - host: localhost
      port: 2333
      password: "youshallnotpass"
```

If you don't have Lavalink, leave the section empty or remove it.

## Usage

### Commands

| Command | Description |
|---------|-------------|
| `/playmusic <name or URL>` | Search and play music from YouTube |
| `/stopmusic` | Stop the music |
| `/musicinfo` | Show info about the current song |

### Permissions

| Permission | Description |
|------------|-------------|
| `youtubemusic.use` | Access to plugin commands |

### Slimefun Machine

- Create the **YouTube Music Machine** from the Slimefun guide
- Place it in the world and click to open the interface
- Type a song name or YouTube URL

## Architecture

```
Player: /playmusic "Bohemian Rhapsody"
    ↓
yt-dlp (binary, auto-downloaded)
    ↓ Searches YouTube and gets audio stream URL
    ↓
LavaPlayer (HttpAudioSourceManager)
    ↓ Decodes the audio stream
    ↓
Simple Voice Chat API → 3D spatial audio
    (or regular chat if no VoiceChat)
```

### Resolution Fallbacks

The plugin tries multiple methods in sequence:

1. **yt-dlp** with cookies.txt (primary method)
2. **Lavalink** if configured and connected (faster, ~200ms)
3. **Piped API** (REST fallback)
4. **Invidious API** (REST fallback)

## Development

```bash
mvn -o package -DskipTests
```

The JAR is generated in `target/`.
