package jzelda.resources;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

/**
 * Development utility that extracts the neutral runtime sprites from the
 * didactic source sheets. The game itself does not depend on the sheet names:
 * it keeps loading the generated files through {@link ResourceManager}.
 */
public final class SpriteSheetExtractor {
    private static final int FRAME_SIZE = 16;
    private static final int SOURCE_BACKGROUND_RGB = 0x008000;

    private SpriteSheetExtractor() {
        // Utility class: no instances.
    }

    /**
     * Regenerates the six animated entity frames under
     * {@code resources/images}.
     *
     * @param args optional first argument identifying the project resources
     *             directory; defaults to {@code resources}
     * @throws IOException if a sheet cannot be read or an output cannot be
     *                     written
     */
    public static void main(String[] args) throws IOException {
        Path resources = args.length == 0 ? Paths.get("resources") : Paths.get(args[0]);
        generateDefaultSprites(resources);
    }

    /**
     * Extracts the runtime frames from the two source sheets supplied for the
     * project. Green sheet pixels are converted to transparency.
     *
     * @param resources project resources directory
     * @throws IOException if reading or writing an image fails
     */
    public static void generateDefaultSprites(Path resources) throws IOException {
        Path imageRoot = resources.resolve("images");
        List<BufferedImage> sheets = readSourceSheets(imageRoot);
        BufferedImage players = sheets.get(0);
        BufferedImage enemies = sheets.get(sheets.size() - 1);

        writeFrame(players, 0, 11, imageRoot.resolve("player0.png"));
        writeFrame(players, 17, 11, imageRoot.resolve("player1.png"));
        writeFrame(enemies, 0, 100, imageRoot.resolve("enemy_patrol0.png"));
        writeFrame(enemies, 17, 100, imageRoot.resolve("enemy_patrol1.png"));
        writeFrame(enemies, 126, 100, imageRoot.resolve("enemy_shooter0.png"));
        writeFrame(enemies, 143, 100, imageRoot.resolve("enemy_shooter1.png"));
    }

    private static List<BufferedImage> readSourceSheets(Path imageRoot) throws IOException {
        List<Path> files;
        try (Stream<Path> paths = Files.walk(imageRoot, 2)) {
            files = paths.filter(Files::isRegularFile).filter(path -> !imageRoot.equals(path.getParent()))
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".png"))
                    .collect(Collectors.toList());
        }
        if (files.size() < 2) {
            throw new IOException("Servono due sprite sheet PNG nelle sottocartelle di " + imageRoot);
        }
        List<BufferedImage> sheets = new ArrayList<>();
        for (Path file : files) {
            sheets.add(readSheet(file));
        }
        sheets.sort(Comparator.comparingInt(BufferedImage::getWidth));
        return sheets;
    }

    private static BufferedImage readSheet(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new IOException("Formato immagine non supportato: " + file);
        }
        return image;
    }

    private static void writeFrame(BufferedImage sheet, int x, int y, Path output) throws IOException {
        if (x < 0 || y < 0 || x + FRAME_SIZE > sheet.getWidth() || y + FRAME_SIZE > sheet.getHeight()) {
            throw new IOException("Frame fuori dallo sprite sheet: " + output.getFileName());
        }
        BufferedImage frame = new BufferedImage(FRAME_SIZE, FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < FRAME_SIZE; row++) {
            for (int column = 0; column < FRAME_SIZE; column++) {
                int argb = sheet.getRGB(x + column, y + row);
                int rgb = argb & 0x00FFFFFF;
                frame.setRGB(column, row, rgb == SOURCE_BACKGROUND_RGB ? 0x00000000 : 0xFF000000 | rgb);
            }
        }
        if (!ImageIO.write(frame, "png", output.toFile())) {
            throw new IOException("Impossibile scrivere il frame PNG: " + output);
        }
    }
}
