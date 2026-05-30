# YouTube Music Machine

Reproduce música de YouTube en tu servidor de Minecraft con audio 3D (Simple Voice Chat) o chat normal.

## Requisitos

- **Servidor**: Spigot/Paper 1.20.x+
- **Java**: 21+
- **Dependencia obligatoria**: Slimefun4
- **Dependencia opcional**: Simple Voice Chat (para audio 3D)

## Instalación

1. Colocar `youtube-music-machine.jar` en `plugins/`
2. Asegurarse de que Slimefun4 esté instalado
3. (Opcional) Instalar Simple Voice Chat para audio 3D
4. Reiniciar el servidor

### Configuración de YouTube (requerido)

El plugin usa **yt-dlp** (se descarga automáticamente) para extraer audio de YouTube. YouTube requiere autenticación, necesitas un archivo `cookies.txt`:

1. Instalar extensión [cookies.txt](https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc) en Chrome/Firefox
2. Visitar [youtube.com](https://youtube.com) (iniciado sesión en tu cuenta de Google)
3. Hacer clic en la extensión → Exportar cookies (Netscape format)
4. Subir el archivo a `plugins/YoutubeMusicMachine/cookies.txt`
5. Reiniciar el servidor

> Las cookies expiran periodicamente. Si deja de funcionar, exporta cookies nuevas.

### Configuración Lavalink (opcional)

Si tienes un servidor Lavalink v4, puedes configurarlo en `config.yml`:

```yaml
lavalink:
  nodes:
    - host: localhost
      port: 2333
      password: "youshallnotpass"
```

Si no tienes Lavalink, deja la sección vacía o elimínala.

## Uso

### Comandos

| Comando | Descripción |
|---------|-------------|
| `/playmusic <nombre o URL>` | Busca y reproduce música de YouTube |
| `/stopmusic` | Detiene la música |
| `/musicinfo` | Muestra información de la canción actual |

### Permisos

| Permiso | Descripción |
|---------|-------------|
| `youtubemusic.use` | Acceso a los comandos del plugin |

### Máquina Slimefun

- Crea la **YouTube Music Machine** desde la guía de Slimefun
- Colócala en el mundo y haz clic para abrir la interfaz
- Escribe el nombre de una canción o URL de YouTube

## Arquitectura

```
Jugador: /playmusic "Bohemian Rhapsody"
    ↓
yt-dlp (binario, auto-descargado)
    ↓ Busca en YouTube y obtiene URL de stream de audio
    ↓
LavaPlayer (HttpAudioSourceManager)
    ↓ Decodifica el stream de audio
    ↓
Simple Voice Chat API → Audio 3D espacial
    (o chat normal si no hay VoiceChat)
```

### Fallbacks de resolución

El plugin intenta múltiples métodos en secuencia:

1. **yt-dlp** con cookies.txt (método principal)
2. **Lavalink** si está configurado y conectado (más rápido, ~200ms)
3. **Piped API** (fallback REST)
4. **Invidious API** (fallback REST)

## Desarrollo

```bash
mvn -o package -DskipTests
```

El JAR se genera en `target/`.
