package org.yamcs.yarch;


public class PartitioningSpec {
    public enum _type{
    	NONE, //no partition at all
    	TIME, //partition by time 
    	VALUE, //partition by value
    	TIME_AND_VALUE //partition by time and value (in this order)
    	};
    final public _type type;
    final public String timeColumn;
    final public String valueColumn;
    
    //this thing is not final because it is set from the TableDefinition when attaching the pspec. Probably should be changed.
    public DataType valueColumnType;
    
    
    public TimePartitionSchema timePartitioningSchema = TimePartitionSchema.getInstance("YYYY/MM"); 
    
    private PartitioningSpec (_type type, String timeColumn, String valueColumn) {
    	this.type=type;
    	this.timeColumn = timeColumn;
    	this.valueColumn = valueColumn;
    }
    
    public static PartitioningSpec noneSpec() {
    	PartitioningSpec pspec = new PartitioningSpec(_type.NONE, null, null);    	
    	return pspec;
    }
    
    public static PartitioningSpec valueSpec(String valueColumn) {
    	PartitioningSpec pspec = new PartitioningSpec(_type.VALUE, null, valueColumn);    	
    	return pspec;
    }
    
    public static PartitioningSpec timeSpec(String timeColumn) {
    	PartitioningSpec pspec = new PartitioningSpec(_type.TIME, timeColumn, null);
    	return pspec;
    }
    
    public static PartitioningSpec timeAndValueSpec(String timeColumn, String valueColumn) {
    	PartitioningSpec pspec = new PartitioningSpec(_type.TIME_AND_VALUE, timeColumn, valueColumn);    	
    	return pspec;
    }
    
    
    public void setTimePartitioningSchema(String schema) {
    	timePartitioningSchema = TimePartitionSchema.getInstance(schema);
    }
    
    @Override
    public String toString() {
        return "timeColumn: "+timeColumn+" valueColumn:"+valueColumn;
    }
}
