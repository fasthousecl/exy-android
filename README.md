# Exy

Asistente de voz personalizado para Android. Exy escucha en segundo plano la
frase **«Oye Exi»** y, al oírla, vibra, suelta el micrófono y abre el asistente
de voz predeterminado del teléfono (Google Gemini/Assistant, Bixby, etc.).
Pasado un tiempo (60 s por defecto) vuelve a escuchar solo.

- Android 10 o superior (minSdk 29, targetSdk 36).
- Reconocimiento **sin internet** con [Vosk](https://alphacephei.com/vosk/) y su
  modelo pequeño en español. Nada de lo que dices sale del teléfono.
- Sin cuentas ni claves: el modelo de voz viene incluido en el APK.
- Todo se compila en la nube con GitHub Actions: no necesitas computador.

---

## 1. Descargar e instalar el APK en el Samsung S25 Ultra

1. En el teléfono, abre el repositorio en GitHub y entra a **Releases** (en el
   navegador: `https://github.com/fasthousecl/exy-android/releases`).
2. Abre la versión más reciente (`Exy 1.0.N`, marcada como *Latest*) y toca el
   archivo `Exy-1.0.N.apk` en **Assets** para descargarlo (unos 50 MB).
   - Como el repositorio es privado, tienes que haber iniciado sesión en GitHub
     en ese navegador.
3. Toca la notificación de descarga (o búscalo en *Mis archivos → Descargas*).
4. La primera vez Android dice que el navegador no puede instalar apps:
   pulsa **Ajustes**, activa **Permitir desde esta fuente** y vuelve atrás.
5. Pulsa **Instalar**. Si aparece *Google Play Protect* avisando que la app es
   desconocida, pulsa **Más detalles → Instalar de todas formas**.
   - En One UI 6.1+ puede estar activo **Auto Blocker** (Ajustes → Seguridad y
     privacidad → Auto Blocker), que impide instalar APKs. Desactívalo mientras
     instalas y vuelve a activarlo después si quieres.

Las versiones nuevas se instalan encima de la anterior, sin perder la
configuración: todas se firman con la misma llave de depuración.

---

## 2. Configurar Exy

Abre **Exy** y, de arriba a abajo:

1. **Permisos**: pulsa **Conceder** en cada uno.
   - **Micrófono** → *Mientras se usa la app*.
   - **Notificaciones** → *Permitir*. La notificación fija es la que mantiene a
     Exy activo y trae los botones *Pausar / Reanudar* y *Detener*.
   - **Mostrar sobre otras apps** → busca **Exy** y actívalo. Sin esto Android
     no deja abrir el asistente desde segundo plano.
   - **Sin optimización de batería** → *Permitir*.
2. **Ajustes**:
   - **Sensibilidad** (0 a 1): más alta acepta la frase aunque Exy no esté del
     todo seguro; detecta más, pero se activa por error más seguido. Empieza
     con 0,5.
   - **Reanudar escucha tras**: segundos que Exy deja libre el micrófono para el
     asistente (60 s por defecto).
3. Pulsa **Iniciar escucha**. La primera vez tarda unos segundos: copia el
   modelo de voz a la memoria interna. Luego aparece la notificación
   "Escuchando «Oye Exi»" y ya puedes salir de la app.

Bajo el estado, **"Último que escuché"** muestra lo que Vosk entendió. Sirve
para ajustar: si dices «Oye Exi» y ahí aparece la frase pero no se activa,
sube la sensibilidad; si se activa sola, bájala.

Usa **Probar asistente** para comprobar qué asistente abre tu teléfono.

### Elegir el asistente predeterminado

Ajustes → **Aplicaciones** → **Elegir apps predeterminadas** → **Asistente
digital** (en algunos modelos: *App de asistencia del dispositivo*) → elige
**Google** / **Gemini** o **Bixby**.

---

## 3. Ajustes de Samsung para que no se duerma

One UI es agresivo cerrando apps en segundo plano. Además del permiso de
batería:

1. Ajustes → **Batería** → **Límites de uso en segundo plano**:
   - Desactiva **Poner apps sin usar en suspensión**, o
   - Agrega **Exy** a **Apps que nunca se suspenden**.
2. Ajustes → Aplicaciones → **Exy** → **Batería** → **Sin restricciones**.
3. En Recientes, toca el ícono de Exy → **Mantener abierta** (candado), para que
   "Cerrar todo" no la cierre.

Con la pantalla apagada Exy mantiene la CPU despierta mientras escucha, así que
consume algo más de batería de lo normal. Pausa desde la notificación cuando no
lo necesites.

---

## 4. Cómo funciona

1. Un servicio en primer plano (tipo `microphone`) mantiene a Vosk escuchando.
   Todo el audio se procesa en el teléfono; la app ni siquiera pide permiso de
   internet.
2. Vosk usa una **gramática restringida**: solo puede reconocer «oye exi», sus
   variantes y «[unk]» (cualquier otra cosa). Como "exi" no es una palabra del
   español, se aceptan las formas en que el modelo la oye: *exi, equis, sexy,
   eksi, ex si*… precedidas de *oye, hoy, oy, hey*… (lista completa en
   [`WakePhrase.kt`](app/src/main/java/cl/exy/app/WakePhrase.kt)). Además, cada
   palabra debe superar una confianza mínima que fija el control de
   sensibilidad.
3. Al detectar la frase: vibración corta → Vosk se detiene y libera el
   micrófono → se abre el asistente con `ACTION_VOICE_COMMAND` (si ninguna app lo
   atiende, `ACTION_ASSIST`).
4. La notificación muestra la cuenta regresiva; al terminar, Exy vuelve a
   escuchar. *Pausar* suelta el micrófono hasta que pulses *Reanudar*.
5. Si otra app toma el micrófono (una llamada, por ejemplo), Exy reintenta solo
   a los 15 segundos.

Limitación de Android 14+: si el sistema cierra Exy por falta de memoria, no
puede volver a encender el micrófono por sí solo. Abre la app y pulsa
**Iniciar escucha** otra vez.

---

## 5. Compilación en la nube

El workflow [`.github/workflows/build.yml`](.github/workflows/build.yml):

- Descarga el modelo `vosk-model-small-es-0.42` desde alphacephei.com (queda en
  caché entre compilaciones), lo copia a `app/src/main/assets/model-es` y lo
  empaqueta dentro del APK. El modelo **no** está en el repositorio.
- Compila el APK debug en **cada push** a cualquier rama y lo deja como
  artefacto en la pestaña **Actions**.
- En cada push a **`main`**, además, crea un **GitHub Release** `v1.0.N` con el
  APK adjunto (N = número de ejecución, que también es el `versionCode`).
- Se puede lanzar a mano desde *Actions → Compilar APK → Run workflow*.

### Estructura

```
app/src/main/java/cl/exy/app/
├── ExyApp.kt             # Canal de notificación
├── MainActivity.kt       # Pantalla de configuración y permisos
├── AssistantLauncher.kt  # Abre el asistente predeterminado
├── WakeWordService.kt    # Servicio en primer plano con Vosk
├── WakePhrase.kt         # Gramática y variantes de «oye exi»
├── VoskModel.kt          # Copia el modelo del APK a almacenamiento interno
└── ExySettings.kt        # Sensibilidad y tiempo de reanudación
```

### Compilar en un computador (opcional)

Descarga y descomprime el modelo en `app/src/main/assets/model-es`, crea dentro
un archivo `uuid` con cualquier texto (por ejemplo, el nombre del modelo) y
corre `./gradlew assembleDebug`. Sin el modelo el APK compila, pero la app
avisa que el modelo de voz no viene incluido.

### Privacidad y firma

- El audio nunca sale del teléfono: Vosk funciona sin conexión y la app no
  declara el permiso `INTERNET`. La app no se respalda en la nube
  (`allowBackup=false`).
- `app/debug.keystore` es una llave de **depuración** con las credenciales
  públicas estándar de Android (`android` / `androiddebugkey`). No protege nada:
  existe solo para que cada APK se firme igual y se pueda actualizar encima del
  anterior. Si algún día la app se publica en Google Play, habrá que crear una
  llave de release y guardarla como *secret* de GitHub, nunca en el repositorio.
