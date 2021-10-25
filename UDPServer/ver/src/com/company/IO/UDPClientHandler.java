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
    private Logger errorLogger;
    byte[] buffer;
    int port;
    int id;
    ArrayList<String> fileList;
    String dir;
    InetAddress clientAddress;
    DatagramPacket packet;
    public UDPClientHandler(DatagramSocket conn, InetAddress inetAddress, int port, ArrayList<String> fileList, String dir, DatagramPacket packet, int id) {
        this.connection = conn;
        this.fileList = fileList;
        this.dir = dir;
        this.buffer = new byte[BUFFER_SIZE];
        this.port = port;
        this.clientAddress = inetAddress;
        this.packet = packet;
        errorLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
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
        buffer = ByteUtils.longToBytes(val);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, clientAddress, port);
        connection.send(packet);
    }

    private void sendMessage(String message) throws IOException {
        buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, clientAddress, port);
        connection.send(packet);
    }

    public void handlePacket(DatagramPacket packet) {
        String msg = getMessage(packet);
        System.out.println("Thread " + this.id + " Message handled: " + msg);
        try {
            if (msg.equals("index")) {
                sendMessage(getConcatFileList());
            } else {
                String[] parts = msg.split(" ");
                if (!parts[0].equals("get") || parts.length != 2)
                    sendMessage("Error input, please try again");
                else {
                    String filename = parts[1];
                    if (!fileList.contains(filename))
                        sendMessage("File not found, please try again");
                    else {
                        sendMessage("ok " + filename);
                        writeFile(filename);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error while communicating to client: ");
            e.printStackTrace();
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

    public byte[] copyOfRange(byte[] srcArr, int start, int end){
        int length = (end > srcArr.length)? srcArr.length-start: end-start;
        byte[] destArr = new byte[length];
        System.arraycopy(srcArr, start, destArr, 0, length);
        return destArr;
    }

    private void writeFile(String filename) {
        File file = new File(dir + filename);
        byte[] fileResponseBuffer = new byte[BUFFER_SIZE];
        DatagramPacket ackResponse = new DatagramPacket(fileResponseBuffer, fileResponseBuffer.length);
        try (FileInputStream fileInputStream = new FileInputStream(file)){
            byte[] fBuffer = new byte[FILE_BUFFER_SIZE];//file buffer
            byte[] gBuffer;//generated buffer size
            int bytes = 0;
            int seq = 0;
            sendMessage(file.length());

            while ((bytes = fileInputStream.read(fBuffer)) != -1) {
                byte[] dataBytes = copyOfRange(fBuffer, 0, bytes);
                gBuffer = generateSeqPacket(seq, dataBytes, bytes);
                DatagramPacket filePacket = new DatagramPacket(gBuffer, gBuffer.length, clientAddress, port);
                connection.send(filePacket);
                seq++;
                //ack
                int ack = -1;
                boolean ackReceived = false;
                while (true) {
                    connection.receive(ackResponse);
                    ackReceived = true;
                    ack = ByteUtils.bytesToInt(ackResponse.getData());
                    System.out.println("Thread " + id + " receive ack " + ack + ", expected = " + seq);
                    if ((ack == seq) && ackReceived) {
                        //success
                        System.out.println("Packet " + seq + " success");
                        break;
                    } else {
                        //either wrong packet or packet dropped
                        System.out.println("Resending packet...");
                        connection.send(filePacket);
                    }
                }

            }
            fileInputStream.close();
            System.out.println("finish");
        } catch (Exception e) {
            System.out.println("Exception occurred when writing file to client");
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println("Thread " + this.id + " running...");
        this.handlePacket(packet);
    }
}
