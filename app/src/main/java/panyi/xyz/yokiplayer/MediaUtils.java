package panyi.xyz.yokiplayer;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;

/**
 *
 */
public class MediaUtils {
    private static final String TAG = "MediaUtils";

    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";

    private static final MediaCodecList sMCL = new MediaCodecList(MediaCodecList.REGULAR_CODECS);

    /**
     *
     * @param encoder
     * @param mime
     * @return
     */
    public static boolean hasCodecForMime(boolean encoder, String mime) {
        for (MediaCodecInfo info : sMCL.getCodecInfos()) {
            if (encoder != info.isEncoder()) {
                continue;
            }

            for (String type : info.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mime)) {
                    Log.i(TAG, "found codec " + info.getName() + " for mime " + mime);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *
     * @param path
     * @param mimeTypePrefix
     * @return
     * @throws IOException
     */
    public static MediaExtractor createMediaExtractorByMimeType(String path, String mimeTypePrefix) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(path, null);
        int trackIndex;
        for (trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++) {
            MediaFormat trackMediaFormat = extractor.getTrackFormat(trackIndex);
            if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith(mimeTypePrefix)) {
                extractor.selectTrack(trackIndex);
                break;
            }
        }
        if (trackIndex == extractor.getTrackCount()) {
            extractor.release();
            throw new IllegalStateException("couldn't get a track for " + mimeTypePrefix);
        }
        return extractor;
    }



}
