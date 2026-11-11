package com.tracewitness.platform.vision;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public final class Thumbnails {
    private Thumbnails() {
    }

    public static void write(String mode, String frameKind, int frame, Path file) throws IOException {
        BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(background(mode));
            graphics.fillRect(0, 0, 96, 96);
            if ("crop".equals(frameKind)) {
                graphics.setColor(new Color(232, 242, 236));
                graphics.fillRect(28, 18, 40, 52);
            }
            graphics.setColor(Color.BLACK);
            graphics.fillRect(28, 18, 40, 16);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            graphics.drawString(mode, 4, 78);
            graphics.drawString(frameKind + " f" + frame, 4, 92);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("png writer missing");
        }
    }

    private static Color background(String mode) {
        if ("gate_failure".equals(mode)) {
            return new Color(226, 168, 75);
        }
        if ("track_break".equals(mode) || "inference_error".equals(mode)) {
            return new Color(122, 72, 62);
        }
        return new Color(48, 92, 72);
    }
}
