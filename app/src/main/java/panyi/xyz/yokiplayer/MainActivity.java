package panyi.xyz.yokiplayer;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;

import java.util.List;

import me.rosuh.filepicker.config.FilePickerManager;

public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_CODE_SELECT_FILE = 53;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE} , 101);

        findViewById(R.id.select_file_btn).setOnClickListener((v)->{
            selectFile();
        });

        final MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        final MediaCodecInfo codecInfo[] = codecList.getCodecInfos();
    }

    private void openVideo(String filepath){
        VideoPlayerActivity.start(this , filepath);
    }

    private void selectFile(){
        FilePickerManager.INSTANCE.from(this).forResult(REQUEST_CODE_SELECT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_FILE && resultCode == RESULT_OK) {
            List<String> paths = FilePickerManager.INSTANCE.obtainData();
            if (paths != null && paths.size() > 0) {
                final String filePath = paths.get(0);
                System.out.println("filepath = " + filePath);
                openVideo(filePath);
            }
        }
    }
}//end class