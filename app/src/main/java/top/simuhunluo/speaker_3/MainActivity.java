package top.simuhunluo.speaker_3;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.util.LinkedList;


public class MainActivity extends AppCompatActivity {

    private boolean flag = true;
    private Button btn_begin;
    private Button btn_end;
    private Thread record;
    private Thread play;
    private int recordBufferSize;//缓存大小
    private byte[] recordbytes;
    private LinkedList<byte[]> recordList;//存放若干个录入字节数组
    private AudioRecord audioRecord;//录音

    private int playBufferSize;//缓存大小
    private byte[] playbytes;
    private AudioTrack audioTrack;//播放

    private AcousticEchoCanceler canceler;//回声消除

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn_begin = (Button) findViewById(R.id.btn_begin);
        btn_end = (Button) findViewById(R.id.btn_end);
        btn_begin.setOnClickListener(listener);
        btn_end.setOnClickListener(listener);
        btn_end.setClickable(false);
    }

    public boolean initAEC(int audioSession) {
        if (canceler != null) {
            return false;
        }
        canceler = AcousticEchoCanceler.create(audioSession);
        canceler.setEnabled(true);
        return canceler.getEnabled();
    }

    private View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_begin:
                    btn_end.setClickable(true);
                    btn_begin.setClickable(false);
                    init();
                    break;
                case R.id.btn_end:
                    btn_begin.setClickable(true);
                    btn_end.setClickable(false);
                    end();
                    break;
            }
        }
    };

    private void end() {
        flag = false;//线程将直接终止啊？貌似并不。。
        audioRecord.stop();
        audioRecord = null;
        audioTrack.stop();
        audioTrack = null;
        this.finish();
    }

    private void init() {
        int Hz = 11025;
        recordBufferSize = AudioRecord.getMinBufferSize(Hz, AudioFormat.CHANNEL_IN_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
//        录音对象
        int audioSession;
        if (isDeviceSupport()) {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, Hz,
                    AudioFormat.CHANNEL_IN_DEFAULT,
                    AudioFormat.ENCODING_PCM_16BIT, recordBufferSize);
        } else {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, Hz,
                    AudioFormat.CHANNEL_IN_DEFAULT,
                    AudioFormat.ENCODING_PCM_16BIT, recordBufferSize);
        }
        recordbytes = new byte[recordBufferSize];
        recordList = new LinkedList<>();//用来存放recordbytes
        audioSession = audioRecord.getAudioSessionId();
        initAEC(audioSession);


        playBufferSize = AudioTrack.getMinBufferSize(Hz,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (isDeviceSupport() && audioRecord != null) {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, Hz,
                    AudioFormat.CHANNEL_IN_DEFAULT,
                    AudioFormat.ENCODING_PCM_16BIT, playBufferSize,
                    AudioTrack.MODE_STREAM, audioSession);
        } else {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, Hz,
                    AudioFormat.CHANNEL_IN_DEFAULT,
                    AudioFormat.ENCODING_PCM_16BIT, playBufferSize,
                    AudioTrack.MODE_STREAM);
        }

        playbytes = new byte[playBufferSize];

        record = new Thread(new record());
        play = new Thread(new play());
        record.start();
        play.start();

    }

    class record implements Runnable {
        @Override
        public void run() {
            byte[] bytes;
            audioRecord.startRecording();
            while (flag) {
                try {
                    audioRecord.read(recordbytes, 0, recordBufferSize);
                    bytes = recordbytes.clone();
                    if (recordList.size() >= 2) {
                        recordList.removeFirst();//移除第一个
                    }
                    recordList.add(bytes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class play implements Runnable {

        @Override
        public void run() {
            byte[] bytes = null;
            audioTrack.play();
            try {
                Thread.currentThread().sleep(400);//设置延迟，测试0.3s~0.5s为宜，给予打开麦克风一点时间，可以尽量避免异常的抛出
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (flag) {
                try {
                    playbytes = recordList.getFirst();//刚启动时候由于一段时间没有声音传入，会导致一段时间的抛出大量异常
                    bytes = playbytes.clone();
                    audioTrack.write(bytes, 0, bytes.length);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean isDeviceSupport() {
        return AcousticEchoCanceler.isAvailable();
    }

}
