import os
import time
import cv2
import numpy as np
import mediapipe as mp
import tensorflow as tf

VIDEO_PATH = "videos/polichinelo_adaptado_p1.mp4"
MOVENET_MODEL_PATH = "movenet_lightning.tflite"

OUTPUT_DIR = "outputs"
os.makedirs(OUTPUT_DIR, exist_ok=True)

CONF_THRESHOLD = 0.3


# =========================
# UTILS
# =========================

def draw_text(frame, model_name, fps, detection_rate):
    cv2.rectangle(frame, (10, 10), (430, 90), (0, 0, 0), -1)
    cv2.putText(frame, f"Modelo: {model_name}", (20, 35),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
    cv2.putText(frame, f"FPS Medio: {fps:.2f}", (20, 60),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
    cv2.putText(frame, f"Detection Rate: {detection_rate:.2f}%", (20, 85),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)


def create_writer(cap, output_path):
    fps_video = cap.get(cv2.CAP_PROP_FPS)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    return cv2.VideoWriter(output_path, fourcc, fps_video, (width, height))


# =========================
# MEDIAPIPE
# =========================

def run_mediapipe():
    from mediapipe.tasks import python
    from mediapipe.tasks.python import vision

    MODEL_PATH = "pose_landmarker_heavy.task"

    cap = cv2.VideoCapture(VIDEO_PATH)
    writer = create_writer(cap, os.path.join(OUTPUT_DIR, "resultado_mediapipe.mp4"))

    base_options = python.BaseOptions(model_asset_path=MODEL_PATH)
    options = vision.PoseLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.IMAGE,
        num_poses=1
    )

    detector = vision.PoseLandmarker.create_from_options(options)

    connections = [
        (11, 12),
        (11, 13), (13, 15),
        (12, 14), (14, 16),
        (11, 23), (12, 24),
        (23, 24),
        (23, 25), (25, 27),
        (24, 26), (26, 28),
        (15, 17), (16, 18),
        (27, 29), (28, 30)
    ]

    total_frames = 0
    detected_frames = 0
    processed_frames = []

    start = time.time()

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        total_frames += 1
        h, w, _ = frame.shape

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)

        result = detector.detect(mp_image)

        if result.pose_landmarks:
            landmarks = result.pose_landmarks[0]

            visible_points = sum(
                1 for lm in landmarks
                if lm.visibility > CONF_THRESHOLD
            )

            if visible_points >= 14:
                detected_frames += 1

            for a, b in connections:
                if (
                    landmarks[a].visibility > CONF_THRESHOLD and
                    landmarks[b].visibility > CONF_THRESHOLD
                ):
                    x1, y1 = int(landmarks[a].x * w), int(landmarks[a].y * h)
                    x2, y2 = int(landmarks[b].x * w), int(landmarks[b].y * h)
                    cv2.line(frame, (x1, y1), (x2, y2), (0, 255, 0), 2)

            for lm in landmarks:
                if lm.visibility > CONF_THRESHOLD:
                    x, y = int(lm.x * w), int(lm.y * h)
                    cv2.circle(frame, (x, y), 4, (0, 255, 255), -1)

        processed_frames.append(frame.copy())

    end = time.time()

    avg_fps = total_frames / (end - start) if end > start else 0
    detection_rate = (detected_frames / total_frames) * 100 if total_frames > 0 else 0

    for frame in processed_frames:
        draw_text(frame, "MediaPipe Heavy", avg_fps, detection_rate)
        writer.write(frame)

    cap.release()
    writer.release()
    detector.close()

    print(f"MediaPipe -> FPS Medio: {avg_fps:.2f} | Detection Rate: {detection_rate:.2f}%")


# =========================
# MOVENET
# =========================

def load_movenet():
    interpreter = tf.lite.Interpreter(model_path=MOVENET_MODEL_PATH)
    interpreter.allocate_tensors()
    return interpreter


