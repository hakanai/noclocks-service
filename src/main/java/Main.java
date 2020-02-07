import com.google.common.primitives.Ints;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import ratpack.handling.Context;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.LongStream;

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
                    Instant now = getNow(context);
                    long[] values = { getUnixTime(now) };
                    sendValuesAsImage(context, values);
                });

                chain.get("api/1/local", context -> {

                    // TODO: Get IP from X-Forwarded-For -> time zone ?

                    Instant now = getNow(context);
                    String zoneParameter = context.getRequest().getQueryParams().get("tz");
                    ZoneId zoneId = zoneParameter == null ? ZoneOffset.UTC : ZoneId.of(zoneParameter);

                    long[] values = new long[4];
                    values[0] = getUnixTime(now);
                    ZoneRules zoneRules = zoneId.getRules();
                    values[1] = zoneRules.getOffset(now).getTotalSeconds();
                    ZoneOffsetTransition transition = zoneRules.nextTransition(now);
                    if (transition != null) {
                        values[2] = getUnixTime(transition.getInstant());
                        values[3] = transition.getOffsetAfter().getTotalSeconds();
                    }
                    sendValuesAsImage(context, values);
                });
            });
        });
    }

    private static Instant getNow(Context context) {
        return getNowFromQueryParam(context)
                .or(() -> getNowFromRequestHeader(context))
                .orElseGet(Instant::now);
    }

    private static int getSize(Context context) {
        return Optional
                .ofNullable(context.getRequest().getQueryParams().get("size"))
                .map(Integer::parseInt)
                .map(i -> Ints.constrainToRange(i, 1, 128))
                .orElse(1);
    }

    private static Optional<Instant> getNowFromQueryParam(Context context) {
        return Optional
                .ofNullable(context.getRequest().getQueryParams().get("now"))
                .map(Instant::parse);
    }

    private static Optional<Instant> getNowFromRequestHeader(Context context) {
        // X-Request-Start is used by Heroku but possibly others.
        return Optional
                .ofNullable(context.getRequest().getHeaders().get("X-Request-Start"))
                .map(Long::parseLong)
                .map(Instant::ofEpochMilli);
    }

    private static long getUnixTime(Instant instant) {
        long unixTime = instant.getLong(ChronoField.INSTANT_SECONDS);
        if (unixTime < 0 || unixTime > MAX_UNSIGNED_INT) {
            throw new IllegalStateException("Application is now broken, unixTime = " + unixTime);
        }
        return unixTime;
    }

    private static void sendValuesAsImage(Context context, long[] values) throws IOException {
        int size = getSize(context);
        int[] pixelValues = LongStream.of(values).mapToInt(Main::pixelValueFor).toArray();
        int[] array = new int[size * size * values.length];
        int stride = size * values.length;
        for (int i = 0; i < values.length; i++) {
            for (int y = 0; y < size; y++) {
                Arrays.fill(array,
                        y * stride + i * size,
                        y * stride + (i + 1) * size,
                        pixelValues[i]);
            }
        }

        // TODO: Can we reuse the image?
        BufferedImage image = new BufferedImage(stride, size, BufferedImage.TYPE_INT_ARGB);
        WritableRaster raster = image.getRaster();
        raster.setDataElements(0, 0, stride, size, array);

        sendImage(context, image);
    }

    private static int pixelValueFor(long value) {
        // RGBA (Unity-friendly) -> ARGB (Java-friendly)
        return (int) ((value << 24) | ((value >> 8) & 0xFFFFFF));
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