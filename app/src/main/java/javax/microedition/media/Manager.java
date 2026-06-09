package javax.microedition.media;

import java.io.IOException;
import java.io.InputStream;

public class Manager {
    public static Player createPlayer(InputStream is, String type) throws IOException, MediaException {
        if (is == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        return new AndroidPlayer(is, type);
    }

    public static Player createPlayer(String locator) throws IOException, MediaException {
        throw new MediaException("Locator type not supported");
    }

    public static String[] getSupportedContentTypes(String protocol) {
        return new String[] { "audio/midi", "audio/x-midi", "audio/wav", "audio/mp3", "audio/mpeg" };
    }

    public static String[] getSupportedProtocols(String contentType) {
        return new String[] { "device" };
    }
}
