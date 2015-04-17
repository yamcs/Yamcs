package org.yamcs.xtce;

import java.io.PrintStream;
import java.util.ArrayList;

/**
 * A list of raw parameters, parameter segments, stream segments, containers, or container segments.  
 * Sequence containers may inherit from other sequence containers; when they do, the sequence in the parent 
 * SequenceContainer is 'inherited' and if the location of entries in the child sequence is not specified,
 * it is assumed to start where the parent sequence ended.  Parent sequence containers may be marked as "abstract".  
 * The idle pattern is part of any unallocated space in the Container.
*/
public class SequenceContainer extends Container {
	private static final long serialVersionUID = 2L;
	public SequenceContainer(String name) {
		super(name);
	}

	ArrayList<SequenceEntry> entryList =new ArrayList<SequenceEntry>();
	transient NameReference baseContainerRef;
	
	SequenceContainer baseContainer;
	MatchCriteria restrictionCriteria;
	
	
	public void setBaseContainerReference(NameReference ref) {
        this.baseContainerRef = ref;
    }
    
	
	public SequenceContainer getBaseContainer() {
		return baseContainer;
	}
	
	
	public void setBaseContainer(SequenceContainer baseContainer) {
		this.baseContainer = baseContainer;
	}
	
	public MatchCriteria getRestrictionCriteria() {
		return restrictionCriteria;
	}
	
	/**
	 * Add single entry to list of entries
	 * @param entry Entry to be added
	 */
	public void addEntry(SequenceEntry entry) {
	    getEntryList().add(entry);
	    // set the entries position in the list
	    entry.setIndex( getEntryList().size() - 1 );
	}
	
	public void setRestrictionCriteria(MatchCriteria restrictionCriteria) {
		this.restrictionCriteria = restrictionCriteria;
	}
	
	
	
	public void print(PrintStream out) {
		out.print("SequenceContainer name: "+name+((sizeInBits>-1)?", sizeInBits: "+sizeInBits:""));
		if(getAliasSet()!=null) out.print(", aliases: "+getAliasSet());
		out.println();
		if(baseContainer!=null) {
			out.print("\tbaseContainer: '"+baseContainer.getQualifiedName());
			out.println("', restrictionCriteria: "+restrictionCriteria);
		}
		for(SequenceEntry se:getEntryList()) {
			out.println("\t\t"+se);
		}
	}
    public void setEntryList(ArrayList<SequenceEntry> entryList) {
        this.entryList = entryList;
    }
    
    public ArrayList<SequenceEntry> getEntryList() {
        return entryList;
    }
    
    @Override
    public String toString() {
        return "SequenceContainer(name="+name+")";
    }
}
