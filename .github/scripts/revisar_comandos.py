"""Revisa app/src/main/assets/comandos.json contra el modelo de Vosk.

1. Falla si alguna palabra no está en el vocabulario del modelo (Vosk la
   descartaría en silencio y el comando no funcionaría nunca).
2. Informativo: sintetiza las frases de "pruebas" con espeak-ng y muestra qué
   entiende Vosk. La voz sintética es robótica, así que un fallo aquí no rompe
   la compilación; sirve para detectar problemas groseros.
"""
import json
import os
import subprocess
import sys
import time

MODELO = sys.argv[1]
COMANDOS = "app/src/main/assets/comandos.json"

c = json.load(open(COMANDOS, encoding="utf-8"))

# Misma gramática que Comandos.kt
activaciones = [f"{o} {e}" for o in c["oye"] for e in c["exi"]]
destinos = [d for lista in c["destinos"].values() for d in lista]
ordenes = destinos + [f"{p} {d}" for p in c["prefijos"] for d in destinos]
gramatica = activaciones + [f"{a} {o}" for a in activaciones for o in ordenes] + c["oye"] + ["[unk]"]
palabras = sorted({w for frase in gramatica if frase != "[unk]" for w in frase.split()})
print(f"Gramática: {len(gramatica)} frases, {len(palabras)} palabras distintas")

# 1) Vocabulario: Vosk avisa por stderr las palabras que no conoce.
codigo = (
    "import json, sys, vosk\n"
    "m = vosk.Model(sys.argv[1])\n"
    "vosk.KaldiRecognizer(m, 16000, sys.argv[2])\n"
)
r = subprocess.run(
    [sys.executable, "-c", codigo, MODELO, json.dumps(palabras + ["[unk]"])],
    capture_output=True, text=True,
)
faltan = sorted({l.split("'")[1] for l in r.stderr.splitlines() if "missing in vocabulary" in l})
if r.returncode != 0:
    print(r.stderr[-3000:])
    sys.exit("No se pudo cargar el modelo")
if faltan:
    print("::error::Palabras que el modelo no conoce (quítalas de comandos.json): " + ", ".join(faltan))
    sys.exit(1)
print("✓ Todas las palabras existen en el vocabulario del modelo")

# 2) Prueba con voz sintética.
import vosk  # noqa: E402

vosk.SetLogLevel(-1)
modelo = vosk.Model(MODELO)
t = time.time()
vosk.KaldiRecognizer(modelo, 16000, json.dumps(gramatica))
print(f"Crear el reconocedor con la gramática tardó {time.time() - t:.2f} s (en el runner)")

for frase, esperado in c.get("pruebas", {}).items():
    audio = subprocess.run(
        f'espeak-ng -v es -s 150 --stdout "{frase}" | ffmpeg -loglevel error -i - -ar 16000 -ac 1 -f s16le -',
        shell=True, capture_output=True,
    ).stdout
    rec = vosk.KaldiRecognizer(modelo, 16000, json.dumps(gramatica))
    rec.SetWords(True)
    rec.AcceptWaveform(b"\0\0" * 8000 + audio + b"\0\0" * 16000)
    texto = json.loads(rec.FinalResult()).get("text", "")
    print(f"  «{frase}» ({esperado}) → Vosk entendió: «{texto}»")
