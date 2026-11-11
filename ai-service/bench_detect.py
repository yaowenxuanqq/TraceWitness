"""Time detection after a 1080p frame has been reduced to 640 px wide."""

from app.vision import measure_detect_fps

TARGET_FPS = 5


def main():
    fps = measure_detect_fps()
    print("detect_fps_640=%.2f target=%s" % (fps, TARGET_FPS))
    return 0 if fps >= TARGET_FPS else 1


if __name__ == "__main__":
    raise SystemExit(main())
