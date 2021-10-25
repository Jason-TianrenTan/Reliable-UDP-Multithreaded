package com.company.IO;

import com.company.Utils.ByteUtils;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class UDPClientHandler implements Runnable {

    private final DatagramSocket connection;
    private static final int BUFFER_SIZE = 4096;
    private static final int FILE_BUFFER_SIZE = 4096;
    private static final int RESPONSE_SIZE = 4;
    int port;
    int id;
    private boolean packetUpdated = false;
    private boolean active = true;
    private boolean fileTranserMode;
    private ArrayList<String> fileList;
    private String dir;
    private InetAddress clientAddress;
    private DatagramPacket packet;
    private FileInputStream fileInputStream;

    //File transfer
    DatagramPacket filePacket;
    int ack = -1;
    boolean ackReceived = false;
    int bytes = 0;
    int seq = 0;
    int waitTime = 0;
    static final int MAXIMUM_WAIT = 130;

    public void setFileTranserMode(boolean mode) {
        this.fileTranserMode = mode;
    }

    public boolean isActive() {
        return this.active;
    }

    public void updatePacket(DatagramPacket packet) {
        this.packet = packet;
        this.packetUpdated = true;
    }

    public UDPClientHandler(DatagramSocket conn, InetAddress inetAddress, int port, ArrayList<String> fileList, String dir, int id) {
        this.connection = conn;
        this.fileList = fileList;
        this.dir = dir;
        this.port = port;
        this.clientAddress = inetAddress;
        this.fileTranserMode = false;
        System.out.println("New thread(No." + id + ") processing on client address: " + inetAddress + ":" + port);
        this.id = id;
    }

    private String getMessage(DatagramPacket packet) {
        return new String(packet.getData(), packet.getOffset(), packet.getLength());
    }

    public String getConcatFileList() {
        StringBuilder sb = new StringBuilder();
        for (String filename : fileList)
            sb.append(filename + " ");
        return sb.toString();
    }

    private void sendMessage(Long val) throws IOException {
        byte[] buffer = ByteUtils.longToBytes(val);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, clientAddress, port);
        connection.send(packet);
    }

    private void sendMessage(String message) throws IOException {
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, clientAddress, port);
        connection.send(packet);
    }

    public void handlePacket(DatagramPacket packet) {
        String msg = getMessage(packet);
        System.out.println("Thread " + this.id + " Message handled: " + msg);
        try {
            if (msg.equals("index")) {
                sendMessage("Server List: " + getConcatFileList());
            } else if (msg.startsWith("get")){
                String[] parts = msg.split(" ");
                if (!parts[0].equals("get") || parts.length != 2)
                    sendMessage("402");
                else {
                    String filename = parts[1];
                    if (!fileList.contains(filename))
                        sendMessage("404");
                    else {
                        System.out.println("ok " + filename);
                        sendMessage("ok " + filename);
                        writeFileInit(filename);
                    }
                }
            } else {
                try {
                    ack = ByteUtils.bytesToInt(packet.getData());
                    System.out.println("Thread " + id + " receive ack " + ack + ", expected = " + seq);
                    this.ackReceived = true;
                    if (this.fileTranserMode) {
                        if (seq == ack) {
                            System.out.println("Packet " + (seq - 1) + " transfer success");
                            this.writeFilePacket();
                        } else if (seq == ack + 1){
                            System.out.println("Resending packet " + ack);
                            connection.send(filePacket);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Corrupted message, packet discarded");
                }
            }
        } catch (Exception e) {
            System.out.println("Error while communicating to client: ");
            e.printStackTrace();
        } finally {
            this.packetUpdated = false;
        }
    }



    /**
     * Generates a sequenced packet
     * @param seq sequence of packet
     * @param buffer raw packet data
     * @return
     */
    private byte[] generateSeqPacket(int seq, byte[] buffer, int bytes) {
        byte[] seqBytes = ByteUtils.intToBytes(seq);
        byte[] generated = new byte[4 + bytes];
        for (int i=0;i<4;i++)
            generated[i] = seqBytes[i];
        for (int i=0;i<bytes;i++)
            generated[4+i] = buffer[i];
        return generated;
    }

    private String convertToUTF8(byte[] buffer, int buflen) throws IOException{
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(buffer, 0, buflen);
        String str = bos.toString("UTF-8");
        return str;
    }

    public byte[] copyOfRange(byte[] src, int start, int end){
        int length = (end > src.length)? src.length-start: end-start;
        byte[] destArr = new byte[length];
        System.arraycopy(src, start, destArr, 0, length);
        return destArr;
    }

    private void writeFileInit(String filename) {
        this.ack = -1;
        this.ackReceived = false;
        this.waitTime = 0;
        File file = new File(dir + filename);
        try {
            fileInputStream = new FileInputStream(file);
            sendMessage(file.length());
        } catch (Exception e) {
            e.printStackTrace();
        }
        writeFilePacket();
    }

    private void writeFilePacket() {
        try {
            byte[] fBuffer = new byte[FILE_BUFFER_SIZE];//file buffer
            byte[] gBuffer;//generated buffer size
            bytes = fileInputStream.read(fBuffer);
            this.waitTime = 0;
            if (bytes == -1) {
                System.out.println("File transfer success.");
                this.waitTime = 0;
                this.setFileTranserMode(false);
                this.ack = -1;
                this.seq = 0;
                this.ackReceived = false;
                this.active = false;
            } else {
                byte[] dataBytes = copyOfRange(fBuffer, 0, bytes);
                gBuffer = generateSeqPacket(seq, dataBytes, bytes);
                filePacket = new DatagramPacket(gBuffer, gBuffer.length, clientAddress, port);
                connection.send(filePacket);
                this.setFileTranserMode(true);
                System.out.println("Initially sent packet " + seq);
                seq++;
            }
        } catch (Exception e) {
            System.out.println("Exception occurred when writing file to client");
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (this.active) {
                while (!this.packetUpdated) {
                    //wait
                    Thread.sleep(5);
                    if (this.fileTranserMode) {
                        this.waitTime += 5;
                        if (this.waitTime >= MAXIMUM_WAIT && (seq != ack)) {
                            System.out.println("Resending packet..." + this.ack + ", " + this.seq);
                            connection.send(filePacket);
                            this.waitTime = 0;
                        }
                    }
                }
                this.handlePacket(this.packet);
                this.packetUpdated = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
