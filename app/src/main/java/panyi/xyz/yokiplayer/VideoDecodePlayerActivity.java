package panyi.xyz.yokiplayer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoDecodePlayerActivity extends AppCompatActivity {

    public static final String INTENT_PARAM_FILE = "_path";

    public static void start(Context ctx , String path){
        Intent it = new Intent(ctx , VideoDecodePlayerActivity.class);
        it.putExtra(INTENT_PARAM_FILE , path);
        ctx.startActivity(it);
    }

    private String mFilePath;

    private MediaExtractor mExtractor;

    private HandlerThread mVideoThread;
    private Handler mVideoThreadHandler;

    private MediaCodec mMediaCodec;

    private ImageView mImageView;

    private int mVideoWidth = 800;
    private int mVideoHeight = 600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_decode_player);

        mImageView = findViewById(R.id.image);

        mFilePath = getIntent().getStringExtra(INTENT_PARAM_FILE);

        try {
            mExtractor = MediaUtils.createMediaExtractorByMimeType(mFilePath , MediaUtils.TYPE_VIDEO);
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaFormat mediaFormat = mExtractor.getTrackFormat(mExtractor.getSampleTrackIndex());
        LogUtil.log(mediaFormat.toString());

        mVideoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        mVideoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);

        LogUtil.log("视频宽 x 高 : " + mVideoWidth +" x " + mVideoHeight);

        final String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
        LogUtil.log("video decoder mimeType = " + mimeType);
        if(!MediaUtils.hasCodecForMime(false , mimeType)){
            LogUtil.log("No decoder found for mimeType = " + mimeType);
            return;
        }

//        mVideoThread = new HandlerThread("video decode thread");
//        mVideoThread.start();
//        mVideoThreadHandler = new Handler(mVideoThread.getLooper());
        mVideoThreadHandler = new Handler();

        try {
            mMediaCodec = MediaCodec.createDecoderByType(mimeType);
        } catch (IOException e) {
            LogUtil.log("No decoder found for mimeType = " + mimeType);
            e.printStackTrace();
        }

        mMediaCodec.configure(mediaFormat , null , null ,0);

        mMediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                if(mExtractor.getSampleTrackIndex() == -1){
                    return;
                }

                ByteBuffer buf = codec.getInputBuffer(index);
                // LogUtil.log("input buf :" + buf.remaining() +"  " + buf.getClass());
                final int readSize = mExtractor.readSampleData(buf , 0);

                if(readSize > 0){
                    codec.queueInputBuffer(index , 0 , readSize , mExtractor.getSampleTime() , 0);
                    //next
                    mExtractor.advance();
                }else{
                    codec.queueInputBuffer(index , 0 , 0 , 0 , MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                Image frame = codec.getOutputImage(index);
                LogUtil.log(frame.getFormat()+"");
                handleRawImage(frame);

                if(info.size > 0){
                    codec.releaseOutputBuffer(index ,  info.presentationTimeUs * 1000);
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
        } , mVideoThreadHandler);

        mMediaCodec.start();
    }

    @Override
    protected void onDestroy() {
        mMediaCodec.release();
        super.onDestroy();
    }

    private void handleRawImage(Image image){
        YuvImage yuvImage = new YuvImage(YUV_420_888toNV21(image), ImageFormat.NV21, mVideoWidth,
                mVideoHeight, null);

        final Bitmap bitmap = createBitmap(yuvImage , mVideoWidth , mVideoHeight);

//        final Bitmap bitmap = createBitmapFromImage(image , mVideoWidth , mVideoHeight);
        // LogUtil.log("生成bitmap " + System.currentTimeMillis());
        mImageView.setImageBitmap(bitmap);
    }

    private Bitmap createBitmapFromImage(Image image , int width , int height){
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }

    private Bitmap createBitmap(YuvImage yuvImage , int width , int height){
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, stream);
        byte[] imageBytes = stream.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        try {
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private static byte[] YUV_420_888toNV21(Image image) {
        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);
        return nv21;
    }
}//end class