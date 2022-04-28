package io.github.aleksas.arduino.simplerpc;

import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * Helper functions.
 */
public class Utils {
    /**
     * Split ByteBuffer by separator byte.
     * @param buffer Byte buffer.
     * @param separator Separator byte.
     * @return Iterator of byte buffers split by separatir byte. 
     */
    public static Iterable<ByteBuffer> Split(ByteBuffer buffer, byte separator) {        
        return new Iterable<ByteBuffer>() {            
            ByteBuffer slice = buffer.slice();
            int start_index = 0;

            @Override
            public Iterator<ByteBuffer> iterator() {
                return new Iterator<ByteBuffer>() {    
                    @Override
                    public boolean hasNext() {
                        var tmp = slice.slice();
                        while(tmp.hasRemaining()) {
                            if (tmp.get() != separator) {
                                return true;
                            }
                        }
                        return false;
                    }
    
                    @Override
                    public ByteBuffer next() {
                        boolean collecting = false;
                        while(slice.hasRemaining()) {
                            var value = slice.get();
                
                            if (collecting) {
                                if (value == separator) {
                                    var ret = slice.slice(start_index, slice.position() - start_index - 1);
                                    return ret;
                                }
                            } else {
                                if (value != separator) {
                                    collecting = true;
                                    start_index = slice.position() - 1;
                                }
                            }   
                            
                            if (value == separator) {
                                start_index = slice.position();
                            }
                        }

                        if (collecting) {
                            return slice.slice(start_index, slice.limit() - start_index);
                        } else {
                            throw new RuntimeException();
                        }
                    }    
                };
            }
        };
    }
}