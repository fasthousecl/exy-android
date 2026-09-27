# Exy

Asistente de voz personalizado para Android. Exy escucha en segundo plano la
frase **«Oye Exi»** y, al oírla, vibra, suelta el micrófono y abre la app de IA
que le pidas, sin tener que cambiar el asistente predeterminado del teléfono.
Pasado un tiempo (60 s por defecto) vuelve a escuchar solo.

| Dices | Abre |
|---|---|
| «Oye Exi» | **Gemini** (favorito) |
| «Oye Exi, vamos a Claude Code» | **Claude Code** (claude.ai/code) |
| «Oye Exi, Claude» o «Oye Exi, abre Claude» | **Claude** |
| «Oye Exi, ChatGPT» | **ChatGPT** |
| «Oye Exi, Gemini» | **Gemini** |

«Vamos a Claude…» siempre va a Claude Code, aunque Exy no alcance a oír el
"code". Dilo de corrido, sin pausa después de «Oye Exi»: si haces una pausa larga, Exy
entiende solo «Oye Exi» y abre Gemini. Si una app no está instalada, se abre su
versión web (Gemini sin app cae en el asistente del sistema).

- Android 10 o superior (minSdk 29, targetSdk 36).
- Reconocimiento **sin internet** con [Vosk](https://alphacephei.com/vosk/) y su
  modelo pequeño en español. Nada de lo que dices sale del teléfono (internet
  solo se usa para avisar de versiones nuevas).
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

Abre **Exy**. La pantalla muestra al centro la X del logo con el estado actual:
apagada (gris), **escuchando** (menta, con un halo que respira) o, después de
abrir una app, un anillo que cuenta el tiempo hasta volver a escuchar.

1. **Conceder micrófono**: es lo único obligatorio para empezar.
2. **Primeros pasos**: mientras falte algún permiso aparece una lista con su
   progreso. Pulsa **Conceder** en cada uno:
   - **Notificaciones** → *Permitir*. La notificación fija mantiene a Exy activo
     y trae *Pausar / Reanudar*, *Escuchar ya* y *Detener*.
   - **Abrir apps desde segundo plano** (permiso *Mostrar sobre otras apps*) →
     busca **Exy** y actívalo. Sin esto Android no deja abrir las apps.
   - **Sin límite de batería** → *Permitir*.

   Cuando están todos, la lista se reduce a **"Permisos listos"**.
3. Pulsa **Iniciar escucha**. La primera vez tarda unos segundos: copia el
   modelo de voz a la memoria interna. Luego ya puedes salir de la app.

**Ajustes** (al final de la pantalla):

- **Precisión**: *Estricta*, *Normal* (recomendada) o *Sensible*. Sensible
  detecta más, pero se activa sola más seguido.
- **Micrófono libre para la app**: 30 s, 1 min, 2 min o 5 min antes de volver a
  escuchar «Oye Exi». En la espera, **Escuchar ya** la corta.

Bajo el estado, **"último: «…»"** muestra lo que Vosk entendió. Sirve para
ajustar: si dices «Oye Exi», aparece la frase y no se activa, sube a
*Sensible*; si se activa sola, baja a *Estricta*.

En **Comandos** está la lista de frases, a qué app va cada una y si esa app está
instalada (*Web* si no). La flecha de cada fila abre la app para probarla sin
hablar.

---

### Herramientas y funciones extra

- **Prueba tu voz**: di un comando y mira qué entendió Exy y si se habría
  activado (y con qué confianza), sin abrir ninguna app.
- **Historial**: las últimas 50 veces que Exy oyó «Oye Exi», qué abrió y cuáles
  ignoró por confianza baja. Útil para saber si se activa sola.
- **Comandos propios**: en *Agregar o cambiar comandos → Agregar app* eliges una
  app (Spotify, WhatsApp…) y dices su nombre. Exy guarda lo que entendió (hasta
  3 formas) y desde ahí «Oye Exi, Spotify» la abre.
- **Favorito**: en la misma pantalla eliges qué abre «Oye Exi» a secas.
- **Horario de descanso**: entre las horas que elijas el micrófono queda libre;
  Exy vuelve a escuchar sola al terminar (con unos minutos de margen).
- **Solo con audífonos**: Exy escucha solo con audífonos Bluetooth conectados.
- **Botón en ajustes rápidos**: baja la cortina, toca el lápiz y arrastra
  **Exy**. Un toque pausa o reanuda; si estaba detenida, abre la app y empieza.
- **Actualizaciones**: al abrir la app revisa (como mucho cada 6 horas) si hay
  una versión nueva en GitHub y muestra *Descargar*. Necesita que el
  repositorio sea público. Es la única conexión a internet de Exy.
- **WhatsApp, Maps y otras tareas**: di «Oye Exi» y pídeselo a Gemini
  («envía un WhatsApp a Juan diciendo…», «navega a…»).

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
2. Vosk usa una **gramática restringida**: solo puede reconocer las frases de
   [`comandos.json`](app/src/main/assets/comandos.json) y «[unk]» (cualquier
   otra cosa). Cada frase es *oye + exi + [prefijo] + [destino]*. Como "exi" y
   "ChatGPT" no son palabras del español, se aceptan las formas en que el modelo
   las oye (*exi, exis, ex si, equis*; *chat ge pe te, chat ji pi ti*…). Además,
   cada palabra debe superar una confianza mínima que fija el control de
   sensibilidad.
3. Al detectar la frase: vibración corta → Vosk se detiene y libera el
   micrófono → se abre la app correspondiente.

   | Destino | Qué abre | Si no está |
   |---|---|---|
   | Gemini | app `com.google.android.apps.bard` | asistente del sistema |
   | Claude Code | `https://claude.ai/code` en la app de Claude | navegador |
   | Claude | app `com.anthropic.claude` | claude.ai |
   | ChatGPT | app `com.openai.chatgpt` | chatgpt.com |
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
- Revisa que todas las palabras de `comandos.json` existan en el vocabulario del
  modelo (si falta una, la compilación falla con el nombre de la palabra) y
  prueba las frases con voz sintética.
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
├── Destino.kt            # Las apps que Exy abre (Gemini, Claude, ChatGPT…)
├── WakeWordService.kt    # Servicio en primer plano con Vosk
├── Comandos.kt           # Gramática y detección de «oye exi» + comando
├── VoskModel.kt          # Copia el modelo del APK a almacenamiento interno
├── OrbeView.kt           # Anillo del estado (escuchando, cuenta regresiva)
└── ExySettings.kt        # Precisión y tiempo de reanudación

app/src/main/assets/comandos.json   # Frases y variantes que Exy reconoce
```

### Compilar en un computador (opcional)

Descarga y descomprime el modelo en `app/src/main/assets/model-es`, crea dentro
un archivo `uuid` con cualquier texto (por ejemplo, el nombre del modelo) y
corre `./gradlew assembleDebug`. Sin el modelo el APK compila, pero la app
avisa que el modelo de voz no viene incluido.

### Privacidad y firma

- El audio nunca sale del teléfono: Vosk funciona sin conexión. El permiso
  `INTERNET` se usa solo para leer la última versión publicada en GitHub. La
  app no se respalda en la nube (`allowBackup=false`).
- `app/debug.keystore` es una llave de **depuración** con las credenciales
  públicas estándar de Android (`android` / `androiddebugkey`). No protege nada:
  existe solo para que cada APK se firme igual y se pueda actualizar encima del
  anterior. Si algún día la app se publica en Google Play, habrá que crear una
  llave de release y guardarla como *secret* de GitHub, nunca en el repositorio.
