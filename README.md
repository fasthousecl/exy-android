# Exy

Asistente de voz personalizado para Android. Exy escucha en segundo plano una
palabra clave propia (hecha con [Picovoice Porcupine](https://picovoice.ai/platform/porcupine/))
y, al oírla, vibra, suelta el micrófono y abre el asistente de voz
predeterminado del teléfono (Google Gemini/Assistant, Bixby, etc.). Pasado un
tiempo (60 s por defecto) vuelve a escuchar solo.

- Android 10 o superior (minSdk 29, targetSdk 36).
- Todo se compila en la nube con GitHub Actions: no necesitas computador.
- La AccessKey y los archivos `.ppn` / `.pv` **nunca** van en este repositorio:
  se cargan desde la app y quedan solo en el teléfono (la clave, cifrada).

---

## 1. Lo que necesitas antes de empezar

Todo esto se hace desde el navegador del teléfono.

1. **Cuenta gratuita en Picovoice Console**: entra a
   <https://console.picovoice.ai/> y crea una cuenta.
2. **AccessKey**: en la portada de la consola aparece tu *AccessKey*. Cópiala
   (la vas a pegar en la app). Es personal: no la compartas ni la subas a GitHub.
3. **Palabra clave en español (`.ppn`)**:
   - En la consola entra a **Porcupine**.
   - Elige el idioma **Spanish**, escribe la palabra (por ejemplo, "Oye Exy") y
     pulsa **Train**.
   - Al terminar, descárgala eligiendo la plataforma **Android**. Llega un `.zip`;
     ábrelo con la app *Mis archivos* de Samsung y extrae el archivo `.ppn`.
4. **Modelo en español (`.pv`)**: descarga `porcupine_params_es.pv` desde
   <https://github.com/Picovoice/porcupine/tree/master/lib/common>
   (abre el archivo y pulsa el botón de descarga).

> El `.ppn` y el `.pv` deben ser del **mismo idioma** y de la **misma versión**
> de Porcupine. La app usa Porcupine 4.0.2; si la consola te ofrece elegir
> versión, elige la más reciente (4.x). Si no coinciden, la app lo avisa con el
> mensaje "el .ppn y el .pv no son compatibles".

---

## 2. Descargar e instalar el APK en el Samsung S25 Ultra

1. En el teléfono, abre el repositorio en GitHub y entra a **Releases** (en el
   navegador: `https://github.com/fasthousecl/exy-android/releases`).
2. Abre la versión más reciente (`Exy 1.0.N`) y toca el archivo `Exy-1.0.N.apk`
   en **Assets** para descargarlo.
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

## 3. Configurar Exy

Abre **Exy** y, de arriba a abajo:

1. **AccessKey de Picovoice**: pégala y pulsa **Guardar clave**. Queda cifrada
   con una llave del Android Keystore; la app no la vuelve a mostrar.
2. **Archivos del modelo**:
   - **Importar palabra clave (.ppn)** → elige el `.ppn` descargado.
   - **Importar modelo en español (.pv)** → elige `porcupine_params_es.pv`.

   Se copian a la memoria interna de la app. Después puedes borrar los
   originales de *Descargas*.
3. **Ajustes**:
   - **Sensibilidad** (0 a 1): más alta detecta mejor pero se activa por error
     más seguido. Empieza con 0,5.
   - **Reanudar escucha tras**: segundos que Exy deja libre el micrófono para el
     asistente (60 s por defecto).
4. **Permisos**: pulsa **Conceder** en cada uno.
   - **Micrófono** → *Mientras se usa la app*.
   - **Notificaciones** → *Permitir*. La notificación fija es la que mantiene a
     Exy activo y trae los botones *Pausar / Reanudar* y *Detener*.
   - **Mostrar sobre otras apps** → busca **Exy** y actívalo. Sin esto Android
     no deja abrir el asistente desde segundo plano.
   - **Sin optimización de batería** → *Permitir*.
5. Pulsa **Iniciar escucha**. Aparece la notificación "Escuchando la palabra
   clave". Ya puedes salir de la app.

Usa **Probar asistente** para comprobar qué asistente abre tu teléfono.

### Elegir el asistente predeterminado

Ajustes → **Aplicaciones** → **Elegir apps predeterminadas** → **Asistente
digital** (en algunos modelos: *App de asistencia del dispositivo*) → elige
**Google** / **Gemini** o **Bixby**.

---

## 4. Ajustes de Samsung para que no se duerma

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

## 5. Cómo funciona

1. Un servicio en primer plano (tipo `microphone`) mantiene a Porcupine
   escuchando. Porcupine procesa el audio en el teléfono; solo usa internet para
   validar la AccessKey.
2. Al detectar la palabra: vibración corta → Porcupine se detiene y libera el
   micrófono → se abre el asistente con `ACTION_VOICE_COMMAND` (si ninguna app lo
   atiende, `ACTION_ASSIST`).
3. La notificación muestra la cuenta regresiva; al terminar, Exy vuelve a
   escuchar. *Pausar* suelta el micrófono hasta que pulses *Reanudar*.
4. Si otra app toma el micrófono (una llamada, por ejemplo), Exy reintenta solo
   a los 15 segundos.

Limitación de Android 14+: si el sistema cierra Exy por falta de memoria, no
puede volver a encender el micrófono por sí solo. Abre la app y pulsa
**Iniciar escucha** otra vez.

---

## 6. Compilación en la nube

El workflow [`.github/workflows/build.yml`](.github/workflows/build.yml):

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
├── WakeWordService.kt    # Servicio en primer plano con Porcupine
├── AssistantLauncher.kt  # Abre el asistente predeterminado
├── SecureStore.kt        # AccessKey cifrada con Android Keystore
├── ExySettings.kt        # Sensibilidad y tiempo de reanudación
└── ModelFiles.kt         # Importa .ppn y .pv a almacenamiento interno
```

### Seguridad

- La AccessKey se cifra con AES-256-GCM y una llave del Android Keystore que no
  sale del teléfono. La app no se respalda en la nube (`allowBackup=false`).
- `.gitignore` bloquea `*.ppn`, `*.pv` y archivos de claves.
- `app/debug.keystore` es una llave de **depuración** con las credenciales
  públicas estándar de Android (`android` / `androiddebugkey`). No protege nada:
  existe solo para que cada APK se firme igual y se pueda actualizar encima del
  anterior. Si algún día la app se publica en Google Play, habrá que crear una
  llave de release y guardarla como *secret* de GitHub, nunca en el repositorio.
