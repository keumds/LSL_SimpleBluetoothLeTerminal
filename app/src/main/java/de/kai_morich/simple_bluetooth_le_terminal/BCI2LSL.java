package de.kai_morich.simple_bluetooth_le_terminal;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import edu.ucsd.sccn.LSL;

public class BCI2LSL {
    private int PKT_STATE_Init = 0;
    private int PKT_STATE_SOF1_Found = 1;
    private int PKT_STATE_SOF2_Found = 2;
    private int PKT_STATE_PktLen_Found = 3;

    private byte PKT_MSG_START_1 = (byte)0xC0;
    private byte PKT_MSG_START_2 = (byte)0xA0;
    private byte PKT_MSG_STOP = (byte)0xC0;

    private int PKT_BUFFER_SIZE = 256;

    private int Pkt_rx_state = 0;
    private int Pkt_datalen = 0;
    private int Pkt_Pos_Counter = 0;
    private int Pkt_Data_Counter = 0;
    
    private float Pkt_Data_Signal = 0.0f;

    private List<Byte> Pkt_Data_Buffer;
    // Initialize StreamOutlet and other necessary objects here

    private static LSL.StreamInfo info;
    private static LSL.StreamOutlet outlet;

    private static void initializeLSL() {
        //LSL.StreamInfo info = new LSL.StreamInfo("Bio","EEG",1,100,LSL.ChannelFormat.float32,"myuid777");
        info = new LSL.StreamInfo("S1LSL", "EEG", 8, 250, LSL.ChannelFormat.float32, "myuid777");

        try {
            outlet = new LSL.StreamOutlet(info);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("*********************** initalizeLSL " );
    }
    public BCI2LSL() {
        Pkt_Data_Buffer = new ArrayList<>();
        initializeLSL();
        // Initialize StreamOutlet and other necessary objects here
    }

    public String csvBCI(byte[] ba) {
        StringBuilder str = new StringBuilder();
        str.append(String.format("%d,%d,", 0xFF & ba[0], 0xFF & ba[1]));
        for (int i = 0; i < 8; i++) {
            int pos = 2 + i * 3;
            str.append(String.format("%d,", byteArrayToInt(Arrays.copyOfRange(ba, pos, pos + 3))));
        }
        for (int i = 0; i < 3; i++) {
            int pos = 26 + i * 2;
            str.append(String.format("%d,", byteArrayToInt(Arrays.copyOfRange(ba, pos, pos + 2))));
        }
        str.append(String.format("%d", 0xFF & ba[32]));
        return str.toString();
    }

    public void Open_BCI(final byte[] reading, int begin, int end) {
        if (reading.length == 0) {
            return;
        }

        byte rxch;

        for (int pos=begin; pos<end; pos++) {
            rxch = reading[pos];
            //System.out.print(rxch);
            if (Pkt_rx_state == PKT_STATE_Init) {
                if (rxch == PKT_MSG_START_1)
                    Pkt_rx_state = PKT_STATE_SOF1_Found;
                Pkt_Data_Buffer.clear();
                //System.out.println("PKT_STATE_Init");
            } else if (Pkt_rx_state == PKT_STATE_SOF1_Found) {
                Pkt_Data_Buffer.add(rxch);
                if (rxch == PKT_MSG_START_2)
                    Pkt_rx_state = PKT_STATE_SOF2_Found;
                else
                    Pkt_rx_state = PKT_STATE_Init;
                //System.out.println("PKT_STATE_SOF1_Found");
            } else if (Pkt_rx_state == PKT_STATE_SOF2_Found) {
                Pkt_Data_Buffer.add(rxch);
                Pkt_rx_state = PKT_STATE_PktLen_Found;
                Pkt_datalen = rxch;
                Pkt_Pos_Counter = 2;
                Pkt_Data_Counter = 0;
                //System.out.println("PKT_STATE_SOF2_Found");
            } else if (Pkt_rx_state == PKT_STATE_PktLen_Found) {
                Pkt_Pos_Counter++;
                Pkt_Data_Buffer.add(rxch);

                if (Pkt_Pos_Counter == 32) {
                    Pkt_Data_Buffer.add((byte) PKT_MSG_START_1);
                    String received = csvBCI(toByteArray(Pkt_Data_Buffer));
                    //System.out.println(received);

                    String[] x = received.split(",");

                    float[] mysample = new float[]{
                        Float.parseFloat(x[2]) / 11.2f,
                        Float.parseFloat(x[3]) / 11.2f,
                        Float.parseFloat(x[4]) / 11.2f,
                        Float.parseFloat(x[5]) / 11.2f,
                        Float.parseFloat(x[6]) / 11.2f,
                        Float.parseFloat(x[7]) / 11.2f,
                        Float.parseFloat(x[8]) / 11.2f,
                        Float.parseFloat(x[9]) / 11.2f
                    };

                    // Push the mysample array to the StreamOutlet
                    //System.out.println("*********************** OUTLET" );
                    outlet.push_sample(mysample);

                    Pkt_rx_state = PKT_STATE_Init;

                    try {
                        Thread.sleep((long) (1));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (Pkt_Pos_Counter > 33)
                    Pkt_rx_state = PKT_STATE_Init;
            }
        }
    }

    public void test_BCI(final byte[] reading, int begin, int end) {
        float[] mysample = new float[]{
            Pkt_Data_Signal,
            Pkt_Data_Signal,
            Pkt_Data_Signal,
            Pkt_Data_Signal,
            Pkt_Data_Signal,
            Pkt_Data_Signal,
            Pkt_Data_Signal,
            Pkt_Data_Signal
        };
        Pkt_Data_Signal += 0.01f;
        if (Pkt_Data_Signal >= 1.0f) Pkt_Data_Signal=0.0f;

        outlet.push_sample(mysample);

        try {
            Thread.sleep((long) (1000.0/250.0));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private byte[] toByteArray(List<Byte> list) {
        byte[] array = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private int byteArrayToInt(byte[] bytes) {
        int value = 0;
        for (int i = 0; i < bytes.length; i++) {
            value <<= 8;
            value |= bytes[i] & 0xFF;
        }
        return value;
    }
/*
    public static void main(String[] args) {
        BCI2LSL test = new BCI2LSL();

        byte[] x = new byte[]{(byte) 0xC0, (byte) 0xA0, 0x08, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x27, 0x28, 0x29, 0x30, 0x31, (byte) 0xC0, (byte) 0xA0, 0x08, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, 0x27, 0x28, 0x29, 0x30, 0x31, (byte) 0xC0};

        while (true) {
            test.test_BCI(x);
        }
    }

 */
}
