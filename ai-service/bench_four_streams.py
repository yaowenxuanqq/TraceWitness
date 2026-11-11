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
    print("streams=%d frames=20 width=1920" % STREAMS)
    print("fps=%s" % ",".join("%.2f" % rate for rate in rates))
    print("min=%.2f p50=%.2f p95=%.2f target_min=%s" % (
        minimum, percentile(rates, 0.50), percentile(rates, 0.95), TARGET_MIN_FPS))
    return 0 if minimum >= TARGET_MIN_FPS else 1


if __name__ == "__main__":
    raise SystemExit(main())
