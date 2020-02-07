import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import ratpack.http.client.ReceivedResponse;
import ratpack.test.MainClassApplicationUnderTest;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class MainTest {

    private MainClassApplicationUnderTest appUnderTest;

    @Before
    public void setUp() {
        appUnderTest = new MainClassApplicationUnderTest(Main.class);
    }

    @Test
    public void testUtc() throws Exception {
        var response = appUnderTest.getHttpClient().get("/api/1/utc?now=2007-08-31T00:00:00Z");
        assertThat(response.getHeaders().get("Content-Type"), is(equalTo("image/png")));
        assertThat(response.getHeaders().get("Cache-Control"), is(equalTo("no-cache")));

        try (var body = response.getBody().getInputStream()) {
            var image = ImageIO.read(body);
            assertThat(image.getWidth(), is(1));
            assertThat(image.getHeight(), is(1));
            assertThat(readPixelValue(image, 0, 0), is(1188518400L));
        }
    }

    @Test
    public void testLocal() throws Exception {
        var response = appUnderTest.getHttpClient().get("/api/1/local?now=2018-09-20T00:00:00Z&tz=Australia/Sydney");
        assertThat(response.getHeaders().get("Content-Type"), is(equalTo("image/png")));
        assertThat(response.getHeaders().get("Cache-Control"), is(equalTo("no-cache")));

        try (var body = response.getBody().getInputStream()) {
            var image = ImageIO.read(body);
            assertThat(image.getWidth(), is(4));
            assertThat(image.getHeight(), is(1));
            assertThat(readPixelValue(image, 0, 0), is(1537401600L));
            assertThat(readPixelValue(image, 1, 0), is(36000L));
            assertThat(readPixelValue(image, 2, 0), is(1538841600L));
            assertThat(readPixelValue(image, 3, 0), is(39600L));
        }
    }

    @Test
    public void testUtcWithSize() throws Exception {
        var response = appUnderTest.getHttpClient().get("/api/1/utc?now=2007-08-31T00:00:00Z&size=4");
        assertThat(response.getHeaders().get("Content-Type"), is(equalTo("image/png")));
        assertThat(response.getHeaders().get("Cache-Control"), is(equalTo("no-cache")));

        try (var body = response.getBody().getInputStream()) {
            var image = ImageIO.read(body);
            assertThat(image.getWidth(), is(4));
            assertThat(image.getHeight(), is(4));
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    assertThat(readPixelValue(image, x, y), is(1188518400L));
                }
            }
        }
    }

    @Test
    public void testLocalWithSize() throws Exception {
        var response = appUnderTest.getHttpClient().get("/api/1/local?now=2018-09-20T00:00:00Z&tz=Australia/Sydney&size=4");
        assertThat(response.getHeaders().get("Content-Type"), is(equalTo("image/png")));
        assertThat(response.getHeaders().get("Cache-Control"), is(equalTo("no-cache")));

        try (var body = response.getBody().getInputStream()) {
            var image = ImageIO.read(body);
            assertThat(image.getWidth(), is(16));
            assertThat(image.getHeight(), is(4));
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    assertThat(readPixelValue(image, x, y), is(1537401600L));
                }
            }
            for (int x = 4; x < 8; x++) {
                for (int y = 0; y < 4; y++) {
                    assertThat(readPixelValue(image, x, y), is(36000L));
                }
            }
            for (int x = 8; x < 12; x++) {
                for (int y = 0; y < 4; y++) {
                    assertThat(readPixelValue(image, x, y), is(1538841600L));
                }
            }
            for (int x = 12; x < 16; x++) {
                for (int y = 0; y < 4; y++) {
                    assertThat(readPixelValue(image, x, y), is(39600L));
                }
            }
        }
    }

    private long readPixelValue(BufferedImage image, int x, int y) {
        int pixel = image.getRGB(x, y);

        // ARGB -> RGBA
        return (pixel << 8) | ((pixel >> 24) & 0xFF);
    }
}
