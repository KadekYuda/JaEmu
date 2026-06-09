package javax.microedition.media.control;

import javax.microedition.media.Control;

public interface TempoControl extends RateControl {
    int getTempo();
    int setTempo(int millitempo);
}
