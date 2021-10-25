package com.company;

import com.company.Data.FileData;
import com.company.IO.UDPServer;

public class Main {

    final static String INITIAL_DIR = "./files/";
    final static int PORT = 8086;
    final static int poolSize = 32;
    public static void main(String[] args) {
	// write your code here
        FileData files = new FileData(INITIAL_DIR);
        new UDPServer(PORT, files, poolSize).start();
    }
}
