package panyi.xyz.yokiplayer;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class YokiMediaPlayer {
    private static final String TAG = "YokiMediaPlayer";

    private MediaExtractor mVideoExtractor;
    private MediaCodec mVideoDecoder;
    private HandlerThread mVideoDecoderThread;
    private Handler mVideoThreadHandler;

    private MediaExtractor mAudioExtractor;
    private MediaCodec mAudioDecoder;

    private Surface mSurface;

    private String mFilePath;

    public YokiMediaPlayer(){
    }

    public void openFile(String path , Surface surface){
        if(TextUtils.isEmpty(path))
            return;

        mFilePath = path;
        mSurface = surface;
        release();

        try {
            startVideoDecoder();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startVideoDecoder() throws IOException {
        mVideoExtractor = MediaUtils.createMediaExtractorByMimeType(mFilePath , MediaUtils.TYPE_VIDEO);
        if(mVideoExtractor == null)
            return;

        MediaFormat mediaFormat = mVideoExtractor.getTrackFormat(mVideoExtractor.getSampleTrackIndex());
        final String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
        Log.i(TAG, "decoder mimeType = " + mimeType);
        if(!MediaUtils.hasCodecForMime(false , mimeType)){
            Log.e(TAG, "No decoder found for mimeType = " + mimeType);
            return;
        }

        mVideoDecoderThread = new HandlerThread("video decoder thread");
        mVideoDecoderThread.start();
        mVideoThreadHandler = new Handler(mVideoDecoderThread.getLooper());

        Log.i(TAG, "create video media decoder");
        mVideoDecoder = MediaCodec.createDecoderByType(mimeType);
        mVideoDecoder.configure(mediaFormat , mSurface , null , 0);
        setVideoDecoderCallback();

        mVideoDecoder.start();
    }

    private void setVideoDecoderCallback(){
        mVideoDecoder.setCallback(new MediaCodec.Callback(){
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                ByteBuffer buf = codec.getInputBuffer(index);

                final int readSize = mVideoExtractor.readSampleData(buf , 0);

                if(readSize > 0){
                    codec.queueInputBuffer(index , 0 , readSize , mVideoExtractor.getSampleTime() , 0);
                    //next
                    mVideoExtractor.advance();
                }else{
                    codec.queueInputBuffer(index , 0 , 0 , 0 , MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                if(info.size > 0){
                    codec.releaseOutputBuffer(index , 1000 * info.presentationTimeUs);
                }else{
                    codec.releaseOutputBuffer(index, false);
                }
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

            }
        },mVideoThreadHandler);
    }

    public void release(){
        if(mVideoExtractor != null){
            mVideoExtractor.release();
        }

        if(mVideoDecoder != null){
            mVideoDecoder.stop();

            mVideoDecoder.release();
        }

        if(mAudioExtractor != null){
            mAudioExtractor.release();
        }

        if(mAudioDecoder != null){
            mAudioDecoder.stop();

            mAudioDecoder.release();
        }
    }

}//end class
