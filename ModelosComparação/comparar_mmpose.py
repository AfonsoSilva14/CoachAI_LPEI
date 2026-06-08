import os
import time
import csv
import cv2

from mmpose.apis import MMPoseInferencer

# Pastas usadas para ler os videos de entrada e guardar os resultados.
VIDEOS_DIR = "videos"
RESULTADOS_DIR = "resultados"

# Garante que existe uma pasta onde guardar visualizacoes, predicoes e CSV.
os.makedirs(RESULTADOS_DIR, exist_ok=True)


# Lista dos modelos MMPose que vao ser testados em cada video.
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
    # Conta quantos frames existem no video para calcular o FPS medio depois.
    cap = cv2.VideoCapture(video_path)
    frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    cap.release()
    return frames


def processar_mmpose(video_path, nome_modelo, pose2d):
    # Executa um modelo MMPose num video e guarda os outputs na pasta de resultados.

    # Usa o nome do video para criar uma pasta propria para este modelo.
    nome_video = os.path.splitext(os.path.basename(video_path))[0]

    output_dir = os.path.join(
        RESULTADOS_DIR,
        f"{nome_video}_{nome_modelo}"
    )

    os.makedirs(output_dir, exist_ok=True)

    # Cria o inferencer da MMPose com a arquitetura escolhida.
    inferencer = MMPoseInferencer(
        pose2d=pose2d
    )

    # Conta os frames antes da inferencia para medir desempenho.
    total_frames = contar_frames(video_path)

    # Mede o tempo total necessario para processar o video completo.
    inicio = time.time()

    # O inferencer devolve um gerador; ao percorre-lo, a inferencia e executada.
    result_generator = inferencer(
        video_path,
        show=False,
        vis_out_dir=output_dir,
        pred_out_dir=output_dir
    )

    # Consome todos os resultados para garantir que o video inteiro foi processado.
    for _ in result_generator:
        pass

    fim = time.time()

    tempo_total = fim - inicio

    # FPS medio: quantidade de frames dividida pelo tempo total de processamento.
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

    # Procura na pasta videos todos os ficheiros com extensoes suportadas.
    videos = [
        f for f in os.listdir(VIDEOS_DIR)
        if f.lower().endswith((".mp4", ".avi", ".mov", ".mkv"))
    ]

    if not videos:
        print("Não encontrei vídeos na pasta videos/")
        return

    # Processa cada video com todos os modelos definidos na lista MODELOS.
    for video in videos:

        video_path = os.path.join(VIDEOS_DIR, video)

        for modelo in MODELOS:

            print(
                f"\nA processar {video} com {modelo['nome']}..."
            )

            # Guarda o resumo devolvido por cada execucao de modelo.
            resultado = processar_mmpose(
                video_path,
                modelo["nome"],
                modelo["pose2d"]
            )

            resultados.append(resultado)

    # Caminho do CSV final com os resultados comparativos.
    csv_path = os.path.join(
        RESULTADOS_DIR,
        "comparacao_mmpose_resultados.csv"
    )

    # Escreve uma linha por combinacao video/modelo.
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

    # Mostra no terminal os mesmos resultados que foram escritos no CSV.
    for r in resultados:
        print(r)


if __name__ == "__main__":
    main()
