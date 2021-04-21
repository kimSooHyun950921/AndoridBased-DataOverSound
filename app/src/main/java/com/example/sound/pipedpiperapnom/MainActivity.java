package com.example.sound.pipedpiperapnom;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonElement;

import com.google.gson.JsonParser;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import casualcoding.reedsolomon.EncoderDecoder;
import google.zxing.common.reedsolomon.ReedSolomonException;


public class MainActivity extends AppCompatActivity implements ToneThread.ToneCallback {
    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int FEC_BYTES = 4;
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);





    ArrayAdapter<String> adapter;
    ArrayList<String> listItems = new ArrayList<String>();
    ListView listView;



    TextView resultView;
    View play_tone;
    View listen_tone;
    ProgressBar progress;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        play_tone = findViewById(R.id.play_tone);
        listen_tone = findViewById(R.id.listen_tone);
        resultView = findViewById(R.id.text_view);




        listen_tone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Listen Start!", Toast.LENGTH_SHORT).show();
                try {
                    requestAudioPermissions();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }
        );
    }

    public void receiveMessage() throws InterruptedException, ExecutionException {
        Toast.makeText(MainActivity.this, "ThreadStart!", Toast.LENGTH_SHORT).show();
        ListenAsync task = new ListenAsync();
        task.execute(new String[] {"useless"});




       /*  Thread receive = new Thread(new Runnable() {

            Listentone recv_tone = new Listentone();


            @Override
            public void run() {



                    recv_tone.ManagingRecvSound();



            }
        });
        Toast.makeText(MainActivity.this, "ThreadStart!", Toast.LENGTH_SHORT).show();

        receive.start();*/

    }

    public void sendMessage(String message){
        Log.d("Message", message);
        byte[] payload = new byte[0];
        payload = message.getBytes(Charset.forName("UTF-8"));
        Log.d("PayLoad", payload.toString());
        EncoderDecoder encoder = new EncoderDecoder();
        final byte[] fec_payload;
        Log.d("ENCODING", encoder.toString());
        try {
            fec_payload = encoder.encodeData(payload, FEC_BYTES);
        } catch (EncoderDecoder.DataTooLargeException e) {
            return;
        }
        Log.d("FEC_PAYLOAD", fec_payload.toString());
        ByteArrayInputStream bis = new ByteArrayInputStream(fec_payload);
        Log.d("BytpeArrayInputStream", bis.toString());
        play_tone.setEnabled(false);
        ToneThread.ToneIterator tone = new BitstreamToneGenerator(bis, 7);
        Log.d("TONE", tone.toString());
        Thread play_tone  = new ToneThread(tone, MainActivity.this);
        play_tone.start();




    }
    private void requestAudioPermissions() throws InterruptedException, ExecutionException {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
            }
        }
        //If permission is granted, then go ahead recording audio
        else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            //Go ahead with recording audio now
            Toast.makeText(MainActivity.this, "ListenOn Class In Right Away", Toast.LENGTH_SHORT).show();
            receiveMessage();
        }

    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    try {
                        receiveMessage();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions Denied to record audio", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    @Override
    public void onProgress(int current, int total) {
        progress.setMax(total);
        progress.setProgress(current);
    }

    @Override
    public void onDone() {
        play_tone.setEnabled(true);
        progress.setProgress(0);
    }

    private class ListenAsync extends AsyncTask<String, String, String[]> {
        int HANDSHAKE_START_HZ = 20000;
        int HANDSHAKE_END_HZ = 20000 + 512;
        String AllData = null;
        int START_HZ = 16384;
        int STEP_HZ = 256;
        int BITS = 4;
        int FEC_BYTES = 4;

        private int mAudioSource = MediaRecorder.AudioSource.MIC;
        private int mSampleRate = 44100;
        private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
        private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
        private float interval = 0.1f;
        private Context context;

        private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

        public AudioRecord mAudioRecord = null;
        boolean startFlag;
        FastFourierTransformer transform;
        private int size;
        private int addSize = 0;
        private int repeatCount;
        ArrayList<PacketFrame> packets;

        public ListenAsync() {
            transform = new FastFourierTransformer(DftNormalization.STANDARD);
            startFlag = false;
            mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
            packets = new ArrayList<>();
            for(int i = 0; i<60 ;i++){
                packets.add(i,null);
            }
            this.size = 0;
            this.addSize = 0;
            this.repeatCount = 0;
            this.context = context;
            this.AllData = "";
        }

        private int findPowerSize(int curSize){
            int i = 1;
            int result = 0;
            int curDiff=0;
            boolean isFirst = true;
            while(true){
                result = (int)Math.pow(2,i);
                curDiff = curSize-result;
                if(curDiff < 0){
                    break;
                }
                i++;
            }
            return (int)Math.pow(2,i-1);
        }

        public void PreRequest() throws EncoderDecoder.DataTooLargeException {
            String StringData = null;
            this.mAudioRecord.startRecording();
            try{

                boolean in_packet = false;
                int blocksize = findPowerSize((int) (long) Math.round(interval / 2 * mSampleRate));
                short[] buffer = new short[blocksize];
                double[] toTransform = new double[blocksize];
                ArrayList<Double> packet = new ArrayList<>();
                while (startFlag == false) {
                    int bufferedReadResult = mAudioRecord.read(buffer, 0, blocksize);
                    if (bufferedReadResult < 0) {
                        break;
                    }
                    for (int i = 0; i < blocksize && i < bufferedReadResult; i++) {
                        toTransform[i] = (double) buffer[i];
                    }
                    double frequency = findFrequency(toTransform);//dominant

                    if (in_packet && match(frequency, HANDSHAKE_END_HZ)) {
                        Log.d("ListenTone Packet Size",Integer.toString(packet.size()));

                        byte[] chunk = extract_packet(packet);
                        byte[] errorCorrection = decodeBitChunk(chunk);

                        byte[] decoding_result = new EncoderDecoder().decodeData(errorCorrection, this.FEC_BYTES);
                        byte[] removedFecBytes = this.removeFecBytes(decoding_result);

                        String[] result = Struct.unpack("hh".toCharArray(), removedFecBytes);
                        int seqNum = Integer.parseInt(result[0]);
                        if(seqNum == 0){

                            this.repeatCount += 1;
                            this.size = Integer.parseInt(result[1]);
                        }
                        else {
                            result = Struct.unpack("hccc".toCharArray(), removedFecBytes);
                            if(packets.get(seqNum) == null){
                                String add_Result = "";
                                for(int i = 1; i<result.length;i++){

                                    this.addSize += 1;
                                    add_Result+=result[i];
                                }
                                this.packets.set(seqNum,new PacketFrame(seqNum, (add_Result).getBytes()));

                                if (this.packets.get(seqNum) != null){
                                    String s = new String(this.packets.get(seqNum).getData());
                                }
                                else{
                                }

                            }
                        }
                        try {
                            Log.d("ListenTone RESULT Call1", result[1]);
                            Log.d("ListenTone RESULT Call2", result[2]);
                            Log.d("ListenTone RESULT Call3", result[3]);
                            AllData.concat(result[1]).concat(result[1]).concat(result[3]);
                            Log.d("ListenTone RESULT Call3", AllData);
                        }catch(ArrayIndexOutOfBoundsException e){
                            Log.d("ListenTone RESULT None", "None");

                        }
                        startFlag = false;
                        break;
                    } else if (in_packet) {
                        packet.add(frequency);
                    } else if (match(frequency, HANDSHAKE_START_HZ)) {
                        Log.d("ListenTone - START","start");
                        in_packet = true;
                    }
                }
            }
            catch (ReedSolomonException e) {
                e.printStackTrace();
            }
            this.mAudioRecord.stop();
        }

        private String decodingUTF8(byte[] decoding_result) {
            String result ="";
            try {
                result = new String(decoding_result,"UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return result;

        }


        private byte[] decodeBitChunk(byte[] chunk) {
            ArrayList<Byte> rawchunk = new ArrayList<Byte>();
            for(int i = 0; i<chunk.length;i++){
                rawchunk.add(chunk[i]);
            }
            int leng = chunk.length;
            byte[] allData = new byte[leng/2 + 1];

            int data_index = 0;

            while(rawchunk.size()>1){
                byte first_data = (byte)(rawchunk.remove(0) << (byte)4);
                byte final_data = (byte)(first_data | rawchunk.remove(0));
                allData[data_index++] = final_data;
            }
            return allData;
        }

        private byte[] extract_packet(ArrayList<Double> packet) {
            Double[] curPacket = packet.toArray(new Double[packet.size()]);
            Double[] sampling = new Double[packet.size()/2+1];
            ArrayList<Double> forChunkSample = new ArrayList<Double>();

            for(int i = 0;i<sampling.length;i++){
                sampling[i]=0D;
            }


            for(int i = 0; i<curPacket.length;i+=2){
                sampling[i/2] = curPacket[i];
            }

            byte[] chunks = new byte[sampling.length];

            int chunkIdx = 0;
            for(int i = 1; i<sampling.length;i++){
                if(sampling[i]!=0D){
                    double freq = sampling[i];
                    int tone = (int) Math.round((freq - START_HZ) / STEP_HZ);
                    if (tone >= 0 && tone < Math.pow(2, BITS)) {
                        chunks[chunkIdx] = (byte) tone;
                        chunkIdx++;
                    }
                }
            }
            return chunks;
        }

        private boolean match(double freq1, double freq2){
            return Math.abs(freq1 - freq2) < 30.0;
        }

        private Double[] fftfreq(int leng,int duration){
            Double val = 1.0 / (leng * duration);
            Double[] results = new Double[leng];
            int N = (int)(leng-1)/(int)2 + 1;
            int[] p1 = new int[N];
            int[] p2 = new int[N];
            for(int i = 0;i<p1.length;i++){
                p1[i] = i;
                p2[p1.length-i-1] = -i-1;
            }
            for(int i = 0;i<leng;i++){
                if(i<N){
                    results[i] = p1[i]*val;
                }
                else{
                    results[i]=p2[i-N]*val;
                }
            }
            return results;

        }

        private double findFrequency(double[] toTransform) {
            int len = toTransform.length;
            double[] real = new double[len];
            double[] img = new double[len];
            double realNum;
            double imgNum;
            double[] mag = new double[len];

            Complex[] complx = transform.transform(toTransform,TransformType.FORWARD);
            Double[] freq = this.fftfreq(complx.length,1);

            for(int i = 0; i< complx.length; i++){
                realNum = complx[i].getReal();
                imgNum = complx[i].getImaginary();
                mag[i] = Math.sqrt((realNum * realNum) + (imgNum * imgNum));
            }
            Double max = 0D;
            int maxIndex = 0;
            for(int i = 0;i<complx.length;i++){
                if(mag[i]>max){
                    max = mag[i];
                    maxIndex = i;
                }
            }
            Double peak_freq = freq[maxIndex];
            return Math.abs(peak_freq * mSampleRate);
        }

        public String[] ManagingRecvSound(){
            String finalResult = null;
            String[] parsingResult = new String[0];
            try {
                this.PreRequest();
                while(this.addSize < this.size && this.repeatCount < 10) {
                    this.PreRequest();
                }
                finalResult = addData();
            } catch (EncoderDecoder.DataTooLargeException e) {
                e.printStackTrace();
            }
            try {
                parsingResult = this.parsingData(finalResult);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return parsingResult;
        }

        private String addData() {
            StringBuffer buf = new StringBuffer();
            for(int i = 1; i<this.packets.size();i++){
                if(this.packets.get(i)==null) {
                    break;
                }
                PacketFrame partialPacket = this.packets.get(i);
                buf.append(decodingUTF8(partialPacket.getData()));
            }
            return buf.toString();
        }

        private String[] parsingData(String s) throws JSONException {
            JsonElement lcode = null;
            String date = null;
            JsonElement time = null;
            String curTime = null;

            try {
                JsonElement protocol = new JsonParser().parse(s);
                time = protocol.getAsJsonObject().get("ctime");
                lcode = protocol.getAsJsonObject().get("l_code");
                date = time.getAsString().split(" ")[0];
                curTime = time.getAsString().split(" ")[1];
                String[] result = {lcode.getAsString(), date, curTime};

                return result;

            }
            catch(NullPointerException e){
                String[] result = {"DNLAB",date,curTime};

                return result;

            }
            catch(IllegalStateException e){
                return null;
            }
        }

        private byte[] removeFecBytes(byte[] partialResult) {
            int length = partialResult.length - this.FEC_BYTES / 2;
            byte[] removedBytes = new byte[length];
            for (int i = 0; i < length; i++) {
                removedBytes[i] = partialResult[i];
            }
            Log.d("ListenTone Removed", removedBytes.toString());

            return removedBytes;
        }


        @Override
        protected String[] doInBackground(String... strings) {
            String[] parsingResult = this.ManagingRecvSound();
            Log.d("ListenTone FinalResult", parsingResult[0]+" "+parsingResult[1]+" "+parsingResult[2]);




            return parsingResult;
        }
        @Override
        protected void onProgressUpdate(String... strings){
            try {
                Log.d("ListenTone Progress", strings[0]);
                resultView.setText(strings[0]);
            }
            catch(NullPointerException e){
                resultView.setText("None Data");
            }

        }
        @Override
        protected void onPostExecute(String[] result){
            resultView.setText(result[0]+" "+result[1]+" "+result[2]);
           //  Toast.makeText(MainActivity.this,result.toString(),Toast.LENGTH_SHORT);
            DBConnect db = new DBConnect(this.context);
            db.setStudentID(20100000);
            db.setClassLoc("DNLAB");
            db.setAttendDate("2019-05-12");
            db.setAttendTime("12:30");
            db.execute();
        }

    }


}
