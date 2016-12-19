package android.os;

import java.io.IOException;
import android.os.ServiceManager;
/**
 * Class that provides access to some of the power management functions.
 *
 * {@hide}
 */
public class Power
{
    // can't instantiate this class
    private Power()
    {
    }
    /**
     * Wake lock that ensures that the CPU is running.  The screen might
     * not be on.
     */
    public static final int PARTIAL_WAKE_LOCK = 1;
    /**
     * Wake lock that ensures that the screen is on.
     */
    public static final int FULL_WAKE_LOCK = 2;
    public static native void acquireWakeLock(int lock, String id);
    public static native void releaseWakeLock(String id);
    /**
     * Brightness value for fully off
     */
    public static final int BRIGHTNESS_OFF = 0;
    /**
     * Brightness value for dim backlight
     */
    public static final int BRIGHTNESS_DIM = 20;
    /**
     * Brightness value for fully on
     */
    public static final int BRIGHTNESS_ON = 255;
    /**
     * Brightness value to use when battery is low
     */
    public static final int BRIGHTNESS_LOW_BATTERY = 10;
    /**
     * Threshold for BRIGHTNESS_LOW_BATTERY (percentage)
     * Screen will stay dim if battery level is <= LOW_BATTERY_THRESHOLD
     */
    public static final int LOW_BATTERY_THRESHOLD = 10;
    /**
     * Turn the screen on or off
     *
     * @param on Whether you want the screen on or off
     */
    public static native int setScreenState(boolean on);
    public static native int setLastUserActivityTimeout(long ms);
    /**
     * Low-level function turn the device off immediately, without trying
     * to be clean.  Most people should use
     * {@link android.internal.app.ShutdownThread} for a clean shutdown.
     *
     * @deprecated
     * @hide
     */
    @Deprecated
    public static native void shutdown();
    /**
     * Reboot the device.
     * @param reason code to pass to the kernel (e.g. "recovery"), or null.
     *
     * @throws IOException if reboot fails for some reason (eg, lack of
     *         permission)
     */
    public static void reboot(String reason) throws IOException
    {
        rebootNative(reason);
    }
    private static native void rebootNative(String reason) throws IOException ;
    public static native int powerInitNative();
}