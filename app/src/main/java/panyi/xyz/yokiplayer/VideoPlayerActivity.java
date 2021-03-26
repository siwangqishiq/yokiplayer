package panyi.xyz.yokiplayer;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class VideoPlayerActivity extends AppCompatActivity {

    public static final String INTENT_PARAM_FILE = "_path";

    public static void start(Context ctx , String path){
        Intent it = new Intent(ctx , VideoPlayerActivity.class);
        it.putExtra(INTENT_PARAM_FILE , path);
        ctx.startActivity(it);
    }

    private String mFilePath;
    private SurfaceView mTextureView;
    private YokiMediaPlayer mYokiMediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        mFilePath = getIntent().getStringExtra(INTENT_PARAM_FILE);

        mTextureView = findViewById(R.id.surface_view);
        mYokiMediaPlayer = new YokiMediaPlayer();

        mTextureView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                mYokiMediaPlayer.openFile(mFilePath , holder.getSurface());
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });

//        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
//            @Override
//            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
//                final Surface surface = new Surface(surfaceTexture);
//                mYokiMediaPlayer.openFile(mFilePath , surface);
//            }
//
//            @Override
//            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
//
//            }
//
//            @Override
//            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
//                return false;
//            }
//
//            @Override
//            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
//
//            }
//        });
    }

    @Override
    protected void onDestroy() {
        if(mYokiMediaPlayer != null){
            mYokiMediaPlayer.release();
            mYokiMediaPlayer = null;
        }
        super.onDestroy();
    }
}//end class