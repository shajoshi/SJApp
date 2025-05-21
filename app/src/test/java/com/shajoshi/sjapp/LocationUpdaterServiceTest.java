package com.shajoshi.sjapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocationUpdaterServiceTest {
    @Test
    public void setDelay_handlesValidValues() {
        assertEquals(10, LocationUpdaterService.setDelay(10));
        assertEquals(10000, LocationUpdaterService.POLL_INTERVAL); 
    }

    @Test
    public void setDelay_handlesBelowMin() {
        assertEquals(1, LocationUpdaterService.setDelay(0));
        assertEquals(1000, LocationUpdaterService.POLL_INTERVAL);
        assertEquals(1, LocationUpdaterService.setDelay(-5));
        assertEquals(1000, LocationUpdaterService.POLL_INTERVAL);
    }

    @Test
    public void setDelay_handlesAboveMax() {
        assertEquals(120, LocationUpdaterService.setDelay(150));
        assertEquals(120000, LocationUpdaterService.POLL_INTERVAL);
        assertEquals(120, LocationUpdaterService.setDelay(121));
        assertEquals(120000, LocationUpdaterService.POLL_INTERVAL);
    }
}
