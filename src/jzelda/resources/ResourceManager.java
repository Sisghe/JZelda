package jzelda.resources;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

/**
 * Singleton resource loader for images and map files. It keeps lightweight caches
 * and returns generated placeholders when image files are missing, which keeps
 * the game runnable in educational environments.
 */
public final class ResourceManager {
    private static ResourceManager instance;
    private final Path root;
    private final Map<String, Image> spriteCache = new ConcurrentHashMap<>();

    private ResourceManager(Path root) {
        this.root = root;
    }

    /**
     * Returns the singleton resource manager rooted at the current project.
     *
     * @return resource manager instance
     */
    public static synchronized ResourceManager getInstance() {
        if (instance == null) {
            instance = new ResourceManager(Paths.get("resources"));
        }
        return instance;
    }

    /**
     * Loads an image from {@code resources/images}. If no matching PNG exists, a
     * generated placeholder is returned.
     *
     * @param key logical image key without extension
     * @return loaded or generated image
     */
    public Image getSprite(String key) {
        return spriteCache.computeIfAbsent(key, this::loadSpriteOrFallback);
    }

    /**
     * Reads a map file from {@code resources/maps}.
     *
     * @param filename map file name
     * @return map lines
     */
    public List<String> loadMapLines(String filename) {
        Path file = root.resolve("maps").resolve(filename);
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> !line.startsWith(";"))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile caricare mappa " + file + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Lists map files using a stream sort pipeline.
     *
     * @return map file names sorted alphabetically
     */
    public List<String> listMapFiles() {
        Path maps = root.resolve("maps");
        try (Stream<Path> stream = Files.list(maps)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".map"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> path.getFileName().toString()).collect(Collectors.toList());
        } catch (IOException ex) {
            return List.of();
        }
    }

    /**
     * Resolves a resource path relative to the resources folder.
     *
     * @param first first path component
     * @param more additional path components
     * @return resolved path
     */
    public Path resolve(String first, String... more) {
        return root.resolve(Paths.get(first, more));
    }

    private Image loadSpriteOrFallback(String key) {
        Path file = root.resolve("images").resolve(key + ".png");
        if (Files.exists(file)) {
            try {
                return ImageIO.read(file.toFile());
            } catch (IOException ex) {
                System.err.println("Impossibile caricare immagine " + file + ": " + ex.getMessage());
            }
        }
        return createFallbackSprite(key);
    }

    private Image createFallbackSprite(String key) {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        int hash = Math.abs(key.hashCode());
        g.setColor(new Color(80 + hash % 120, 60 + (hash / 3) % 120, 50 + (hash / 7) % 120));
        g.fillRect(2, 2, 20, 20);
        g.setColor(Color.BLACK);
        g.drawRect(2, 2, 20, 20);
        g.dispose();
        return image;
    }
}
