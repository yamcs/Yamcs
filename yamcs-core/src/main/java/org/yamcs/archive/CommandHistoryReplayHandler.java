package org.yamcs.archive;

import org.yamcs.YamcsException;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.cmdhistory.CommandHistoryRecorder;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.MessageLite;

/**
 * Performs replays for command history
 * @author nm
 *
 */
public class CommandHistoryReplayHandler implements ReplayHandler {
    private ReplayRequest request;
    
    public CommandHistoryReplayHandler(String instance) {
    }

    @Override
    public void setRequest(ReplayRequest newRequest) throws YamcsException {
        this.request=newRequest;
    }

    @Override
    public String getSelectCmd() {
        StringBuilder sb=new StringBuilder();
        sb.append("SELECT ").append(ProtoDataType.CMD_HISTORY.getNumber()).
           append(",* from "+CommandHistoryRecorder.TABLE_NAME);
        appendTimeClause(sb, request);
        return sb.toString();
    }

    @Override
    public MessageLite transform(Tuple t) {
        CommandHistoryEntry.Builder che=CommandHistoryEntry.newBuilder();
        che.setCommandId(PreparedCommand.getCommandId(t));
        
        for(int i=1;i<t.size(); i++) { //first column is constant ProtoDataType.CMD_HISTORY.getNumber()
            ColumnDefinition cd=t.getColumnDefinition(i);
            String name=cd.getName();
            if(PreparedCommand.CNAME_GENTIME.equals(name)
                ||PreparedCommand.CNAME_ORIGIN.equals(name)
                ||PreparedCommand.CNAME_SEQNUM.equals(name)
                ||PreparedCommand.CNAME_CMDNAME.equals(name)) continue;
            che.addAttr(CommandHistoryAttribute.newBuilder()
                    .setName(name)
                    .setValue(ValueUtility.getColumnValue(cd, t.getColumn(i)))
                    .build());
        }
        return che.build();
    }

    @Override
    public void reset() {
        // TODO Auto-generated method stub

    }
    
    static void appendTimeClause(StringBuilder sb, ReplayRequest request) {
        if(request.hasStart() || (request.hasStop())) {
            sb.append(" where ");
            if(request.hasStart()) {
                sb.append(" gentime>="+request.getStart());
                if(request.hasStop()) sb.append(" and gentime<"+request.getStop());
            } else {
                sb.append(" gentime<"+request.getStop());
            }
        }
    }
}
