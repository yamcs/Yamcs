package org.yamcs.commanding;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.archive.TcUplinkerAdapter;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.Value;


/** 
 * This class is to keep track of a command binary and source included
 * @author nm
 *
 */
public class PreparedCommand {
	private byte[] binary;
	private CommandId id;
	List<CommandHistoryAttribute> attributes=new ArrayList<CommandHistoryAttribute>();
	
	//column names to use when converting to tuple
	public final static String CNAME_GENTIME = "gentime";
	public final static String CNAME_SEQNUM = "seqNum";
	public final static String CNAME_ORIGIN = "origin";
	public final static String CNAME_USERNAME = "username";
	public final static String CNAME_BINARY = "binary";
	public final static String CNAME_CMDNAME = "cmdName";
	public final static String CNAME_SOURCE = "source";
    
	public PreparedCommand(CommandId id) {
		this.id=id;
	}
	
	/**
	 * Used for testing the uplinkers
	 * @param binary
	 */
	public PreparedCommand(byte[] binary) {
		this.setBinary(binary);
	}
	
	public long getGenerationTime() {
		return id.getGenerationTime();
	}

    public void setSource(String source) {
        setStringAttribute(CNAME_SOURCE, source);
    }
    
    public String getSource() {
        return getStringAttribute(CNAME_SOURCE);
    }

    public String getCmdName() {
        return id.getCommandName();
    }

    public String getStringAttribute(String attrname) {
        CommandHistoryAttribute a=getAttribute(attrname);
        Value v = a.getValue();
        if((a!=null) && (v.getType()==Value.Type.STRING)) return v.getStringValue();
        return null;
    }
    
    public CommandHistoryAttribute getAttribute(String name) {
        for(CommandHistoryAttribute a:attributes) {
            if(name.equals(a.getName())) return a;
        }
        return null;
    }
    
    public CommandId getCommandId() {
        return id;
    }
    
    static public CommandId getCommandId(Tuple t) {
        CommandId cmdId=CommandId.newBuilder()
            .setGenerationTime((Long)t.getColumn(CNAME_GENTIME))
            .setOrigin((String)t.getColumn(CNAME_ORIGIN))
            .setSequenceNumber((Integer)t.getColumn(CNAME_SEQNUM))
            .setCommandName((String)t.getColumn(CNAME_CMDNAME))
            .build();
        return cmdId;
    }

    
    
    public Tuple toTuple() {
        TupleDefinition td=TcUplinkerAdapter.TC_TUPLE_DEFINITION.copy();
        ArrayList<Object> al=new ArrayList<Object>();
        al.add(id.getGenerationTime());
        al.add(id.getOrigin());
        al.add(id.getSequenceNumber());
        al.add(id.getCommandName());
        
        
        if(getBinary()!=null) {
            td.addColumn(CNAME_BINARY, DataType.BINARY);
            al.add(getBinary());
        }
        
        for(CommandHistoryAttribute a:attributes) {
            td.addColumn(a.getName(), ValueUtility.getYarchType(a.getValue()));
            al.add(ValueUtility.getYarchValue(a.getValue()));
        }
        Tuple t =  new Tuple(td, al.toArray());
        return t;
    }
    public void setBinary(byte[] b) {
		this.binary =b;
	}

    public String getUsername() {
    	CommandHistoryAttribute cha = getAttribute(CNAME_USERNAME);
    	if(cha==null) return null;
    	
        return cha.getValue().getStringValue();
    }

    public List<CommandHistoryAttribute> getAttirbutes() {
    	return attributes;
    }
    
    public static PreparedCommand fromTuple(Tuple t) {
        CommandId cmdId=getCommandId(t);
        PreparedCommand pc=new PreparedCommand(cmdId);
        for(int i=0;i<t.size();i++) {
            ColumnDefinition cd=t.getColumnDefinition(i);
            String name=cd.getName();
            if(CNAME_GENTIME.equals(name) || CNAME_ORIGIN.equals(name) || CNAME_SEQNUM.equals(name)) continue;
            CommandHistoryAttribute a=CommandHistoryAttribute.newBuilder()
                .setName(name)
                .setValue(ValueUtility.getColumnValue(cd, t.getColumn(i)))
                .build();
            pc.attributes.add(a);
        }
        pc.setBinary((byte[])t.getColumn(CNAME_BINARY));
        
        return pc;
    }
    
    public void setStringAttribute(String name, String value) {
    	int i;
    	for(i =0; i<attributes.size(); i++) {
    		CommandHistoryAttribute a = attributes.get(i);
            if(name.equals(a.getName())) break;
        }
        CommandHistoryAttribute a=CommandHistoryAttribute.newBuilder()
                .setName(name)
                .setValue(ValueUtility.getStringValue(value))
                .build();
        if(i<attributes.size()) {
            attributes.set(i, a);
        } else {
        	attributes.add(a);
        }
     }
    
    public void addStringAttribute(String name, String value) {
        CommandHistoryAttribute a=CommandHistoryAttribute.newBuilder()
            .setName(name)
            .setValue(ValueUtility.getStringValue(value))
            .build();
        attributes.add(a);
    }

	public byte[] getBinary() {
		return binary;
	}

	public void setUsername(String username) {
		setStringAttribute(CNAME_USERNAME, username);
	}	
}