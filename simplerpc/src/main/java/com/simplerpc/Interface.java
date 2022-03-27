package com.simplerpc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.simplerpc.serial.Serial;

public class Interface  implements AutoCloseable {
    private static String PROTOCOL = "simpleRPC";
    private static int LIST_REQUEST = 0xff;
    
    public static int[] VERSION = {3, 0, 0};

    public int baudrate = 9600;
    public int wait = 2;
    public boolean autoconnect = true;
    public InputStream load = null; 
    public Serial connection = null; 
    public Device device = null; 

    public Interface(String device, int baudrate, int wait, boolean autoconnect, InputStream load) throws Exception {
        this.wait = wait;
        this.connection = new Serial(device, true, baudrate); //serial_for_url(device, true, baudrate);
        this.device = new Device();

        if (autoconnect)
            this.open(load);
    }

    private static void AssertProtocol(String protocol) {
        if (!protocol.equals(PROTOCOL))
            throw new RuntimeException("Invalid protocol header");
    }

    private static void AssertVersion(int[] version) {
        if (!Arrays.equals(version, VERSION)) {
            String vesionString = IntStream.of(version).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining(",")) ;
            String refVesionString = IntStream.of(VERSION).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining(","));
            throw new RuntimeException(String.format("Version mismatch (device: %s, client: %s)", refVesionString, vesionString)); 
        }
    }

    public boolean isOpen() {
        return this.connection.isOpen();
    }

    private void write(String format, Object value) throws IOException {
        try (var stream  = connection.getOutputStream()) {
            try (var channel = Channels.newChannel(stream)) {
                var buffer = ByteBufferStruct.Pack(format, new Object[]{ value });

                try {
                    channel.write(buffer);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // TODO Get rid of sleep
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private String readByteString() throws IOException {
        return Io.ReadByteString(connection.getInputStream());
    }

    /**
     * Read a return value from a remote procedure call.
     * @param obj_type Return type.
     * @return Return type.
     * @throws IOException
     * @throws Exception
     */
    private Object read(Object obj_type) throws IOException {
        return Io.Read(connection.getInputStream(), device.endianness, device.size_t, obj_type);
    }

    /**
     * Initiate a remote procedure call, select the method.
     * @param index Method index.
     * @throws IOException
     * @throws Exception
     */
    private void select(int index) throws IOException {
        this.write("B", index);
    } 

    private void load(InputStream handle) {
        var mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        // Interface iterface = mapper.readValue(handle, Interface.class);
        // TODO: implement yaml loader
        throw new RuntimeException("Not implementd");
    }

    /**
     * Get remote procedure call methods.
     * @return Methods.
     * @throws IOException
     * @throws Exception
     */
    private void getMethods() throws IOException {
        select(LIST_REQUEST);  
        
        AssertProtocol(readByteString());
        device.protocol = PROTOCOL;

        var version = new int[]{
            ((Integer)read("B")).intValue(),
            ((Integer)read("B")).intValue(),
            ((Integer)read("B")).intValue()
        };
        AssertVersion(version);

        device.version = VERSION;

        var endianness_size = readByteString();
        device.endianness = endianness_size.charAt(0);
        device.size_t = endianness_size.charAt(1);

        for (int i = 0;; i++) {
            var line = readByteString();
            if (line.isEmpty())
                break;

            var buffer = ByteBuffer.wrap(line.getBytes());
            
            var method = Protocol.ParseLine(i, buffer);
            device.methods.put(method.name, method);
        }
    }

    public void open() throws Exception {
        open(null);
    }

    public void open(InputStream handle) throws Exception {
        try {
            Thread.sleep(wait * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        connection.open();

        if (handle != null)
            load(handle);
        else
            getMethods();
            
        for (var method : device.methods.values()){
            // TODO: generate function.
        }
            // setattr(
            //     self, method['name'], MethodType(make_function(method), self))
    }

    @Override
    public void close() throws Exception {
        if (connection != null)
            connection.close();
    }
}
