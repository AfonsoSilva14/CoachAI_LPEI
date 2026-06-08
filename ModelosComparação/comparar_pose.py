import cv2
import time
import os
import csv

import mediapipe as mp
from ultralytics import YOLO

VIDEOS_DIR = "videos"
RESULTADOS_DIR = "resultados"

os.makedirs(RESULTADOS_DIR, exist_ok=True)

# Se yolo26n-pose.pt não funcionar troca para:
# yolov8n-pose.pt
YOLO_MODEL = "yolo26n-pose.pt"


def processar_mediapipe(video_path):

    mp_pose = mp.solutions.pose
    mp_drawing = mp.solutions.drawing_utils

    nome = os.path.splitext(os.path.basename(video_path))[0]

    output_path = os.path.join(
        RESULTADOS_DIR,
        f"{nome}_mediapipe.mp4"
    )

    cap = cv2.VideoCapture(video_path)

    largura = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    altura = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps_original = cap.get(cv2.CAP_PROP_FPS)

    writer = cv2.VideoWriter(
        output_path,
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps_original,
        (largura, altura)
    )

    total_frames = 0
    tempos = []

    with mp_pose.Pose(
        static_image_mode=False,
        model_complexity=1,
        enable_segmentation=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5
    ) as pose:

        while True:

            ret, frame = cap.read()

            if not ret:
                break

            inicio = time.time()

            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

            resultado = pose.process(rgb)

            fim = time.time()

            tempos.append(fim - inicio)

            if resultado.pose_landmarks:

                mp_drawing.draw_landmarks(
                    frame,
                    resultado.pose_landmarks,
                    mp_pose.POSE_CONNECTIONS
                )

            fps_atual = 1 / (tempos[-1] + 1e-9)

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

    cap.release()
    writer.release()

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

    model = YOLO(YOLO_MODEL)

    nome = os.path.splitext(
        os.path.basename(video_path)
    )[0]

    output_path = os.path.join(
        RESULTADOS_DIR,
        f"{nome}_yolo.mp4"
    )

    cap = cv2.VideoCapture(video_path)

    largura = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    altura = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps_original = cap.get(cv2.CAP_PROP_FPS)

    writer = cv2.VideoWriter(
        output_path,
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps_original,
        (largura, altura)
    )

    total_frames = 0
    tempos = []

    while True:

        ret, frame = cap.read()

        if not ret:
            break

        inicio = time.time()

        results = model(frame, verbose=False)

        annotated = results[0].plot()

        fim = time.time()

        tempos.append(fim - inicio)

        fps_atual = 1 / (tempos[-1] + 1e-9)

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

    cap.release()
    writer.release()

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

    videos = [
        f for f in os.listdir(VIDEOS_DIR)
        if f.lower().endswith(
            (".mp4", ".avi", ".mov", ".mkv")
        )
    ]

    if not videos:
        print("Não encontrei vídeos na pasta videos/")
        return

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

    for r in resultados:
        print(r)

    print(
        f"\nResultados guardados em: "
        f"{RESULTADOS_DIR}/"
    )


if __name__ == "__main__":
    main()