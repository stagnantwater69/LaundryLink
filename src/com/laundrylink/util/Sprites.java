package com.laundrylink.util;

import com.laundrylink.model.Sex;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javafx.scene.image.Image;

/**
 * Pixel-art pictures for customers (by sex) and services (by name).
 * Loaded once from com/laundrylink/images.
 */
public final class Sprites {

    private static final String IMAGE_FOLDER = "/com/laundrylink/images/";
    private static final Map<String, Image> CACHE = new HashMap<>();

    private Sprites() {
    }

    /** Male or female avatar, or a neutral one if not set. */
    public static Image avatar(Sex sex) {
        if (sex == Sex.MALE) {
            return load("avatar-male.png");
        }
        if (sex == Sex.FEMALE) {
            return load("avatar-female.png");
        }
        return load("avatar-neutral.png");
    }

    /** Picks a service picture from words in its name, so new services still get a good one. */
    public static Image service(String serviceName) {
        String name = serviceName == null ? "" : serviceName.toLowerCase(Locale.ROOT);
        if (name.contains("iron") || name.contains("press")) {
            return load("service-iron.png");
        }
        if (name.contains("stain") || name.contains("spot")) {
            return load("service-stain.png");
        }
        if (name.contains("curtain") || name.contains("drape")) {
            return load("service-curtains.png");
        }
        if (name.contains("comforter") || name.contains("blanket") || name.contains("duvet")
                || name.contains("quilt") || name.contains("bedsheet") || name.contains("bed sheet")) {
            return load("service-comforter.png");
        }
        if (name.contains("dry clean") || name.contains("dry-clean") || name.contains("barong")
                || name.contains("gown") || name.contains("suit")) {
            return load("service-dryclean.png");
        }
        if (name.contains("fold")) {
            return load("service-fold.png");
        }
        if (name.contains("dry")) {
            return load("service-dry.png");
        }
        if (name.contains("wash")) {
            return load("service-wash.png");
        }
        return load("service-default.png");
    }

    private static Image load(String fileName) {
        return CACHE.computeIfAbsent(fileName,
                name -> new Image(Sprites.class.getResource(IMAGE_FOLDER + name).toExternalForm()));
    }
}
