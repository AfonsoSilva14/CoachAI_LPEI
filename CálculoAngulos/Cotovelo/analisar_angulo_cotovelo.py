import cv2
import mediapipe as mp
from mediapipe.tasks import python as mp_tasks
from mediapipe.tasks.python import vision
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import os
import urllib.request
from scipy.signal import butter, filtfilt

VIDEO_PATH = "deslizamento_escapular_p1.mp4"

PASTA_RESULTADOS = "resultados"
os.makedirs(PASTA_RESULTADOS, exist_ok=True)

OUTPUT_CSV_ORIGINAL = os.path.join(PASTA_RESULTADOS, "angulos_cotovelo_original.csv")
OUTPUT_CSV_FILTRADO = os.path.join(PASTA_RESULTADOS, "angulos_cotovelo_filtrado.csv")

OUTPUT_GRAFICO_ORIGINAL = os.path.join(PASTA_RESULTADOS, "original_cotovelos_juntos.png")
OUTPUT_GRAFICO_ESQ = os.path.join(PASTA_RESULTADOS, "comparacao_cotovelo_esquerdo.png")
OUTPUT_GRAFICO_DIR = os.path.join(PASTA_RESULTADOS, "comparacao_cotovelo_direito.png")

MODEL_PATH = "pose_landmarker_heavy.task"


def download_modelo():
    url = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task"

    if not os.path.exists(MODEL_PATH):
        print("A descarregar modelo...")
        urllib.request.urlretrieve(url, MODEL_PATH)
        print("Modelo descarregado.")


def calcular_angulo(a, b, c):
    a = np.array(a)
    b = np.array(b)
    c = np.array(c)

    ba = a - b
    bc = c - b

    coseno = np.dot(ba, bc) / (np.linalg.norm(ba) * np.linalg.norm(bc))
    coseno = np.clip(coseno, -1.0, 1.0)

    return np.degrees(np.arccos(coseno))


def filtro_butterworth(sinal, fps, cutoff_hz=3, ordem=4):
    sinal = np.asarray(sinal)

    nyquist = 0.5 * fps

    if cutoff_hz >= nyquist:
        cutoff_hz = nyquist * 0.8
        print(f"Aviso: cutoff ajustado para {cutoff_hz:.2f} Hz")

    normal_cutoff = cutoff_hz / nyquist

    b, a = butter(ordem, normal_cutoff, btype="low", analog=False)
    return filtfilt(b, a, sinal)


