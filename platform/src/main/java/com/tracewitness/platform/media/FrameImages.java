package com.tracewitness.platform.media;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public final class FrameImages {
    private FrameImages() {
    }

    public static void write(long tick, String profile, int index, Path file) throws IOException {
        BufferedImage image = new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor("HIGH".equals(profile) ? new Color(226, 168, 75) : new Color(48, 92, 72));
            graphics.fillRect(0, 0, 160, 90);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            graphics.drawString(profile + " t" + tick + " f" + index, 8, 50);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("png writer missing");
        }
    }
}
