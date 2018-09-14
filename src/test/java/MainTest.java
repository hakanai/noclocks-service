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
    public void test() throws Exception {
        ReceivedResponse response = appUnderTest.getHttpClient().get("/api/1/utc?now=2007-08-31T00:00:00Z");
        assertThat(response.getHeaders().get("Content-Type"), is(equalTo("image/png")));
        assertThat(response.getHeaders().get("Cache-Control"), is(equalTo("no-cache")));

        try (InputStream body = response.getBody().getInputStream()) {
            BufferedImage image = ImageIO.read(body);
            assertThat(image.getWidth(), is(1));
            assertThat(image.getHeight(), is(1));

            int pixel = image.getRGB(0, 0);

            // ARGB -> RGBA
            long unixTime = (pixel << 8) | ((pixel >> 24) & 0xFF);

            assertThat(unixTime, is(1188518400L));
        }
    }
}