def main():
    download_modelo()

    if not os.path.exists(VIDEO_PATH):
        print("Erro: vídeo não encontrado:", VIDEO_PATH)
        print("Pasta atual:", os.getcwd())
        return

    cap = cv2.VideoCapture(VIDEO_PATH)

    if not cap.isOpened():
        print("Erro: não foi possível abrir o vídeo.")
        return

    fps = cap.get(cv2.CAP_PROP_FPS)
    print("FPS do vídeo:", fps)

    dados = []
    frame_id = 0

    base_options = mp_tasks.BaseOptions(model_asset_path=MODEL_PATH)

    options = vision.PoseLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    with vision.PoseLandmarker.create_from_options(options) as landmarker:
        while True:
            ret, frame = cap.read()

            if not ret:
                break

            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)

            timestamp_ms = int((frame_id / fps) * 1000)
            results = landmarker.detect_for_video(mp_image, timestamp_ms)

            if results.pose_landmarks:
                lm = results.pose_landmarks[0]

                ombro_esq = lm[11]
                cotovelo_esq = lm[13]
                pulso_esq = lm[15]

                ombro_dir = lm[12]
                cotovelo_dir = lm[14]
                pulso_dir = lm[16]

                angulo_esq = calcular_angulo(
                    [ombro_esq.x, ombro_esq.y],
                    [cotovelo_esq.x, cotovelo_esq.y],
                    [pulso_esq.x, pulso_esq.y],
                )

                angulo_dir = calcular_angulo(
                    [ombro_dir.x, ombro_dir.y],
                    [cotovelo_dir.x, cotovelo_dir.y],
                    [pulso_dir.x, pulso_dir.y],
                )

                dados.append(
                    {
                        "frame": frame_id,
                        "tempo_segundos": frame_id / fps,
                        "angulo_cotovelo_esquerdo_original": angulo_esq,
                        "angulo_cotovelo_direito_original": angulo_dir,
                    }
                )

            frame_id += 1

    cap.release()

    df = pd.DataFrame(dados)

    if df.empty:
        print("Erro: não foram detetadas poses suficientes.")
        return

    df["angulo_cotovelo_esquerdo_filtrado"] = filtro_butterworth(
        df["angulo_cotovelo_esquerdo_original"], fps, cutoff_hz=3
    )

    df["angulo_cotovelo_direito_filtrado"] = filtro_butterworth(
        df["angulo_cotovelo_direito_original"], fps, cutoff_hz=3
    )

    ruido_esq = np.std(
        df["angulo_cotovelo_esquerdo_original"]
        - df["angulo_cotovelo_esquerdo_filtrado"]
    )

    ruido_dir = np.std(
        df["angulo_cotovelo_direito_original"]
        - df["angulo_cotovelo_direito_filtrado"]
    )

    df_original = df[
        [
            "frame",
            "tempo_segundos",
            "angulo_cotovelo_esquerdo_original",
            "angulo_cotovelo_direito_original",
        ]
    ]

    df_filtrado = df[
        [
            "frame",
            "tempo_segundos",
            "angulo_cotovelo_esquerdo_filtrado",
            "angulo_cotovelo_direito_filtrado",
        ]
    ]

    df_original.to_csv(OUTPUT_CSV_ORIGINAL, index=False)
    df_filtrado.to_csv(OUTPUT_CSV_FILTRADO, index=False)

    plt.figure(figsize=(12, 6))
    plt.plot(
        df["tempo_segundos"],
        df["angulo_cotovelo_esquerdo_original"],
        label="Cotovelo esquerdo original",
        linewidth=1.5,
    )
    plt.plot(
        df["tempo_segundos"],
        df["angulo_cotovelo_direito_original"],
        label="Cotovelo direito original",
        linewidth=1.5,
    )
    plt.xlabel("Tempo (s)")
    plt.ylabel("Ângulo (graus)")
    plt.title("Ângulo original dos cotovelos esquerdo e direito")
    plt.legend()
    plt.grid(True)
    plt.savefig(OUTPUT_GRAFICO_ORIGINAL, dpi=300)
    plt.show()

    plt.figure(figsize=(12, 6))
    plt.plot(
        df["tempo_segundos"],
        df["angulo_cotovelo_esquerdo_original"],
        label="Original com ruído",
        linewidth=1,
        alpha=0.5,
    )
    plt.plot(
        df["tempo_segundos"],
        df["angulo_cotovelo_esquerdo_filtrado"],
        label="Butterworth 3 Hz",
        linewidth=3,
    )
    plt.xlabel("Tempo (s)")
    plt.ylabel("Ângulo (graus)")
    plt.title("Comparação do cotovelo esquerdo: original vs filtrado")
    plt.legend()
    plt.grid(True)
    plt.savefig(OUTPUT_GRAFICO_ESQ, dpi=300)
    plt.show()

    plt.figure(figsize=(12, 6))
    plt.plot(
        df["tempo_segundos"],
        df["angulo_cotovelo_direito_original"],
        label="Original com ruído",
        linewidth=1,
        alpha=0.5,
    )
    plt.plot(
        df["tempo_segundos"],
        df["angulo_cotovelo_direito_filtrado"],
        label="Butterworth 3 Hz",
        linewidth=3,
    )
    plt.xlabel("Tempo (s)")
    plt.ylabel("Ângulo (graus)")
    plt.title("Comparação do cotovelo direito: original vs filtrado")
    plt.legend()
    plt.grid(True)
    plt.savefig(OUTPUT_GRAFICO_DIR, dpi=300)
    plt.show()

    print("CSV original:", os.path.abspath(OUTPUT_CSV_ORIGINAL))
    print("CSV filtrado:", os.path.abspath(OUTPUT_CSV_FILTRADO))
    print("Gráfico original:", os.path.abspath(OUTPUT_GRAFICO_ORIGINAL))
    print("Gráfico esquerdo:", os.path.abspath(OUTPUT_GRAFICO_ESQ))
    print("Gráfico direito:", os.path.abspath(OUTPUT_GRAFICO_DIR))

    print("\nResumo:")
    print("FPS do vídeo:", fps)

    print("\nCotovelo esquerdo:")
    print("Média original:", df["angulo_cotovelo_esquerdo_original"].mean())
    print("Média filtrada:", df["angulo_cotovelo_esquerdo_filtrado"].mean())
    print("Ruído removido:", ruido_esq)

    print("\nCotovelo direito:")
    print("Média original:", df["angulo_cotovelo_direito_original"].mean())
    print("Média filtrada:", df["angulo_cotovelo_direito_filtrado"].mean())
    print("Ruído removido:", ruido_dir)


if __name__ == "__main__":
    main()