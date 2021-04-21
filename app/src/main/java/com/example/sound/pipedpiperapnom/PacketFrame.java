package com.example.sound.pipedpiperapnom;

public class PacketFrame {
    private int seqNum;
    private byte[] data;

    public int getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(int seqNum) {
        this.seqNum = seqNum;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public PacketFrame(int seqNum, byte[] data) {

        this.seqNum = seqNum;
        this.data = data;
    }
}
