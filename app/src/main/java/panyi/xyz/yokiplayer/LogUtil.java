package panyi.xyz.yokiplayer;

import android.util.Log;

public class LogUtil {
    public static void log(final String msg){
        System.out.println(msg);
        Log.i("LogUtil", msg);
    }
}
