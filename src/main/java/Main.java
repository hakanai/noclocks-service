import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import ratpack.handling.Context;
import ratpack.render.Renderable;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Arrays;

public class Main {
    private static final long MAX_UNSIGNED_INT = 0xFFFFFFFFL;

    public static void main(String... args) throws Exception {
        RatpackServer.start(server -> {
            server.serverConfig(config -> {
                config.baseDir(BaseDir.find());
                config.env();
            });

            server.handlers(chain -> {

                chain.get("api/1/utc", context -> {

                    String nowParameter = context.getRequest().getQueryParams().get("now");
                    Instant now = nowParameter == null ? Instant.now() : Instant.parse(nowParameter);

                    // TODO: Code obviously breaks in 2037.
                    long unixTime = now.getLong(ChronoField.INSTANT_SECONDS);
                    if (unixTime < 0 || unixTime > MAX_UNSIGNED_INT) {
                        throw new IllegalStateException("Application is now broken, unixTime = " + unixTime);
                    }

                    // RGBA -> ARGB
                    int pixel = (int) ((unixTime << 24) | ((unixTime >> 8) & 0xFFFFFF));

                    // TODO: Can we reuse the image?
                    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                    image.setRGB(0, 0, pixel);

                    ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
                    boolean success = false;
                    try {

                        // TODO: Could avoid using Image I/O and write directly into a buffer ourselves. Maybe in BMP format?
                        try (ByteBufOutputStream outputStream = new ByteBufOutputStream(buffer)) {
                            if (!ImageIO.write(image, "PNG", outputStream)) {
                                throw new IllegalStateException("No PNG writer in JRE!");
                            }
                        }

                        context.getResponse().contentType("image/png");
                        context.getResponse().noCompress();
                        // TODO: Could possibly ETag it with the value we used.
                        context.getResponse().getHeaders().add("Cache-Control", "no-cache");
                        context.getResponse().send(buffer);

                        success = true;
                    } finally {
                        if (!success) {
                            buffer.release();
                        }
                    }
                });

            });
        });
  }
}