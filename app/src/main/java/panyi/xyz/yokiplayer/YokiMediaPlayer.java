package panyi.xyz.yokiplayer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaSync;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class YokiMediaPlayer {
    private static final String TAG = "YokiMediaPlayer";

    private MediaExtractor mVideoExtractor;
    private MediaCodec mVideoDecoder;
    private HandlerThread mVideoDecoderThread;
    private Handler mVideoThreadHandler;

    private AudioTrack mAudioTrack;
    private MediaExtractor mAudioExtractor;
    private MediaCodec mAudioDecoder;
    private HandlerThread mAudioDecoderThread;
    private Handler mAudioHandler;

    private MediaSync mMediaSync;

    private Surface mSurface;

    private String mFilePath;

    private AtomicBoolean isPlaying = new AtomicBoolean(false);

    public YokiMediaPlayer(){
    }

    public void openFile(String path , Surface surface){
        if(TextUtils.isEmpty(path))
            return;

        mFilePath = path;
        release();

        mSurface = surface;
        try {
            mMediaSync = new MediaSync();
            if(mMediaSync != null){
                mMediaSync.setSurface(surface);
                mSurface = mMediaSync.createInputSurface();
            }

            prepareAudioDecoder();
            prepareVideoDecoder();


            resume();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void prepareAudioDecoder() throws IOException {
        mAudioExtractor = MediaUtils.createMediaExtractorByMimeType(mFilePath , MediaUtils.TYPE_AUDIO);
        if(mAudioExtractor == null)
            return;

        mAudioDecoderThread = new HandlerThread("audio decodec");
        mAudioDecoderThread.start();
        mAudioHandler = new Handler(mAudioDecoderThread.getLooper());

        mAudioTrack = createAudioTrack();

        if(mMediaSync != null){
            mMediaSync.setAudioTrack(mAudioTrack);
            mMediaSync.setCallback(new MediaSync.Callback() {
                @Override
                public void onAudioBufferConsumed(@NonNull MediaSync sync, @NonNull ByteBuffer audioBuffer, int bufferId) {
                    //System.out.println("onAudioBufferConsumed => " + audioBuffer.capacity() +"  bufferId = " + bufferId);
                    if(!isPlaying.get()){
                        return;
                    }

                    if(mAudioDecoder != null){
                        mAudioDecoder.releaseOutputBuffer(bufferId , false);
                    }
                }
            } , null);

            mMediaSync.setOnErrorListener(new MediaSync.OnErrorListener() {
                @Override
                public void onError(@NonNull MediaSync sync, int what, int extra) {
                    //System.out.println("sync ERROR = " + what +"   extra = " + extra);
                }
            } , null);
        }

        final MediaFormat audioMediaFormat = mAudioExtractor.getTrackFormat(mAudioExtractor.getSampleTrackIndex());
        final String mimeType = audioMediaFormat.getString(MediaFormat.KEY_MIME);
        Log.i(TAG, "audio decoder mimeType = " + mimeType);
        if(!MediaUtils.hasCodecForMime(false , mimeType)){
            Log.e(TAG, "No decoder found for mimeType = " + mimeType);
            return;
        }

        mAudioDecoder = MediaCodec.createDecoderByType(mimeType);
        mAudioDecoder.configure(audioMediaFormat , null , null , 0);

        setAudioDecoderCallback();
    }

    private void setAudioDecoderCallback(){

        mAudioDecoder.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                if(!isPlaying.get()){
                    return;
                }

                if (mAudioExtractor == null){
                    return;
                }

                ByteBuffer buffer = codec.getInputBuffer(index);
                final int readSize = mAudioExtractor.readSampleData(buffer , 0);
                if(readSize > 0){
                    codec.queueInputBuffer(index , 0 , readSize , mAudioExtractor.getSampleTime() , 0);
                    mAudioExtractor.advance();
                }else{
                    codec.queueInputBuffer(index , 0 , 0 , mAudioExtractor.getSampleTime() , MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int bufferId, @NonNull MediaCodec.BufferInfo info) {
                System.out.println("audio info size = " + info.size +"  presentationTimeUs = " + info.presentationTimeUs);

                if(!isPlaying.get()){
                    return;
                }

                if(info.size > 0){
                    ByteBuffer buf = codec.getOutputBuffer(bufferId);
//                    ByteBuffer copyBuffer = ByteBuffer.allocate(buf.remaining());
//                    copyBuffer.put(buf);
//                    copyBuffer.flip();
//
//                    codec.releaseOutputBuffer(bufferId , false);
                    if(mMediaSync != null){
                        mMediaSync.queueAudio(buf  , bufferId , info.presentationTimeUs);
                    }
                }else{
                    codec.releaseOutputBuffer(bufferId , false);
                }
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

            }
        } , mAudioHandler);
    }

    private AudioTrack createAudioTrack(){
        MediaFormat mediaFormat = mAudioExtractor.getTrackFormat(mAudioExtractor.getSampleTrackIndex());
        int sampleRateInHz = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelConfig = (mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO);
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);

        final int frameCount = 200 * sampleRateInHz / 1000;
        final int frameSizeInBytes = Integer.bitCount(channelConfig) * getBytesPerSample(audioFormat);
        // ensure we consider application requirements for writing audio data
        minBufferSizeInBytes = 2 * Math.max(minBufferSizeInBytes, frameCount * frameSizeInBytes);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, channelConfig, audioFormat, minBufferSizeInBytes, AudioTrack.MODE_STREAM);
        return mAudioTrack;
    }


    private void prepareVideoDecoder() throws IOException {
        mVideoExtractor = MediaUtils.createMediaExtractorByMimeType(mFilePath , MediaUtils.TYPE_VIDEO);
        if(mVideoExtractor == null)
            return;

        MediaFormat mediaFormat = mVideoExtractor.getTrackFormat(mVideoExtractor.getSampleTrackIndex());
        final String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
        Log.i(TAG, "video decoder mimeType = " + mimeType);
        if(!MediaUtils.hasCodecForMime(false , mimeType)){
            Log.e(TAG, "No decoder found for mimeType = " + mimeType);
            return;
        }
        setupVideoMediaCodec(mediaFormat , mimeType);
    }



    private void setupVideoMediaCodec(MediaFormat mediaFormat , String mimeType) throws IOException {
        mVideoDecoderThread = new HandlerThread("video decoder thread");
        mVideoDecoderThread.start();
        mVideoThreadHandler = new Handler(mVideoDecoderThread.getLooper());

        Log.i(TAG, "create video media decoder");
        mVideoDecoder = MediaCodec.createDecoderByType(mimeType);
        mVideoDecoder.configure(mediaFormat , mSurface , null , 0);
        setVideoDecoderCallback();
    }

    private void setVideoDecoderCallback(){
        mVideoDecoder.setCallback(new MediaCodec.Callback(){
            private long renderStart = -1;
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                if(!isPlaying.get())
                    return;

                if(mVideoExtractor.getSampleTrackIndex() == -1){
                    return;
                }

                ByteBuffer buf = codec.getInputBuffer(index);
                final int readSize = mVideoExtractor.readSampleData(buf , 0);
                //System.out.println("readSize = " + readSize +" index = " + index);

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
                System.out.println("Video info size = " + info.size +"  presentationTimeUs = " + info.presentationTimeUs);
                if(!isPlaying.get()){
                    return;
                }

                //System.out.println("getOutputFormat ->" + bufferFormat);
                if(info.size > 0){
                    if(isPlaying.get()){
                        codec.releaseOutputBuffer(index ,  info.presentationTimeUs * 1000);
                    }
//                    codec.releaseOutputBuffer(index ,  true);
                }else{
                    if(isPlaying.get()){
                        codec.releaseOutputBuffer(index, false);
                    }
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
        isPlaying.set(false);

        if(mMediaSync != null){
            mMediaSync.flush();
            mMediaSync.release();
            mMediaSync = null;
        }

        if(mAudioTrack != null){
            mAudioTrack.stop();
            mAudioTrack.release();
        }


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

    public void pause(){
        isPlaying.set(false);

        mMediaSync.setPlaybackParams(new PlaybackParams().setSpeed(0.0f));

        if(mAudioDecoder != null){
            mAudioDecoder.stop();
        }

        if(mVideoDecoder != null){
            mVideoDecoder.stop();
        }
    }

    public void resume(){
        isPlaying.set(true);
        mMediaSync.setPlaybackParams(new PlaybackParams().setSpeed(1.0f));

        if(mAudioDecoder != null){
            mAudioDecoder.start();
        }

        if(mVideoDecoder != null){
            mVideoDecoder.start();
        }
    }



    public static int getBytesPerSample(int audioFormat) {
        switch (audioFormat) {
            case AudioFormat.ENCODING_PCM_8BIT:
                return 1;
            case AudioFormat.ENCODING_PCM_16BIT:
            case AudioFormat.ENCODING_IEC61937:
            case AudioFormat.ENCODING_DEFAULT:
                return 2;
            case AudioFormat.ENCODING_PCM_FLOAT:
                return 4;
            case AudioFormat.ENCODING_INVALID:
            default:
                throw new IllegalArgumentException("Bad audio format " + audioFormat);
        }
    }
}//end class
