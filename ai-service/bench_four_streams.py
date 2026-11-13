"""Time four 1080p streams at once. Each stream uses its own ONNX session."""

from concurrent.futures import ThreadPoolExecutor

import onnxruntime as ort

from app.vision import MODEL_PATH, measure_detect_fps_with_session

STREAMS = 4
TARGET_MIN_FPS = 2


def percentile(values, pct):
    ordered = sorted(values)
    rank = (len(ordered) - 1) * pct
    low = int(rank)
    high = min(low + 1, len(ordered) - 1)
    weight = rank - low
    return ordered[low] * (1 - weight) + ordered[high] * weight


def one_stream(_index):
    session = ort.InferenceSession(str(MODEL_PATH), providers=["CPUExecutionProvider"])
    return measure_detect_fps_with_session(session)


def main():
    with ThreadPoolExecutor(max_workers=STREAMS) as pool:
        rates = list(pool.map(one_stream, range(STREAMS)))
    minimum = min(rates)
    print(f"streams={STREAMS} frames=20 width=1920")
    rates_text = ",".join(f"{rate:.2f}" for rate in rates)
    print(f"fps={rates_text}")
    print(
        f"min={minimum:.2f} p50={percentile(rates, 0.50):.2f} "
        f"p95={percentile(rates, 0.95):.2f} target_min={TARGET_MIN_FPS}"
    )
    return 0 if minimum >= TARGET_MIN_FPS else 1


if __name__ == "__main__":
    raise SystemExit(main())
