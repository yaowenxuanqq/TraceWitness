"""ONNX detection and a short-window tracker.

Business timestamps are supplied by the caller. This module does not read the wall clock.
The ONNX graph is a synthetic luminance detector, not a trained weight file.
"""

import threading
import time
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path

import numpy as np
import onnxruntime as ort

MODEL_PATH = Path(__file__).with_name("synthetic_detect.onnx")
DETECT_WIDTH = 640
TRACK_WINDOW = 5
SAMPLE_FRAMES = 8
THRESHOLD = 0.75

_session = None
_session_lock = threading.Lock()


class InferenceError(Exception):
    pass


def get_session():
    global _session
    if _session is None:
        with _session_lock:
            if _session is None:
                _session = ort.InferenceSession(str(MODEL_PATH), providers=["CPUExecutionProvider"])
    return _session


def quantize(value):
    return float(Decimal(value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def sample_frame(index, width=1920, height=1080):
    image = np.zeros((height, width, 3), dtype=np.uint8)
    box_w = 180
    box_h = 360
    x0 = 240 + index * 90
    y0 = 360
    image[y0 : y0 + box_h, x0 : x0 + box_w] = 255
    return image


def downsample_width(frame, width=DETECT_WIDTH):
    height, source_w = frame.shape[:2]
    if source_w == width:
        return frame
    new_h = max(1, int(round(height * float(width) / float(source_w))))
    ys = np.linspace(0, height - 1, new_h).astype(np.int32)
    xs = np.linspace(0, source_w - 1, width).astype(np.int32)
    return frame[ys][:, xs]


def to_nchw(frame):
    image = frame.astype(np.float32) / 255.0
    return np.transpose(image, (2, 0, 1))[np.newaxis, ...]


def run_luma(session, tensor):
    try:
        outputs = session.run(None, {"images": np.ascontiguousarray(tensor, dtype=np.float32)})
    except Exception as exc:
        raise InferenceError(str(exc)) from exc
    return outputs[0]


def boxes_from_luma(luma, threshold=THRESHOLD):
    mask = luma[0, 0] >= threshold
    ys, xs = np.nonzero(mask)
    if xs.size == 0:
        return []
    x0 = int(xs.min())
    x1 = int(xs.max())
    y0 = int(ys.min())
    y1 = int(ys.max())
    height, width = mask.shape
    confidence = Decimal(str(float(luma[0, 0, y0 : y1 + 1, x0 : x1 + 1].mean())))
    return [
        {
            "x": quantize(Decimal(x0) / Decimal(width)),
            "y": quantize(Decimal(y0) / Decimal(height)),
            "w": quantize(Decimal(x1 - x0 + 1) / Decimal(width)),
            "h": quantize(Decimal(y1 - y0 + 1) / Decimal(height)),
            "confidence": quantize(confidence),
        }
    ]


def iou(a, b):
    ax2 = a["x"] + a["w"]
    ay2 = a["y"] + a["h"]
    bx2 = b["x"] + b["w"]
    by2 = b["y"] + b["h"]
    ix1 = max(a["x"], b["x"])
    iy1 = max(a["y"], b["y"])
    ix2 = min(ax2, bx2)
    iy2 = min(ay2, by2)
    inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
    union = a["w"] * a["h"] + b["w"] * b["h"] - inter
    if union <= 0:
        return 0.0
    return inter / union


class Tracker:
    def __init__(self, window=TRACK_WINDOW):
        self.window = window
        self.tracks = []
        self._seq = 0

    def update(self, frame_index, tick, boxes):
        used = set()
        for box in boxes:
            track = self._match(frame_index, box, used)
            if track is None:
                self._seq += 1
                track = {"local_id": f"track-{self._seq}", "points": []}
                self.tracks.append(track)
            point = dict(box)
            point["frame_index"] = frame_index
            point["tick"] = tick
            track["points"].append(point)
            used.add(track["local_id"])

    def _match(self, frame_index, box, used):
        best = None
        best_score = 0.0
        for track in self.tracks:
            if track["local_id"] in used or not track["points"]:
                continue
            last = track["points"][-1]
            if frame_index - last["frame_index"] > self.window:
                continue
            score = iou(last, box)
            if score > best_score:
                best_score = score
                best = track
        if best is not None and best_score >= 0.05:
            return best
        return None


def analyze_prepared(tensors, session=None):
    session = session or get_session()
    tracker = Tracker()
    faults = []
    for index, tensor in enumerate(tensors):
        try:
            boxes = boxes_from_luma(run_luma(session, tensor))
        except InferenceError as exc:
            faults.append(
                {
                    "frame_index": index,
                    "tick": index,
                    "fault_type": "inference_error",
                    "detail": str(exc)[:500],
                }
            )
            continue
        tracker.update(index, index, boxes)
    return {
        "tracks": tracker.tracks,
        "faults": faults,
        "detector": "synthetic-onnx",
        "input_width": DETECT_WIDTH,
        "frames": len(tensors),
    }


def analyze_frames(frames, session=None):
    tensors = []
    for frame in frames:
        small = downsample_width(frame, DETECT_WIDTH)
        tensors.append(to_nchw(small))
    return analyze_prepared(tensors, session)


def analyze_sample(frames=SAMPLE_FRAMES, width=1920, height=1080):
    if frames < 2 or frames > 30:
        raise ValueError("frames out of range")
    return analyze_frames([sample_frame(index, width, height) for index in range(frames)])


def measure_detect_fps_with_session(session, frames=20, width=1920, height=1080):
    tensors = []
    for index in range(frames):
        small = downsample_width(sample_frame(index, width, height), DETECT_WIDTH)
        tensors.append(to_nchw(small))
    run_luma(session, tensors[0])
    boxes_from_luma(run_luma(session, tensors[0]))
    started = time.perf_counter()
    for tensor in tensors:
        boxes_from_luma(run_luma(session, tensor))
    elapsed = time.perf_counter() - started
    if elapsed <= 0:
        return 0.0
    return frames / elapsed


def measure_detect_fps(frames=20, width=1920, height=1080):
    return measure_detect_fps_with_session(get_session(), frames, width, height)