def run_movenet():
    cap = cv2.VideoCapture(VIDEO_PATH)
    writer = create_writer(cap, os.path.join(OUTPUT_DIR, "resultado_movenet.mp4"))

    interpreter = load_movenet()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    input_shape = input_details[0]["shape"]
    input_height = input_shape[1]
    input_width = input_shape[2]
    input_dtype = input_details[0]["dtype"]

    connections = [
        (5, 6),
        (5, 7), (7, 9),
        (6, 8), (8, 10),
        (5, 11), (6, 12),
        (11, 12),
        (11, 13), (13, 15),
        (12, 14), (14, 16)
    ]

    total_frames = 0
    detected_frames = 0
    processed_frames = []

    start = time.time()

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        total_frames += 1
        h, w, _ = frame.shape

        img = cv2.resize(frame, (input_width, input_height))
        img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

        if input_dtype == np.float32:
            input_data = np.expand_dims(img_rgb.astype(np.float32), axis=0)
        else:
            input_data = np.expand_dims(img_rgb.astype(np.int32), axis=0)

        interpreter.set_tensor(input_details[0]["index"], input_data)
        interpreter.invoke()

        keypoints = interpreter.get_tensor(output_details[0]["index"])[0][0]

        valid_points = sum(1 for kp in keypoints if kp[2] > CONF_THRESHOLD)

        if valid_points >= 14:
            detected_frames += 1

        for a, b in connections:
            y1, x1, s1 = keypoints[a]
            y2, x2, s2 = keypoints[b]

            if s1 > CONF_THRESHOLD and s2 > CONF_THRESHOLD:
                cv2.line(
                    frame,
                    (int(x1 * w), int(y1 * h)),
                    (int(x2 * w), int(y2 * h)),
                    (255, 0, 255),
                    2
                )

        for y, x, score in keypoints:
            if score > CONF_THRESHOLD:
                cv2.circle(frame, (int(x * w), int(y * h)), 5, (255, 255, 0), -1)

        processed_frames.append(frame.copy())

    end = time.time()

    avg_fps = total_frames / (end - start) if end > start else 0
    detection_rate = (detected_frames / total_frames) * 100 if total_frames > 0 else 0

    for frame in processed_frames:
        draw_text(frame, "MoveNet Lightning", avg_fps, detection_rate)
        writer.write(frame)

    cap.release()
    writer.release()

    print(f"MoveNet -> FPS Medio: {avg_fps:.2f} | Detection Rate: {detection_rate:.2f}%")


# =========================
# RF-DETR KEYPOINTS
# =========================

def extract_rfdetr_keypoints(prediction):
    """
    Tenta extrair keypoints do output do RF-DETR.
    Como a API ainda é preview, pode variar conforme a versão.
    """

    if hasattr(prediction, "keypoints"):
        return prediction.keypoints

    if isinstance(prediction, list) and len(prediction) > 0:
        first = prediction[0]
        if hasattr(first, "keypoints"):
            return first.keypoints

    if hasattr(prediction, "xy"):
        return prediction.xy

    return None


def run_rfdetr():
    try:
        from rfdetr import RFDETRKeypointPreview
    except Exception as e:
        print("Erro ao importar RF-DETR:", e)
        print("Instala com: pip install rfdetr")
        return

    cap = cv2.VideoCapture(VIDEO_PATH)
    writer = create_writer(cap, os.path.join(OUTPUT_DIR, "resultado_rfdetr.mp4"))

    model = RFDETRKeypointPreview()

    total_frames = 0
    detected_frames = 0
    processed_frames = []

    start = time.time()

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        total_frames += 1
        h, w, _ = frame.shape

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

        try:
            prediction = model.predict(rgb, threshold=CONF_THRESHOLD)
            keypoints = extract_rfdetr_keypoints(prediction)

            if keypoints is not None:
                keypoints = np.array(keypoints)

                if keypoints.ndim >= 2:
                    points = keypoints.reshape(-1, keypoints.shape[-1])

                    valid_points = 0

                    for p in points:
                        if len(p) >= 3:
                            x, y, score = p[0], p[1], p[2]
                        elif len(p) >= 2:
                            x, y = p[0], p[1]
                            score = 1.0
                        else:
                            continue

                        if score > CONF_THRESHOLD:
                            valid_points += 1
                            cv2.circle(frame, (int(x), int(y)), 5, (0, 255, 255), -1)

                    if valid_points >= 14:
                        detected_frames += 1

        except Exception as e:
            cv2.putText(frame, "RF-DETR erro neste frame", (20, 130),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2)

        processed_frames.append(frame.copy())

    end = time.time()

    avg_fps = total_frames / (end - start) if end > start else 0
    detection_rate = (detected_frames / total_frames) * 100 if total_frames > 0 else 0

    for frame in processed_frames:
        draw_text(frame, "RF-DETR Keypoints", avg_fps, detection_rate)
        writer.write(frame)

    cap.release()
    writer.release()

    print(f"RF-DETR -> FPS Medio: {avg_fps:.2f} | Detection Rate: {detection_rate:.2f}%")


# =========================
# MAIN
# =========================

if __name__ == "__main__":
    print("A correr MediaPipe...")
    run_mediapipe()

    print("A correr MoveNet...")
    run_movenet()

    print("A correr RF-DETR Keypoints...")
    run_rfdetr()

    print("\nConcluido. Videos guardados na pasta outputs/")