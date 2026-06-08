import cv2
import time
import os
import csv

import mediapipe as mp
from ultralytics import YOLO

# Pastas usadas para ler os videos de entrada e guardar os resultados.
VIDEOS_DIR = "videos"
RESULTADOS_DIR = "resultados"

# Garante que a pasta de resultados existe antes de escrever videos ou CSV.
os.makedirs(RESULTADOS_DIR, exist_ok=True)

# Se yolo26n-pose.pt não funcionar troca para:
# yolov8n-pose.pt
YOLO_MODEL = "yolo26n-pose.pt"


def processar_mediapipe(video_path):
    # Processa um video com MediaPipe Pose e grava o video anotado.

    # Inicializa os modulos usados para detetar a pose e desenhar o esqueleto.
    mp_pose = mp.solutions.pose
    mp_drawing = mp.solutions.drawing_utils

    # Usa o nome do ficheiro original para gerar o nome do resultado.
    nome = os.path.splitext(os.path.basename(video_path))[0]

    output_path = os.path.join(
        RESULTADOS_DIR,
        f"{nome}_mediapipe.mp4"
    )

    # Abre o video e recolhe as propriedades necessarias para o ficheiro de saida.
    cap = cv2.VideoCapture(video_path)

    largura = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    altura = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps_original = cap.get(cv2.CAP_PROP_FPS)

    # Cria um video de saida com a mesma resolucao e FPS do original.
    writer = cv2.VideoWriter(
        output_path,
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps_original,
        (largura, altura)
    )

    total_frames = 0
    tempos = []

    # Configura o detetor de pose do MediaPipe para processar video frame a frame.
    with mp_pose.Pose(
        static_image_mode=False,
        model_complexity=1,
        enable_segmentation=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5
    ) as pose:

        while True:

            # Le um frame; quando nao ha mais frames, termina o ciclo.
            ret, frame = cap.read()

            if not ret:
                break

            # Mede o tempo gasto pelo modelo neste frame.
            inicio = time.time()

            # Converte BGR (OpenCV) para RGB (formato esperado pelo MediaPipe).
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

            resultado = pose.process(rgb)

            fim = time.time()

            tempos.append(fim - inicio)

            if resultado.pose_landmarks:

                # Desenha os pontos e ligacoes da pose detetada no frame.
                mp_drawing.draw_landmarks(
                    frame,
                    resultado.pose_landmarks,
                    mp_pose.POSE_CONNECTIONS
                )

            fps_atual = 1 / (tempos[-1] + 1e-9)

            # Escreve o FPS instantaneo no video anotado.
            cv2.putText(
                frame,
                f"MediaPipe | FPS: {fps_atual:.1f}",
                (20, 40),
                cv2.FONT_HERSHEY_SIMPLEX,
                1,
                (0, 255, 0),
                2
            )

            writer.write(frame)

            total_frames += 1

    # Fecha os recursos de leitura e escrita de video.
    cap.release()
    writer.release()

    # Calcula o FPS medio usando o tempo total de inferencia.
    fps_medio = (
        total_frames / sum(tempos)
        if sum(tempos) > 0 else 0
    )

    return {
        "video": nome,
        "modelo": "MediaPipe BlazePose",
        "frames": total_frames,
        "fps_medio": round(fps_medio, 2),
        "ficheiro_saida": output_path
    }


def processar_yolo(video_path):
    # Processa um video com YOLO Pose e grava o video anotado.

    # Carrega o modelo YOLO definido na constante YOLO_MODEL.
    model = YOLO(YOLO_MODEL)

    # Extrai o nome do video para nomear o ficheiro de saida.
    nome = os.path.splitext(
        os.path.basename(video_path)
    )[0]

    output_path = os.path.join(
        RESULTADOS_DIR,
        f"{nome}_yolo.mp4"
    )

    # Abre o video e le as propriedades usadas pelo VideoWriter.
    cap = cv2.VideoCapture(video_path)

    largura = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    altura = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps_original = cap.get(cv2.CAP_PROP_FPS)

    # Prepara o video de saida com as dimensoes e FPS do original.
    writer = cv2.VideoWriter(
        output_path,
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps_original,
        (largura, altura)
    )

    total_frames = 0
    tempos = []

    while True:

        # Le os frames sequencialmente ate ao fim do video.
        ret, frame = cap.read()

        if not ret:
            break

        # Mede o tempo da inferencia YOLO no frame atual.
        inicio = time.time()

        results = model(frame, verbose=False)

        # Gera um frame ja desenhado com keypoints e esqueleto.
        annotated = results[0].plot()

        fim = time.time()

        tempos.append(fim - inicio)

        fps_atual = 1 / (tempos[-1] + 1e-9)

        # Mostra o FPS instantaneo no frame anotado.
        cv2.putText(
            annotated,
            f"YOLO Pose | FPS: {fps_atual:.1f}",
            (20, 40),
            cv2.FONT_HERSHEY_SIMPLEX,
            1,
            (0, 255, 255),
            2
        )

        writer.write(annotated)

        total_frames += 1

    # Liberta os recursos associados ao video.
    cap.release()
    writer.release()

    # Calcula o FPS medio do processamento YOLO.
    fps_medio = (
        total_frames / sum(tempos)
        if sum(tempos) > 0 else 0
    )

    return {
        "video": nome,
        "modelo": "YOLO Pose",
        "frames": total_frames,
        "fps_medio": round(fps_medio, 2),
        "ficheiro_saida": output_path
    }


def main():

    resultados = []

    # Recolhe todos os ficheiros de video suportados na pasta de entrada.
    videos = [
        f for f in os.listdir(VIDEOS_DIR)
        if f.lower().endswith(
            (".mp4", ".avi", ".mov", ".mkv")
        )
    ]

    if not videos:
        print("Não encontrei vídeos na pasta videos/")
        return

    # Cada video e processado pelos dois modelos para comparar desempenho.
    for video in videos:

        video_path = os.path.join(
            VIDEOS_DIR,
            video
        )

        print(
            f"\nA processar {video} com MediaPipe..."
        )

        resultados.append(
            processar_mediapipe(video_path)
        )

        print(
            f"A processar {video} com YOLO Pose..."
        )

        resultados.append(
            processar_yolo(video_path)
        )

    # Guarda o resumo da comparacao num ficheiro CSV.
    csv_path = os.path.join(
        RESULTADOS_DIR,
        "comparacao_resultados.csv"
    )

    with open(
        csv_path,
        "w",
        newline="",
        encoding="utf-8"
    ) as f:

        writer = csv.DictWriter(
            f,
            fieldnames=[
                "video",
                "modelo",
                "frames",
                "fps_medio",
                "ficheiro_saida"
            ]
        )

        writer.writeheader()

        writer.writerows(resultados)

    print("\nConcluído!")

    # Apresenta no terminal cada registo guardado no CSV.
    for r in resultados:
        print(r)

    print(
        f"\nResultados guardados em: "
        f"{RESULTADOS_DIR}/"
    )


if __name__ == "__main__":
    main()
