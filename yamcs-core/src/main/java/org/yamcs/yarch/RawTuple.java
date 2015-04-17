package org.yamcs.yarch;

import java.util.Comparator;

import com.google.common.primitives.UnsignedBytes;


public class RawTuple implements Comparable<RawTuple>{
	int index;//used for sorting tuples with equals keys
    public byte[] key;
	public byte[] value;
    static Comparator<byte[]> bytesComparator=UnsignedBytes.lexicographicalComparator();
    
    public RawTuple(byte[] key, byte[] value, int index) {
		this.key = key;
        this.value = value;
        this.index=index;
    }
    
    @Override
    public int compareTo(RawTuple o) {
        int c = bytesComparator.compare(key, o.key);
        if(c!=0) return c;
        return (index-o.index);
    }
}