package org.yamcs.archive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ChannelFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.ParameterValue;
import org.yamcs.TmProcessor;
import org.yamcs.YamcsException;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterRequestManagerIf;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.tctm.TcTmService;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.Subscription;
import org.yamcs.xtceproc.XtceTmProcessor;
import org.yamcs.yarch.Tuple;

import com.google.common.util.concurrent.AbstractService;
import com.google.protobuf.MessageLite;

public class ParameterReplayHandler implements ReplayHandler, ParameterWithIdConsumer {
    final XtceDb xtcedb;
    static Logger log=LoggerFactory.getLogger(ParameterReplayHandler.class.getName());
    ReplayRequest request;
    static AtomicInteger counter=new AtomicInteger(); 
    XtceTmProcessor tmProcessor;
    ParameterWithIdRequestHelper pidrm;
    ParameterRequestManager prm;
    ArrayList<ParameterValueWithId> paramList=new ArrayList<ParameterValueWithId>();
    final Set<String> tmPartitions=new HashSet<String>();
    Set<String>ppGroups=new HashSet<String>();
    Set<Parameter> ppSet=new HashSet<Parameter>();

    Channel channel;
    String instance;
    boolean hasTm, hasPp;

    /**
     * Implements parameter replays. One of xtcedb or ppdb can be null meaning
     * that only the other one will be used to retrieve parameters 
     * 
     * @param instance
     * @param xtcedb
     * @param ppdb
     */
    public ParameterReplayHandler(String instance, XtceDb xtcedb) {
        this.instance=instance;
        this.xtcedb=xtcedb;
    }

    @Override
    public void setRequest(ReplayRequest newRequest) throws YamcsException {
        this.request=newRequest;
        tmPartitions.clear();

        //find all the packets where a given parameter appears
        List<NamedObjectId> plist=request.getParameterRequest().getNameFilterList();
        if(plist.isEmpty()) throw new YamcsException("Cannot create a replay with an empty parameter list");

        MyTcTmService tctms=new MyTcTmService();

        try {
            channel = ChannelFactory.create(instance, "paramreplay"+counter.getAndIncrement(), "ParamReplay", tctms, "internal");
        } catch (Exception e) {
            throw new YamcsException("cannot create channel", e);
        }
        prm = channel.getParameterRequestManager();
        pidrm= new ParameterWithIdRequestHelper(prm, this);

        //add all parameters to the ParameterManager and later one query which tm packets or pp groups are subscribed
        // to use them when creating the replay streams.
        try {
            pidrm.addRequest(plist);
        } catch (InvalidIdentification e) {
            NamedObjectList nol=NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
            throw new YamcsException("InvalidIdentification", "Invalid identification", nol);
        }

        if(xtcedb!=null) {
            tmProcessor=channel.getTmProcessor();
            Subscription subscription=tmProcessor.getSubscription();
            Collection<SequenceContainer> containers =subscription.getInheritingContainers(xtcedb.getRootSequenceContainer());

            if((containers==null)|| (containers.isEmpty())) {
                log.debug("No container required for the parameter extract");
            } else {
                log.debug("creating a packet replay for the following containers: "+containers);
                SequenceContainer rootSc=xtcedb.getRootSequenceContainer();

                for(SequenceContainer sc:containers) {
                    while(sc!=null) {
                        if(sc.getBaseContainer()==rootSc) {
                            tmPartitions.add(sc.getQualifiedName());
                            break;
                        } else {
                            sc=sc.getBaseContainer();
                        }
                    }
                }
            }
        }
        
        MyPpProvider mpp= (MyPpProvider) tctms.pp;
        ppGroups=mpp.ppgroups;
        ppSet=mpp.subscribedParams;
        hasTm=!tmPartitions.isEmpty();
        hasPp=!ppGroups.isEmpty();

        channel.start();
    }

    @Override
    public String getSelectCmd() {
        if(!hasTm && !hasPp)return null;
        
        StringBuilder sb=new StringBuilder();

        if(hasTm && hasPp) sb.append("MERGE (");
        if(hasTm) {
            sb.append("SELECT ").append(ProtoDataType.PARAMETER.getNumber()).
            append(",* from tm where pname in (");
            boolean first=true;
            for(String pn:tmPartitions) {
                if(first) first=false;
                else sb.append(", ");
                sb.append("'").append(pn).append("'");
            }
            sb.append(")");
            XtceTmReplayHandler.appendTimeClause(sb, request, false);
        }
        if(hasTm && hasPp) sb.append("), (");

        if(hasPp) {
            boolean first=true;
            sb.append("SELECT ").append(ProtoDataType.PARAMETER.getNumber()).
            append(",* from pp where ppgroup in(");
            for(String g:ppGroups) {
                if(first) first=false;
                else sb.append(", ");
                sb.append("'").append(g).append("'");
            }
            sb.append(")");
            XtceTmReplayHandler.appendTimeClause(sb, request, false);
        }
        if(hasTm && hasPp) sb.append(") USING gentime");
        return sb.toString();
    }


