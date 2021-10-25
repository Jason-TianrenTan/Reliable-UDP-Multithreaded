package com.company;

import java.net.InetAddress;

public class Main {
    static final int PORT = 8086;
    public static void main(String[] args) {

        new UDPClient("127.0.0.1", PORT).start();
    }
}
