package com.company.IO;
import com.company.Data.FileData;

import java.net.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

public class UDPServer {

    ExecutorService threadPool;
    Logger errorLogger;
    int port;
    DatagramSocket socket;
    FileData fileData;
    byte[] buffer;
    public UDPServer(int port, FileData fileData, int poolSize) {
        errorLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        this.port = port;
        this.fileData = fileData;
        threadPool = Executors.newFixedThreadPool(poolSize);
    }
    
    private void printFiles() {
        ArrayList<String> filenames = fileData.getFiles();
        for (String filename : filenames)
            System.out.println(filename);
        System.out.println();
    }

    public void start() {
        try {
            //configure
            System.out.println("Server file location: " + this.fileData.getDir());
            boolean configured = false;
            while (!configured) {
                System.out.print("Do you wish to change location?(Y/N)\t");
                Scanner scanner = new Scanner(System.in);
                String input = scanner.nextLine();
                if (input.equals("Y")) {
                    System.out.print("Please input new directory: ");
                    String nDir = scanner.nextLine();
                    configured = fileData.setDir(nDir);
                } else if (!input.equals("N")) {
                    System.out.println("Illegal input, please try again");
                } else {
                    configured = true;
                    fileData.initialize();
                }
            }
            //initialize
            socket = new DatagramSocket(this.port);
            //
            int threadID = 0;
            System.out.println("Server started at localhost:" + this.port);
            printFiles();
            while (true) {
                buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    Thread.sleep(100);
                    socket.receive(packet);
                    InetAddress clientAddress = packet.getAddress();
                    int clientPort = packet.getPort();
                    threadPool.execute(new UDPClientHandler(socket, clientAddress, clientPort, fileData.getFiles(), fileData.getDir(), packet, threadID++));
                } catch (Exception e) {
                }
            }
        } catch (IOException e) {
            errorLogger.log(Level.SEVERE, "Server fatal error : ", e);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

}
