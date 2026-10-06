# Space Invaders Android MVP

Aplicação Android nativa em Kotlin que reproduz um MVP do clássico Space Invaders em estilo arcade, com:

- SurfaceView e loop de jogo em passo fixo a 60 FPS
- resolução lógica 224x256 com escala sem suavização
- sprites pixel-art definidos em matrizes no código
- som sintetizado em tempo real com AudioTrack
- ecrãs de splash, menu, jogo, game over e high score persistente
- mecânicas básicas de formação alienígena, movimento, tiros, barreiras, vidas e pontuação

## Estrutura do projeto

- `app/src/main/java/com/spaceinvaders/spaceinvaders/MainActivity.kt` — activity principal
- `app/src/main/java/com/spaceinvaders/spaceinvaders/GameView.kt` — motor do jogo, renderização e lógica
- `app/src/main/java/com/spaceinvaders/spaceinvaders/SoundEngine.kt` — síntese sonora através de AudioTrack

## Requisitos

- Android Studio Hedgehog ou mais recente
- JDK 17
- Android SDK com plataforma Android 34 e build-tools 35.0.1
- Emulador configurado ou telemóvel Android com depuração USB habilitada

## Como correr no emulador ou telemóvel

1. Abra a pasta do projeto neste diretório em Android Studio.
2. Aguarde o Gradle sincronizar.
3. No menu superior:
   - `Build > Make Project` para compilar
   - `Run > Run 'app'` para instalar no emulador ou dispositivo
4. Se preferir via linha de comandos:

```powershell
cd "C:\Users\adpinheiro\OneDrive - IMPAR.CV\Ambiente de Trabalho\Pong\spaceinvadersandroid"
./gradlew.bat assembleDebug
```

## APK de debug

O APK gerado está em:

```text
C:\Users\adpinheiro\OneDrive - IMPAR.CV\Ambiente de Trabalho\Pong\spaceinvadersandroid\app\build\outputs\apk\debug\app-debug.apk
```

Pode instalar diretamente no dispositivo ou arrastá-lo para um emulador.

## Notas

- A aplicação foi desenvolvida como MVP fiel ao conceito de arcade, mantendo o motor em SurfaceView e respeitando o requisito de áudio totalmente sintetizado sem ficheiros externos.
- O high score é guardado em `SharedPreferences`.
- A dinâmica de gameplay foi implementada em Kotlin nativo, seguindo a resolução lógica baixa e o visual em preto/verde/vermelho/branco da máquina arcade.
