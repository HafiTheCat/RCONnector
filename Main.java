package com.company;

import java.io.IOException;

public class Main {
    public static void main(String[] args){
        RCONnector rconInstance = RCONnector.getInstance();
        try {
            rconInstance.createSocket("0.0.0.0","25575");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            rconInstance.authenticate("test");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            System.out.println("Server Response: " + rconInstance.sendRCON("kill <your_playername>"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        /*for(Packet p :inpacket){
            System.out.println("===============================");
            System.out.println("RawLength=" + p.getRawLength());
            System.out.println("Length=" + p.getLength());
            System.out.println("RequestID=" + p.getRequestID());
            System.out.println("Type=" + p.getType());
            System.out.println("PayloadLength=" + p.getPayload().length());
        }*/

    }
}

