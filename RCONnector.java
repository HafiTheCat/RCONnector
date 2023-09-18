package com.company;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class RCONnector {
    private static final class InstanceHolder { static final RCONnector INSTANCE = new RCONnector(); }
    private TCPconnector connector=null;
    private RCONnector() {}
    public static RCONnector getInstance () {
        return InstanceHolder.INSTANCE;
    }

    public void createSocket(String address, String port) throws IOException {
        this.connector = new TCPconnector(address, port);
    }

    public boolean authenticate(String password) throws IOException {
        Packet authPacket = new Packet(1, PacketType.LOGIN, password);
        System.out.println("[RCON] Authentication in progress...");
        this.connector.sendPacket(authPacket);
        Packet[] inpacket = connector.recievePacket();
        System.out.println("AuthAnswer: " + inpacket[0].toString());
        if(inpacket[0].getType() == PacketType.FAILED || inpacket[0].getType() != PacketType.COMMAND){
            System.out.println("[RCON] Authentication Failed!");
            return false;
        }
        if(inpacket[0].getType() == PacketType.COMMAND || inpacket[0].getRequestID() == authPacket.getRequestID()){
            System.out.println("[RCON] Authentication Succeeded!");
            return true;
        }
        System.out.println("[RCON] Couldn't Authenticate!");
        return false;
    }

    public String sendRCON(String command) throws IOException {
        Packet packet = new Packet(1, PacketType.COMMAND, command);
        System.out.println("[RCON] Sending in progress...");
        this.connector.sendPacket(packet);
        Packet[] inpacket = connector.recievePacket();
        System.out.println("[RCON] Sending Succeeded! RecievedPackets=" + inpacket.length);
        String output = "";
        for(Packet p : inpacket){
            output += p.getPayload();
        }
        return output;
    }
}

enum PacketType {
    LOGIN(3), COMMAND(2), MULTIPACKET(0), FAILED(-1);
    public final int value;
    PacketType(int value){ this.value=value; }
    public static PacketType getType(int value){
        return switch (value) {
            case 3 -> LOGIN;
            case 2 -> COMMAND;
            case 0 -> MULTIPACKET;
            default -> FAILED;
        };
    }
}

class TCPconnector {
    private InetAddress address = null;
    private Socket socket;
    private ByteArrayOutputStream outgoingByteStream;
    private DataInputStream dataInputStream;

    public TCPconnector(String address, String port) throws IOException {
        this.address = InetAddress.getByName(address);
        this.socket = new Socket(address, Integer.parseInt(port));
        outgoingByteStream = new ByteArrayOutputStream();
        dataInputStream = new DataInputStream(new BufferedInputStream(this.socket.getInputStream()));
    }

    public void sendPacket(Packet packet) throws IOException {
        outgoingByteStream.write(packet.getRawPacket());
        outgoingByteStream.flush();
        outgoingByteStream.writeTo(socket.getOutputStream());
        outgoingByteStream.close();
        outgoingByteStream.reset();
        if(packet.getType()==PacketType.COMMAND){
            outgoingByteStream.write(new Packet(1, PacketType.COMMAND, "100").getRawPacket());
            outgoingByteStream.flush();
            outgoingByteStream.writeTo(socket.getOutputStream());
            outgoingByteStream.close();
            outgoingByteStream.reset();
        }
    }

    public Packet[] recievePacket() throws IOException {
        MessageReciever messageReciever = new MessageReciever(this.socket);
        messageReciever.start();
        try {
            messageReciever.join();
        } catch (InterruptedException e) {
            System.out.println("Thread not finished");
        }
        System.out.println("Thread finished");
        return messageReciever.packetarray;
    }

    class MessageReciever extends Thread {
        Packet[] packetarray;
        Socket socket;
        MessageReciever(Socket socket){
            this.socket = socket;
        }

        public void run(){
            Packet inPacket = null;
            ArrayList<Packet> incomingPackets = new ArrayList<>();
            DataInputStream dataInputStream = null;
            try {
                dataInputStream = new DataInputStream(new BufferedInputStream(this.socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            do{
                dataInputStream.mark(1);
                try{
                    if(dataInputStream.readByte() != 0x0){
                        dataInputStream.reset();
                        int packetLength = Integer.reverseBytes(dataInputStream.readInt());
                        int packetRequestID = Integer.reverseBytes(dataInputStream.readInt());
                        int packetType = Integer.reverseBytes(dataInputStream.readInt());
                        String payload = "";
                        while(true){
                            byte b = dataInputStream.readByte();
                            if(b == 0x0)
                                break;
                            payload += (char)b;
                        }

                        inPacket = new Packet(packetLength, packetRequestID, PacketType.getType(packetType),payload);
                        incomingPackets.add(inPacket);
                    }
                }catch(IOException e){ e.printStackTrace(); }
            } while(Objects.requireNonNull(inPacket).getRawLength()==4110);
            Packet[] packetarray = new Packet[incomingPackets.size()];
            this.packetarray = incomingPackets.toArray(packetarray);
        }
    }
}

class Packet{
    private final int rawLength;
    private final int length;
    private final int requestID;
    private final PacketType type;
    private final String payload;
    private final byte[] rawPacket;

    Packet(int requestID, PacketType type, String payload){
        this.rawLength = payload.length() + 14;
        this.length = payload.length() + 10;
        this.requestID = requestID;
        this.type = type;
        this.payload = payload;
        this.rawPacket = createPacket();
    }

    Packet(int length, int requestID, PacketType type, String payload){
        this.rawLength = length + 4;
        this.length = length;
        this.requestID = requestID;
        this.type = type;
        this.payload = payload;
        this.rawPacket = createPacket();
    }

    private byte[] createPacket(){
        int currentIndex = 0;
        byte[] packet = new byte[this.rawLength];
        // Little Endian int Length
        for(byte b : intToBytes(Integer.reverseBytes(this.length))){
            packet[currentIndex] = b;
            currentIndex++;
        }
        // Little Endian int requestID
        for(byte b : intToBytes(Integer.reverseBytes(this.requestID))){
            packet[currentIndex] = b;
            currentIndex++;
        }
        // Little Endian int packetType
        for(byte b : intToBytes(Integer.reverseBytes(this.type.value))){
            packet[currentIndex] = b;
            currentIndex++;
        }
        // Payload
        for(byte b : this.payload.getBytes(StandardCharsets.ISO_8859_1)){
            //if(b == 0xA7)
            //   continue;
            //System.out.print(String.format("%X ",b));
            packet[currentIndex] = b;
            currentIndex++;
        }
        // Null-terminator for string
        packet[currentIndex] = 0x0;
        // 1-byte pad
        packet[++currentIndex] = 0x0;
        return packet;
    }

    private byte[] intToBytes(final int i ) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(i);
        return bb.array();
    }

    @Override
    public String toString() {
        return "Packet{" +
                "rawlength=" + rawLength +
                ", length=" + length +
                ", requestID=" + requestID +
                ", type=" + type +
                ", payload='" + payload + '\'' +
                ", rawPacket=" + Arrays.toString(rawPacket) +
                '}';
    }

    public int getRawLength() { return rawLength; }
    public int getLength() { return length; }
    public int getRequestID() { return requestID; }
    public PacketType getType() { return type; }
    public String getPayload() { return payload; }
    public byte[] getRawPacket() { return rawPacket; }
}