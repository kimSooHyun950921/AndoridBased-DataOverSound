package com.example.sound.pipedpiperapnom;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.util.ArrayList;

import casualcoding.reedsolomon.EncoderDecoder;
import google.zxing.common.reedsolomon.ReedSolomonException;

public class Listentone{

    int HANDSHAKE_START_HZ = 20000;
    int HANDSHAKE_END_HZ = 20000 + 512;

    int START_HZ = 16384;
    int STEP_HZ = 256;
    int BITS = 4;


    int FEC_BYTES = 4;



    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;
    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    boolean startFlag;


    FastFourierTransformer transform;



    public Listentone(){


        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);




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

    public String PreRequest() throws EncoderDecoder.DataTooLargeException {
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
                Log.d("ListenTone freq",Double.toString(frequency));

                if (in_packet && match(frequency, HANDSHAKE_END_HZ)) {
                    Log.d("ListenTone Packet", Integer.toString(packet.size()));
                    Log.d("ListenTone END", "END");


                    byte[] chunk = extract_packet(packet);

                    byte[] errorCorrection = decodeBitChunk(chunk);
                    byte[] decoding_result = new EncoderDecoder().decodeData(errorCorrection, this.FEC_BYTES);
                    StringData = new String(decoding_result);
                    startFlag = false;
                    break;
                } else if (in_packet) {
                    packet.add(frequency);
                } else if (match(frequency, HANDSHAKE_START_HZ)) {
                    Log.d("ListenTone - START","start");
                    in_packet = true;
                }
            }
            Log.d("ListenTone RESULT Call", StringData);
        }
            catch (ReedSolomonException e) {
                System.out.println("RESULT:"+StringData);
                e.printStackTrace();
            }
        this.mAudioRecord.stop();
        return StringData;
    }




    private byte[] decodeBitChunk(byte[] chunk) {
        ArrayList<Byte> rawchunk = new ArrayList<Byte>();
        for(int i = 0; i<chunk.length;i++){
            rawchunk.add(chunk[i]);
        }
        int leng = chunk.length;
        byte[] allData = new byte[leng/2 + 1];
        Log.d("ListenTone rawfdata",Integer.toString(chunk.length));

        int data_index = 0;


       while(rawchunk.size()>1){
            byte first_data = (byte)(rawchunk.remove(0) << (byte)4);
            byte final_data = (byte)(first_data | rawchunk.remove(0));
            allData[data_index++] = final_data;
            Log.d("ListenTone StringData",Character.toString((char)final_data));
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

        for(int i = 0; i<curPacket.length;i++){
            try{
                Log.d("ListenTone before",Double.toString(curPacket[i]));}
            catch(NullPointerException e){
            }
        }

        for(int i = 0; i<curPacket.length;i+=2){
            sampling[i/2] = curPacket[i];
        }

        for(int i = 0; i<sampling.length;i++){
            try{
                Log.d("ListenTone relay2 ",Double.toString(sampling[i]));}
            catch(NullPointerException e){
                sampling[i] = 0D;
            }
        }
        for(int i = 0; i<sampling.length;i++){
            try{
                Log.d("ListenTone relay1",Double.toString(sampling[i]));}
            catch(NullPointerException e){
                sampling[i] = 0D;
            }
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

        for(int i = 0; i<chunkIdx;i++){
            Log.d("ListenTone chunks",Short.toString(chunks[i]));
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
   //     Log.d("ListenTone peak_freq",Double.toString(peak_freq));
        return Math.abs(peak_freq * mSampleRate);

    }

    public void ManagingRecvSound(){
        String finalResult = null;
        StringBuffer addPartialResult = new StringBuffer();

        try {
            while(true) {
                String partialResult = this.PreRequest();
                if(partialResult == null)
                    continue;
                if(partialResult.contains("FIN"))
                    break;

                addPartialResult.append(this.removeFecBytes(partialResult));
            }

        } catch (EncoderDecoder.DataTooLargeException e) {
            e.printStackTrace();
        }
        Log.d("ListenTone",addPartialResult.toString());

        String[] parsingResult = this.parsingData(addPartialResult.toString());

    }

    private String[] parsingData(String s) {
        JsonElement lcode = null;
        String date = null;
        JsonElement time = null;
        String curTime = null;

        JsonElement  protocol = new JsonParser().parse(s);


        JsonElement outerJson = protocol.getAsJsonObject().get("data");
        if(outerJson.isJsonObject()){
            JsonObject innersjon = outerJson.getAsJsonObject();
            time = innersjon.get("ctime");
            lcode = innersjon.get("l_code");

        }


        date = time.getAsString().split(" ")[0];
        curTime = time.getAsString().split(" ")[1];


        String[] result = {lcode.getAsString(), date,curTime};
        return result;

    }

    private StringBuffer removeFecBytes(String partialResult) {
        Log.d("ListenTonebeforeRemoved",Integer.toString(partialResult.length()));
        int length = partialResult.length()-this.FEC_BYTES/2;

        StringBuffer removedBytes = new StringBuffer();
        for(int i = 0; i< length;i++){
            removedBytes.append(partialResult.charAt(i));
        }
        Log.d("ListenTone Removed",removedBytes.toString());

        return removedBytes;
    }
}