    @Override
    public MessageLite transform(Tuple t) {
        boolean isTm=false, isPp=false;
        if(hasTm && hasPp) {
            if(t.hasColumn("ppgroup")) isPp=true;
            else isTm=true;
        } else if(hasTm) {
            isTm=true;
        } else {
            isPp=true;
        }
        if(isTm) {
            long recTime=(Long)t.getColumn("rectime");
            long genTime=(Long)t.getColumn("gentime");
            byte[]pbody=(byte[]) t.getColumn("packet");
            
            ///this will cause derived values to be computed and updateItems to be called
            tmProcessor.processPacket(new PacketWithTime(recTime, genTime, pbody));
        }
        
        if(isPp) { //definition is in {@link org.yamcs.archive.PpProviderAdapter}
            ArrayList<ParameterValue> params=new ArrayList<ParameterValue>(t.size());
            for(int i=3; i<t.size(); i++) {
                Parameter ppDef=xtcedb.getParameter(t.getColumnDefinition(i).getName());
                if (!ppSet.contains(ppDef)) continue;
                org.yamcs.protobuf.Pvalue.ParameterValue gpv=(org.yamcs.protobuf.Pvalue.ParameterValue) t.getColumn(i);
                ParameterValue pv=ParameterValue.fromGpb(ppDef, gpv);
                
                if(pv!=null) params.add(pv);
            }

            //this will cause derived values to be computed and updateItems to be called            
            if(!params.isEmpty()) prm.update(params);
        }

        if(!paramList.isEmpty()) {
            ParameterData.Builder pd=ParameterData.newBuilder();
            for(ParameterValueWithId pvwi:paramList) {
                pd.addParameter(pvwi.getParameterValue().toGpb(pvwi.getId()));
            }
            paramList.clear();
            return pd.build();
        } else {
            return null;
        }
    }


    @Override
    public void update(int subscriptionId, List<ParameterValueWithId> plist) {
        this.paramList.addAll(plist);
    }


    @Override
    public void reset() {
        if(channel!=null) {
            channel.quit();
            channel=null;
        }
    }


    class MyTcTmService extends AbstractService implements TcTmService {
        MyTmPacketProvider tm;
        MyPpProvider pp;
        
        
        public MyTcTmService() {
            if(xtcedb!=null) tm=new MyTmPacketProvider();
            if(xtcedb!=null) pp=new MyPpProvider();
        }

        @Override
        protected void doStart() {
            notifyStarted();

        }

        @Override
        protected void doStop() {
            notifyStopped();
        }

        @Override
        public TmPacketProvider getTmPacketProvider() {
            return tm;
        }

        @Override
        public CommandReleaser getCommandReleaser() {
            return null;
        }

        @Override
        public List<ParameterProvider> getParameterProviders() {
            ArrayList<ParameterProvider> a = new ArrayList<ParameterProvider>();
            a.add(pp);
            return a;
        }

        @Override
        public boolean isSynchronous() {
            return true;
        }
    }

    /**
     * Dummy class that does not do anything. All the packets are pushed to the tmprocessor directly
     * in the transform method above. 
     *
     */
    class MyTmPacketProvider extends AbstractService implements TmPacketProvider {

        @Override
        public void setTmProcessor(TmProcessor tmProcessor) {
        }

        @Override
        public String getLinkStatus() {
            return null;
        }

        @Override
        public boolean isArchiveReplay() {
            return false;
        }

        @Override
        public String getDetailedStatus() {
            return null;
        }


        @Override
        public void disable() {
        }

        @Override
        public void enable() {
        }

        @Override
        public boolean isDisabled() {
            return false;
        }

        @Override
        protected void doStart() {
            notifyStarted();
        }

        @Override
        public void doStop() {
            notifyStopped();
        }

        @Override
        public long getDataCount() {
            return 0;
        }
    }
    
    public class MyPpProvider extends AbstractService implements ParameterProvider {
        private volatile boolean disabled=false;
        Set<String> ppgroups=new HashSet<String>();
        Set<Parameter> subscribedParams = new HashSet<Parameter>();
        
        @Override
		public void init(Channel channel) throws ConfigurationException {
			
		}
        @Override
        public void setParameterListener(ParameterRequestManagerIf parameterRequestManager) {
        }

        @Override
        public void startProviding(Parameter paramDef) {
            ppgroups.add(paramDef.getRecordingGroup());
            subscribedParams.add(paramDef);
        }
        
        @Override
        public void startProvidingAll() {
        }
        @Override
        public void stopProviding(Parameter paramDef) {
        }
        
        @Override
        public boolean canProvide(NamedObjectId id) {
            if(xtcedb.getParameter(id)!=null) return true;
            else return false;
        }

        @Override
        public boolean canProvide(Parameter p) {
            return true;
        }

        public String getDownlinkStatus() {
            return disabled ? "DISABLED" : "OK";
        }
        
        @Override
        public Parameter getParameter(NamedObjectId id) throws InvalidIdentification {
            Parameter p=xtcedb.getParameter(id);
            if(p==null) throw new InvalidIdentification();
            else return p;
        }

        @Override
        protected void doStart() {
            notifyStarted();
        }

        @Override
        protected void doStop() {
            notifyStopped();
        }

		
    }
}