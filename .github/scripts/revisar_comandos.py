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
debiles = [f"{o} {e}" for o in c["oye"] for e in c.get("exi_debil", [])]
destinos = [d for lista in c["destinos"].values() for d in lista]
atajos = c.get("atajos", {})
ordenes = list(dict.fromkeys(destinos + [f"{p} {d}" for p in c["prefijos"] for d in destinos] + list(atajos)))
gramatica = activaciones + [f"{a} {o}" for a in activaciones for o in ordenes] + c["oye"] + ["[unk]"]
destino_de = {d: id_ for id_, lista in c["destinos"].items() for d in lista}


def rutear(texto):
    """Misma lógica que Comandos.detectar() (sin la confianza)."""
    palabras = texto.split()
    for i in range(len(palabras)):
        for a in activaciones + debiles:
            partes = a.split()
            if palabras[i:i + len(partes)] == partes:
                resto = " ".join(w for w in palabras[i + len(partes):] if w != "[unk]")
                if not resto:
                    return None if a in debiles else "gemini"
                orden = resto
                for p in sorted(c["prefijos"], key=len, reverse=True):
                    if resto.startswith(p + " "):
                        orden = resto[len(p) + 1:]
                        break
                return atajos.get(resto) or destino_de.get(orden)
    return None
palabras = sorted({w for frase in gramatica if frase != "[unk]" for w in frase.split()})
print(f"Gramática: {len(gramatica)} frases, {len(palabras)} palabras distintas")

# 1) Vocabulario: Vosk avisa por stderr las palabras que no conoce.
codigo = (
    "import json, sys, vosk\n"
    "m = vosk.Model(sys.argv[1])\n"
    "vosk.KaldiRecognizer(m, 16000, sys.argv[2])\n"
)
r = subprocess.run(
    [sys.executable, "-c", codigo, MODELO, json.dumps(palabras + ["[unk]"], ensure_ascii=False)],
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
vosk.KaldiRecognizer(modelo, 16000, json.dumps(gramatica, ensure_ascii=False))
print(f"Crear el reconocedor con la gramática tardó {time.time() - t:.2f} s (en el runner)")

import io  # noqa: E402
import wave  # noqa: E402


def probar(frase, esperado):
    wav = subprocess.run(
        ["espeak-ng", "-v", "es", "-s", "140", "--stdout", frase], capture_output=True,
    ).stdout
    with wave.open(io.BytesIO(wav)) as w:
        tasa = w.getframerate()
        audio = w.readframes(w.getnframes())
    silencio = b"\0\0" * (tasa // 2)

    def reconocer(gram):
        rec = vosk.KaldiRecognizer(modelo, tasa, gram) if gram else vosk.KaldiRecognizer(modelo, tasa)
        rec.SetWords(True)
        rec.AcceptWaveform(silencio + audio + silencio * 2)
        return json.loads(rec.FinalResult()).get("text", "")

    con = reconocer(json.dumps(gramatica, ensure_ascii=False))
    libre = reconocer(None)
    ruta = rutear(con)
    marca = "✓" if ruta == esperado else "✗"
    print(f"  {marca} «{frase}» → entendió «{con}» → abre {ruta} (esperado {esperado}) | sin gramática: «{libre}»")


print("Prueba con voz sintética (informativa):")
for frase, esperado in c.get("pruebas", {}).items():
    try:
        probar(frase, esperado)
    except Exception as e:  # nunca rompe la compilación
        print(f"  «{frase}»: no se pudo probar ({e})")
