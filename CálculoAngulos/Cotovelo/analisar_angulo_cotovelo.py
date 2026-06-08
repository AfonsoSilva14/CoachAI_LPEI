import cv2
import mediapipe as mp
from mediapipe.tasks import python as mp_tasks
from mediapipe.tasks.python import vision
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import os
import urllib.request

# Caminhos principais: video analisado, outputs gerados e modelo MediaPipe usado.
VIDEO_PATH = "deslizamento_escapular_p1.mp4"
os.makedirs("resultados", exist_ok=True)
OUTPUT_CSV = "resultados/angulos_cotovelo.csv"
OUTPUT_GRAFICO = "resultados/grafico_angulo_cotovelo.png"
MODEL_PATH = "pose_landmarker_heavy.task"


def download_modelo():
    # Descarrega o modelo de pose apenas se ainda nao existir localmente.
    url = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task"
    if not os.path.exists(MODEL_PATH):
        print("A descarregar modelo...")
        urllib.request.urlretrieve(url, MODEL_PATH)
        print("Modelo descarregado.")


def calcular_angulo(a, b, c):
    # Calcula o angulo formado por tres pontos, usando b como ponto central.
    a = np.array(a)
    b = np.array(b)
    c = np.array(c)
    # Vetores que saem do ponto central para os outros dois pontos.
    ba = a - b
    bc = c - b
    # Formula do produto escalar para obter o cosseno do angulo.
    coseno = np.dot(ba, bc) / (np.linalg.norm(ba) * np.linalg.norm(bc))
    # Limita o valor para evitar erros numericos no arccos.
    coseno = np.clip(coseno, -1.0, 1.0)
    return np.degrees(np.arccos(coseno))


def main():
    # Garante que o modelo esta disponivel antes de iniciar a analise.
    download_modelo()

    # Abre o video que vai ser analisado.
    cap = cv2.VideoCapture(VIDEO_PATH)
    if not cap.isOpened():
        print("Erro: não foi possível abrir o vídeo.")
        return

    # Guarda informacao basica do video e inicializa a lista de resultados.
    fps = cap.get(cv2.CAP_PROP_FPS)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    dados = []
    frame_id = 0

    # Configura o MediaPipe Pose Landmarker para funcionar em modo video.
    base_options = mp_tasks.BaseOptions(model_asset_path=MODEL_PATH)
    options = vision.PoseLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_tracking_confidence=0.5
    )

    # Cria o detetor e processa o video frame a frame.
    with vision.PoseLandmarker.create_from_options(options) as landmarker:
        while True:
            # Le o proximo frame; quando falha, o video terminou.
            ret, frame = cap.read()
            if not ret:
                break

            # Converte o frame para RGB e para o formato de imagem do MediaPipe.
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)

            # timestamp em milissegundos
            timestamp_ms = int((frame_id / fps) * 1000)
            # Deteta os pontos corporais correspondentes ao instante atual do video.
            results = landmarker.detect_for_video(mp_image, timestamp_ms)

            if results.pose_landmarks:
                lm = results.pose_landmarks[0]  # primeira pessoa detetada

                # Índices iguais ao anterior: 11=ombro esq, 13=cotovelo esq, 15=pulso esq
                ombro_esq    = lm[11]
                cotovelo_esq = lm[13]
                pulso_esq    = lm[15]

                # Angulo do cotovelo esquerdo: ombro-cotovelo-pulso.
                angulo_esq = calcular_angulo(
                    [ombro_esq.x,    ombro_esq.y],
                    [cotovelo_esq.x, cotovelo_esq.y],
                    [pulso_esq.x,    pulso_esq.y]
                )

                ombro_dir    = lm[12]
                cotovelo_dir = lm[14]
                pulso_dir    = lm[16]

                # Angulo do cotovelo direito: ombro-cotovelo-pulso.
                angulo_dir = calcular_angulo(
                    [ombro_dir.x,    ombro_dir.y],
                    [cotovelo_dir.x, cotovelo_dir.y],
                    [pulso_dir.x,    pulso_dir.y]
                )

                # Guarda o resultado deste frame para depois exportar para CSV/grafico.
                dados.append({
                    "frame": frame_id,
                    "tempo_segundos": frame_id / fps,
                    "angulo_cotovelo_esquerdo": angulo_esq,
                    "angulo_cotovelo_direito":  angulo_dir
                })

            # Avanca o contador mesmo quando nao ha pose detetada no frame.
            frame_id += 1

    cap.release()

    # Converte os resultados para tabela e grava em CSV.
    df = pd.DataFrame(dados)
    df.to_csv(OUTPUT_CSV, index=False)

    # Cria um grafico com a evolucao dos angulos ao longo do tempo.
    plt.figure(figsize=(12, 6))
    plt.plot(df["tempo_segundos"], df["angulo_cotovelo_esquerdo"], label="Cotovelo esquerdo")
    plt.plot(df["tempo_segundos"], df["angulo_cotovelo_direito"],  label="Cotovelo direito")
    plt.xlabel("Tempo (s)")
    plt.ylabel("Ângulo (graus)")
    plt.title("Variação angular do cotovelo - Ombro/Cotovelo/Pulso")
    plt.legend()
    plt.grid(True)
    plt.savefig(OUTPUT_GRAFICO)
    plt.show()

    # Mostra os ficheiros gerados e algumas estatisticas simples dos angulos.
    print("CSV guardado em:", OUTPUT_CSV)
    print("Gráfico guardado em:", OUTPUT_GRAFICO)
    print("\nResumo:")
    print("Ângulo médio cotovelo esquerdo:", df["angulo_cotovelo_esquerdo"].mean())
    print("Ângulo médio cotovelo direito:",  df["angulo_cotovelo_direito"].mean())
    print("Ângulo mínimo/máximo esquerdo:", df["angulo_cotovelo_esquerdo"].min(), "/", df["angulo_cotovelo_esquerdo"].max())
    print("Ângulo mínimo/máximo direito:",  df["angulo_cotovelo_direito"].min(),  "/", df["angulo_cotovelo_direito"].max())


if __name__ == "__main__":
    main()
