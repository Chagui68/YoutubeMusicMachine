# YouTube Music Machine

## Requisitos

- **Servidor**: Spigot/Paper 1.20.x
- **Dependencias obligatorias**: Slimefun4
- **Dependencias opcionales**: Simple Voice Chat (para audio 3D)
- **FFmpeg**: Requerido en el servidor para decodificar audio

## Instalación

1. Colocar `youtube-music-machine.jar` en la carpeta `plugins/`
2. Asegurarse de que Slimefun4 esté instalado
3. **(Opcional)** Instalar Simple Voice Chat para audio 3D
4. **(Requerido)** Instalar FFmpeg en el servidor:
   - **Windows**: Descargar de https://ffmpeg.org/download.html y añadir al PATH
   - **Linux**: `sudo apt-get install ffmpeg`

## Uso

### Comandos

| Comando | Descripción |
|---------|-------------|
| `/playmusic <youtube_url>` | Reproduce música de YouTube |
| `/stopmusic` | Detiene la música |
| `/musicinfo` | Muestra información de la canción actual |

### Máquina Slimefun

- **YouTube Music Machine**: Reproduce música en 3D alrededor del bloque
- La música se escucha solo cuando el jugador está cerca (configurable en config.yml)
- Volumen y rango ajustables por distancia

## Arquitectura

```
YouTube URL → YouTubeDownloader → Stream de audio
    ↓
FFmpeg → Decodificación a PCM (16-bit, 48kHz, estéreo)
    ↓
Simple Voice Chat API → AudioPacket (3D espacial, 20ms frames)
    ↓
Jugadores cercanos → Audio 3D (se atenúa con la distancia)
```
{