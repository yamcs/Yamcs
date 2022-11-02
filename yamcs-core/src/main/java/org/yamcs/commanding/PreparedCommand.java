package org.yamcs.commanding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.Assignment;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.AssignmentInfo;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Commanding.CommandAssignment;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.ValueHelper;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

/**
 * Stores command source, binary attributes
 *
 */
public class PreparedCommand {
    private byte[] binary;
    private CommandId id;
    private MetaCommand metaCommand;
    private final UUID uuid; // Used in REST API as an easier single-field ID. Not persisted.

    // if true, the transmission constraints (if existing) will not be checked
    boolean disableTransmissionConstraints = false;

    boolean disableCommandVerifiers = false;

    List<CommandHistoryAttribute> attributes = new ArrayList<>();
    private Map<Argument, ArgumentValue> argAssignment; // Ordered from top entry to bottom entry
    private Set<String> userAssignedArgumentNames;

    // Verifier-specific configuration options (that override the MDB verifier settings)
    private Map<String, VerifierConfig> verifierConfig = new HashMap<>();

    // same as attributes but converted to parameters for usage in verifiers and transmission constraints
    private volatile ParameterValueList cmdParams;

    // column names to use when converting to tuple
    public final static String CNAME_GENTIME = StandardTupleDefinitions.GENTIME_COLUMN;
    public final static String CNAME_SEQNUM = StandardTupleDefinitions.SEQNUM_COLUMN;
    public final static String CNAME_ORIGIN = StandardTupleDefinitions.TC_ORIGIN_COLUMN;
    public final static String CNAME_USERNAME = "username";
    public final static String CNAME_BINARY = "binary";
    public final static String CNAME_CMDNAME = "cmdName";
    public final static String CNAME_SOURCE = "source";
    public final static String CNAME_ASSIGNMENTS = "assignments";
    public final static String CNAME_COMMENT = "comment";

    private static Set<String> reservedNames = new HashSet<>();
    static {
        reservedNames.add(CNAME_GENTIME);
        reservedNames.add(CNAME_SEQNUM);
        reservedNames.add(CNAME_ORIGIN);
        reservedNames.add(CNAME_USERNAME);
        reservedNames.add(CNAME_BINARY);
        reservedNames.add(CNAME_CMDNAME);
        reservedNames.add(CNAME_SOURCE);
        reservedNames.add(CNAME_ASSIGNMENTS);
    }

    public PreparedCommand(CommandId id) {
        this.id = id;
        uuid = UUID.randomUUID();
    }

