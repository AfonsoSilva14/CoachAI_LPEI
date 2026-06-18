import cv2
import mediapipe as mp
from mediapipe.tasks import python as mp_tasks
from mediapipe.tasks.python import vision
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import os
import urllib.request

VIDEO_PATH = "gisela_agachamento_for_022_lat-1.mp4"

os.makedirs("resultados", exist_ok=True)

OUTPUT_CSV = "resultados/angulos_joelho_direito.csv"
OUTPUT_GRAFICO = "resultados/grafico_angulo_joelho_direito.png"

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


def main():
    download_modelo()

    cap = cv2.VideoCapture(VIDEO_PATH)

    if not cap.isOpened():
        print("Erro: não foi possível abrir o vídeo.")
        return

    fps = cap.get(cv2.CAP_PROP_FPS)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

    dados = []
    frame_id = 0

    base_options = mp_tasks.BaseOptions(model_asset_path=MODEL_PATH)

    options = vision.PoseLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_tracking_confidence=0.5
    )

    with vision.PoseLandmarker.create_from_options(options) as landmarker:
        while True:
            ret, frame = cap.read()

            if not ret:
                break

            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(
                image_format=mp.ImageFormat.SRGB,
                data=rgb
            )

            timestamp_ms = int((frame_id / fps) * 1000)

            results = landmarker.detect_for_video(
                mp_image,
                timestamp_ms
            )

            if results.pose_landmarks:
                lm = results.pose_landmarks[0]

                # Joelho direito:
                # 24 = anca direita
                # 26 = joelho direito
                # 28 = tornozelo direito
                anca_dir = lm[24]
                joelho_dir = lm[26]
                tornozelo_dir = lm[28]

                angulo_joelho_dir = calcular_angulo(
                    [anca_dir.x, anca_dir.y],
                    [joelho_dir.x, joelho_dir.y],
                    [tornozelo_dir.x, tornozelo_dir.y]
                )

                dados.append({
                    "frame": frame_id,
                    "tempo_segundos": frame_id / fps,
                    "angulo_joelho_direito": angulo_joelho_dir
                })

            frame_id += 1

    cap.release()

    df = pd.DataFrame(dados)

    df.to_csv(OUTPUT_CSV, index=False)

    plt.figure(figsize=(12, 6))

    plt.plot(
        df["tempo_segundos"],
        df["angulo_joelho_direito"],
        label="Joelho direito"
    )

    plt.xlabel("Tempo (s)")
    plt.ylabel("Ângulo (graus)")
    plt.title("Variação angular do joelho direito - Anca/Joelho/Tornozelo")
    plt.legend()
    plt.grid(True)
    plt.savefig(OUTPUT_GRAFICO)
    plt.show()

    print("CSV guardado em:", OUTPUT_CSV)
    print("Gráfico guardado em:", OUTPUT_GRAFICO)

    print("\nResumo:")
    print("Ângulo médio joelho direito:", df["angulo_joelho_direito"].mean())
    print(
        "Ângulo mínimo/máximo joelho direito:",
        df["angulo_joelho_direito"].min(),
        "/",
        df["angulo_joelho_direito"].max()
    )


if __name__ == "__main__":
    main()