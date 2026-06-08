import os
import time
import csv
import cv2

from mmpose.apis import MMPoseInferencer

VIDEOS_DIR = "videos"
RESULTADOS_DIR = "resultados"

os.makedirs(RESULTADOS_DIR, exist_ok=True)


MODELOS = [
    {
        "nome": "RTMPose",
        "pose2d": "rtmpose-m"
    },
    {
        "nome": "ViTPose",
        "pose2d": "vitpose"
    }
]


def contar_frames(video_path):
    cap = cv2.VideoCapture(video_path)
    frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    cap.release()
    return frames


def processar_mmpose(video_path, nome_modelo, pose2d):

    nome_video = os.path.splitext(os.path.basename(video_path))[0]

    output_dir = os.path.join(
        RESULTADOS_DIR,
        f"{nome_video}_{nome_modelo}"
    )

    os.makedirs(output_dir, exist_ok=True)

    inferencer = MMPoseInferencer(
        pose2d=pose2d
    )

    total_frames = contar_frames(video_path)

    inicio = time.time()

    result_generator = inferencer(
        video_path,
        show=False,
        vis_out_dir=output_dir,
        pred_out_dir=output_dir
    )

    for _ in result_generator:
        pass

    fim = time.time()

    tempo_total = fim - inicio

    fps_medio = total_frames / tempo_total if tempo_total > 0 else 0

    return {
        "video": nome_video,
        "modelo": nome_modelo,
        "frames": total_frames,
        "fps_medio": round(fps_medio, 2),
        "ficheiro_saida": output_dir
    }


def main():

    resultados = []

    videos = [
        f for f in os.listdir(VIDEOS_DIR)
        if f.lower().endswith((".mp4", ".avi", ".mov", ".mkv"))
    ]

    if not videos:
        print("Não encontrei vídeos na pasta videos/")
        return

    for video in videos:

        video_path = os.path.join(VIDEOS_DIR, video)

        for modelo in MODELOS:

            print(
                f"\nA processar {video} com {modelo['nome']}..."
            )

            resultado = processar_mmpose(
                video_path,
                modelo["nome"],
                modelo["pose2d"]
            )

            resultados.append(resultado)

    csv_path = os.path.join(
        RESULTADOS_DIR,
        "comparacao_mmpose_resultados.csv"
    )

    with open(csv_path, "w", newline="", encoding="utf-8") as f:

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


if __name__ == "__main__":
    main()