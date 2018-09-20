import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import ratpack.handling.Context;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;

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

                    // TODO: Can we reuse the image?
                    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                    setPixelValue(image, 0, 0, getUnixTime(now));
                    sendImage(context, image);
                });

                chain.get("api/1/local", context -> {

                    String nowParameter = context.getRequest().getQueryParams().get("now");
                    Instant now = nowParameter == null ? Instant.now() : Instant.parse(nowParameter);
                    String zoneParameter = context.getRequest().getQueryParams().get("tz");
                    ZoneId zoneId = zoneParameter == null ? ZoneOffset.UTC : ZoneId.of(zoneParameter);

                    BufferedImage image = new BufferedImage(4, 1, BufferedImage.TYPE_INT_ARGB);
                    setPixelValue(image, 0, 0, getUnixTime(now));
                    ZoneRules zoneRules = zoneId.getRules();
                    setPixelValue(image, 1, 0, zoneRules.getOffset(now).getTotalSeconds());
                    ZoneOffsetTransition transition = zoneRules.nextTransition(now);
                    if (transition != null) {
                        setPixelValue(image, 2, 0, getUnixTime(transition.getInstant()));
                        setPixelValue(image, 3, 0, transition.getOffsetAfter().getTotalSeconds());
                    }
                    sendImage(context, image);
                });
            });
        });
    }

    private static long getUnixTime(Instant instant) {
        long unixTime = instant.getLong(ChronoField.INSTANT_SECONDS);
        if (unixTime < 0 || unixTime > MAX_UNSIGNED_INT) {
            throw new IllegalStateException("Application is now broken, unixTime = " + unixTime);
        }
        return unixTime;
    }

    private static void setPixelValue(BufferedImage image, int x, int y, long value) {
        // RGBA -> ARGB
        int pixel = (int) ((value << 24) | ((value >> 8) & 0xFFFFFF));

        image.setRGB(x, y, pixel);
    }

    private static void sendImage(Context context, BufferedImage image) throws IOException {
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
    }
}