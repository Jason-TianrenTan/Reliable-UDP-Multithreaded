package com.company;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DataTruncation;
import java.util.*;
import java.net.Socket;
public class UDPClient {

    static final int BUFFER_SIZE = 4096;
    static final int FILE_BUFFER_SIZE = 4096;
    static final int ACK_BUFFER_SIZE = 4;
    String address;
    InetAddress hostAddress;
    int port;
    String dir = "./files/";
    DatagramSocket socket;

    public UDPClient(String address, int port) {
        this.address = address;
        this.port = port;
    }

    private String convertToUTF8(byte[] buffer, int buflen) throws IOException{
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(buffer, 0, buflen);
        String str = bos.toString("UTF-8");
        return str;
    }

    public void downloadFile(String filename) {
        System.out.println("Downloading file " + filename + " ...");
        byte[] fBuffer = new byte[FILE_BUFFER_SIZE + ACK_BUFFER_SIZE];

        int expectedSeq = 0;
        try {
            //Checking files
            File directory = new File(dir);
            if (!directory.exists() || !directory.isDirectory())
                directory.mkdir();
            File file = new File(dir + filename);
            file.createNewFile();

            FileOutputStream fos = new FileOutputStream(file);
            DatagramPacket lengthPacket = new DatagramPacket(new byte[Long.BYTES], Long.BYTES);
            socket.receive(lengthPacket);
            long fileSize = ByteUtils.bytesToLong(lengthPacket.getData());

            DatagramPacket filePacket = new DatagramPacket(fBuffer, fBuffer.length);
            DatagramPacket ackPacket;
            while (fileSize > 0) {
                System.out.println("filesize = " + fileSize);
                Random rand = new Random();
//                int sleep = 30 + rand.nextInt(170);
//                Thread.sleep(sleep);
                socket.receive(filePacket);
                fBuffer = filePacket.getData();
                //get seq and data
                ByteBuffer byteBuffer = ByteBuffer.wrap(fBuffer);
                byte[] seqBytes = new byte[ACK_BUFFER_SIZE], dataBuffer = new byte[FILE_BUFFER_SIZE];
                byteBuffer.get(seqBytes, 0, ACK_BUFFER_SIZE);
                byteBuffer.get(dataBuffer, 0, FILE_BUFFER_SIZE);
                int seq = ByteUtils.bytesToInt(seqBytes);
                boolean sendAck = true;
                System.out.println("Seq = " + seq + ", expected = " + expectedSeq);
                if (seq == expectedSeq) {
                    //success
                    System.out.println("Success on packet " + seq);
                    fos.write(dataBuffer, 0, (int)Math.min(fileSize, 4096));
                    fos.flush();
                    expectedSeq++;
                    fileSize -= dataBuffer.length;
                } else if (seq == expectedSeq - 1){//Duplicated packet
                    sendAck = false;
                } else {//Packet missing
                    System.out.println("Packet missed, request for resend");
                }

                //acknowledge server
                if (sendAck) {
                    System.out.println("Sending seq : " + expectedSeq);
                    ackPacket = new DatagramPacket(ByteUtils.intToBytes(expectedSeq), ACK_BUFFER_SIZE, hostAddress, port);
                    socket.send(ackPacket);
                }
            }
            fos.close();
            System.out.println("File successfully saved");
            System.out.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFileName(String response) {
        String[] parts = response.split(" ");
        return parts[1];
    }

    private String getMessage(DatagramPacket response) {
        return new String(response.getData(), response.getOffset(), response.getLength());
    }

    private void sendMessage(String message) throws IOException {
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, hostAddress, port);
        socket.send(packet);
    }

    public void start() {
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(10000);
            hostAddress = InetAddress.getByName(address);
            //get index

            byte[] sBuffer = "index".getBytes();
            DatagramPacket messagePacket = new DatagramPacket(sBuffer, sBuffer.length, hostAddress, port);
            socket.send(messagePacket);

            byte[] rBuffer = new byte[BUFFER_SIZE];
            DatagramPacket initalResponse = new DatagramPacket(rBuffer, rBuffer.length);
            socket.receive(initalResponse);
            System.out.println("List from server: " + getMessage(initalResponse));

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("Input query: ");
                String input = scanner.nextLine();
                sendMessage(input);
                Thread.sleep(50);

                while (input.startsWith("get")) {
                    rBuffer = new byte[BUFFER_SIZE];
                    DatagramPacket server_response = new DatagramPacket(rBuffer, rBuffer.length);
                    socket.receive(server_response);
                    String responseString = getMessage(server_response);
                    System.out.println(responseString);
                    if (responseString.startsWith("ok") || responseString.equals("402") || responseString.equals("404")) {
                        if (responseString.equals("402"))
                            System.out.println("Error input, please try again");
                        if (responseString.equals("404"))
                            System.out.println("File not found, please try again");
                        else if (responseString.startsWith("ok"))
                            downloadFile(getFileName(responseString));
                        break;
                    }
                }

                while (input.equals("index")) {
                    rBuffer = new byte[BUFFER_SIZE];
                    DatagramPacket server_response = new DatagramPacket(rBuffer, rBuffer.length);
                    socket.receive(server_response);
                    String responseString = getMessage(server_response);
                    if (responseString.startsWith("Server List:")) {
                        System.out.println(responseString);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