    /**
     * Used for testing the uplinkers
     * 
     * @param binary
     */
    public PreparedCommand(byte[] binary) {
        this.setBinary(binary);
        uuid = UUID.randomUUID();
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

    public void setComment(String comment) {
        setStringAttribute(CNAME_COMMENT, comment);
    }

    public String getComment() {
        return getStringAttribute(CNAME_COMMENT);
    }

    public String getCmdName() {
        return id.getCommandName();
    }

    public String getStringAttribute(String attrname) {
        CommandHistoryAttribute a = getAttribute(attrname);
        if (a != null) {
            Value v = ValueUtility.fromGpb(a.getValue());
            if (v.getType() == Type.STRING) {
                return v.getStringValue();
            }
        }
        return null;
    }

    public CommandHistoryAttribute getAttribute(String name) {
        for (CommandHistoryAttribute a : attributes) {
            if (name.equals(a.getName())) {
                return a;
            }
        }
        return null;
    }

    public String getId() {
        return id.getGenerationTime() + "-" + id.getOrigin() + "-" + id.getSequenceNumber();
    }

    /**
     * String useful for logging. Contains command name and sequence number
     * 
     * @return
     */
    public String getLoggingId() {
        return id.getCommandName() + "-" + id.getSequenceNumber();
    }

    public String getOrigin() {
        return id.getOrigin();
    }

    public int getSequenceNumber() {
        return id.getSequenceNumber();
    }

    public String getCommandName() {
        return id.getCommandName();
    }

    public CommandId getCommandId() {
        return id;
    }

    public UUID getUUID() {
        return uuid;
    }

    static public CommandId getCommandId(Tuple t) {
    	CommandId cmdId;
    	
    	try {
           cmdId = CommandId.newBuilder().setGenerationTime((Long) t.getColumn(CNAME_GENTIME))
                    .setOrigin((String) t.getColumn(CNAME_ORIGIN)).setSequenceNumber((Integer) t.getColumn(CNAME_SEQNUM))
                    .setCommandName((String) t.getColumn(CNAME_CMDNAME)).build();
    	}
    	catch ( java.lang.NullPointerException e ) {
           cmdId = null; 
        }
    	
        return cmdId;
    }

    public Tuple toTuple() {
        TupleDefinition td = StandardTupleDefinitions.TC.copy();
        ArrayList<Object> al = new ArrayList<>();
        al.add(id.getGenerationTime());
        al.add(id.getOrigin());
        al.add(id.getSequenceNumber());
        al.add(id.getCommandName());

        if (getBinary() != null) {
            td.addColumn(CNAME_BINARY, DataType.BINARY);
            al.add(getBinary());
        }

        for (CommandHistoryAttribute a : attributes) {
            td.addColumn(a.getName(), ValueUtility.getYarchType(a.getValue().getType()));
            al.add(ValueUtility.getYarchValue(a.getValue()));
        }

        AssignmentInfo.Builder assignmentb = AssignmentInfo.newBuilder();
        if (getArgAssignment() != null) {
            for (Entry<Argument, ArgumentValue> entry : getArgAssignment().entrySet()) {
                assignmentb.addAssignment(Assignment.newBuilder()
                        .setName(entry.getKey().getName())
                        .setValue(ValueUtility.toGbp(entry.getValue().getEngValue()))
                        .setUserInput(userAssignedArgumentNames.contains(entry.getKey().getName()))
                        .build());
            }
        }
        td.addColumn(CNAME_ASSIGNMENTS, DataType.protobuf("org.yamcs.cmdhistory.protobuf.Cmdhistory$AssignmentInfo"));
        al.add(assignmentb.build());

        return new Tuple(td, al.toArray());
    }

    public List<CommandAssignment> getAssignments() {
        List<CommandAssignment> assignments = new ArrayList<>();
        if (getArgAssignment() != null) {
            for (Entry<Argument, ArgumentValue> entry : getArgAssignment().entrySet()) {
                assignments.add(CommandAssignment.newBuilder()
                        .setName(entry.getKey().getName())
                        .setValue(ValueUtility.toGbp(entry.getValue().getEngValue()))
                        .setUserInput(userAssignedArgumentNames.contains(entry.getKey().getName()))
                        .build());
            }
        }
        return assignments;
    }

    public void setBinary(byte[] b) {
        this.binary = b;
    }

    public String getUsername() {
        CommandHistoryAttribute cha = getAttribute(CNAME_USERNAME);
        if (cha == null) {
            return null;
        }

        return cha.getValue().getStringValue();
    }

    public List<CommandHistoryAttribute> getAttributes() {
        return attributes;
    }

    public ParameterValueList getAttributesAsParameters(XtceDb xtcedb) {
        if (cmdParams != null) {
            return cmdParams;
        }
        ParameterValueList pvlist = new ParameterValueList();

        for (CommandHistoryAttribute cha : attributes) {
            String fqn = XtceDb.YAMCS_CMD_SPACESYSTEM_NAME + "/" + cha.getName();
            Parameter p = xtcedb.getParameter(fqn);

            if (p == null) {
                // if it was required in the algorithm, it would be already in the system parameter db
                continue;
            }

            ParameterValue pv = new ParameterValue(p);
            pv.setEngValue(ValueUtility.fromGpb(cha.getValue()));
            pvlist.add(pv);
        }
        cmdParams = pvlist;
        return cmdParams;
    }

    public static PreparedCommand fromTuple(Tuple t, XtceDb xtcedb) {
    	PreparedCommand pc;
    	CommandId cmdId = getCommandId(t);
        if(cmdId != null) {
            pc = new PreparedCommand(cmdId);
            pc.setMetaCommand(xtcedb.getMetaCommand(cmdId.getCommandName()));
            for (int i = 0; i < t.size(); i++) {
                ColumnDefinition cd = t.getColumnDefinition(i);
                String name = cd.getName();
                if (CNAME_GENTIME.equals(name) || CNAME_ORIGIN.equals(name) || CNAME_SEQNUM.equals(name)
                     || CNAME_ASSIGNMENTS.equals(name) || CNAME_COMMENT.equals(name)) {
                    continue;
                }
                Value v = ValueUtility.getColumnValue(cd, t.getColumn(i));
                CommandHistoryAttribute a = CommandHistoryAttribute.newBuilder().setName(name)
                        .setValue(ValueUtility.toGbp(v)).build();
                pc.attributes.add(a);
            }
            pc.setBinary((byte[]) t.getColumn(CNAME_BINARY));
            if (t.hasColumn(CNAME_COMMENT)) {
                String comment = (String) t.getColumn(CNAME_COMMENT);
                pc.setComment(comment);
            }

            AssignmentInfo assignments = (AssignmentInfo) t.getColumn(CNAME_ASSIGNMENTS);
            if (assignments != null) {
                pc.argAssignment = new LinkedHashMap<>();
                for (Assignment assignment : assignments.getAssignmentList()) {
                    Argument arg = findArgument(pc.getMetaCommand(), assignment.getName());
                    Value v = ValueUtility.fromGpb(assignment.getValue());
                    ArgumentValue argv = new ArgumentValue(arg);
                    argv.setEngValue(v);
                    pc.argAssignment.put(arg, argv);
                }
            }
        } else {
        	pc = null;
        }
        
        return pc;
    }

    private static Argument findArgument(MetaCommand mc, String name) {
        Argument arg = mc.getArgument(name);
        if (arg == null && mc.getBaseMetaCommand() != null) {
            arg = findArgument(mc.getBaseMetaCommand(), name);
        }
        return arg;
    }

    public static PreparedCommand fromCommandHistoryEntry(CommandHistoryEntry che) {
    	PreparedCommand pc;
    	CommandId cmdId = che.getCommandId();
        if(cmdId != null) {
            pc = new PreparedCommand(cmdId);

            pc.attributes = che.getAttrList();
        } else {
        	pc = null;
        }
        
        return pc;
    }

    public void setStringAttribute(String name, String value) {
        int i;
        for (i = 0; i < attributes.size(); i++) {
            CommandHistoryAttribute a = attributes.get(i);
            if (name.equals(a.getName())) {
                break;
            }
        }
        CommandHistoryAttribute a = CommandHistoryAttribute.newBuilder().setName(name)
                .setValue(ValueHelper.newValue(value)).build();
        if (i < attributes.size()) {
            attributes.set(i, a);
        } else {
            attributes.add(a);
        }
    }

    public void addStringAttribute(String name, String value) {
        CommandHistoryAttribute a = CommandHistoryAttribute.newBuilder().setName(name)
                .setValue(ValueHelper.newValue(value)).build();
        attributes.add(a);
    }

    public void addAttribute(CommandHistoryAttribute cha) {
        String name = cha.getName();
        if (CNAME_GENTIME.equals(name) || CNAME_ORIGIN.equals(name) || CNAME_SEQNUM.equals(name)
                || CNAME_ASSIGNMENTS.equals(name)) {
            throw new IllegalArgumentException("Cannot use '" + name + "' as a command attribute");
        }
        attributes.add(cha);
    }

    public byte[] getBinary() {
        return binary;
    }

    public void setUsername(String username) {
        setStringAttribute(CNAME_USERNAME, username);
    }

    public MetaCommand getMetaCommand() {
        return metaCommand;
    }

    public void setMetaCommand(MetaCommand cmd) {
        this.metaCommand = cmd;
    }

    public void setArgAssignment(Map<Argument, ArgumentValue> argAssignment, Set<String> userAssignedArgumentNames) {
        this.argAssignment = argAssignment;
        this.userAssignedArgumentNames = userAssignedArgumentNames;
    }

    public ArgumentValue getArgAssignment(Argument arg) {
        return argAssignment.get(arg);
    }

    public Map<Argument, ArgumentValue> getArgAssignment() {
        return argAssignment;
    }

    @Override
    public String toString() {
        return "PreparedCommand(" + uuid + ", " + StringConverter.toString(id) + ")";
    }

    public void disableTransmissionContraints(boolean b) {
        disableTransmissionConstraints = b;
    }

    /**
     * 
     * @return true if the transmission constraints have to be disabled for this command
     */
    public boolean disableTransmissionContraints() {
        return disableTransmissionConstraints;
    }

    /**
     * 
     * @return true if the command verifiers have to be disabled for this command
     */
    public boolean disableCommandVerifiers() {
        return disableCommandVerifiers;
    }

    public void disableCommandVerifiers(boolean b) {
        disableCommandVerifiers = b;
    }

    public void addVerifierConfig(String name, VerifierConfig verifierConfig) {
        this.verifierConfig.put(name, verifierConfig);
    }

    /**
     * @return a list of command verifiers options overriding MDB settings.
     */
    public Map<String, VerifierConfig> getVerifierOverride() {
        return verifierConfig;
    }
}
