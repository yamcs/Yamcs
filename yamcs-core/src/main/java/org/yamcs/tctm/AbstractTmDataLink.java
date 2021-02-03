package org.yamcs.tctm;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.time.SimulationTimeService;
import org.yamcs.utils.DataRateMeter;
import org.yamcs.utils.YObjectLoader;

public abstract class AbstractTmDataLink extends AbstractLink implements TmPacketDataLink, SystemParametersProducer {
    protected AtomicLong packetCount = new AtomicLong(0);
    DataRateMeter packetRateMeter = new DataRateMeter();
    DataRateMeter dataRateMeter = new DataRateMeter();

    String packetPreprocessorClassName;
    YConfiguration packetPreprocessorArgs;
    protected PacketPreprocessor packetPreprocessor;

    private String spDataRate, spPacketRate;

    final static String CFG_PREPRO_CLASS = "packetPreprocessorClassName";
    private TmSink tmSink;
	protected String tmStreamName;
    protected boolean updateSimulationTime;

    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
        if (config.containsKey(CFG_PREPRO_CLASS)) {
            this.packetPreprocessorClassName = config.getString(CFG_PREPRO_CLASS);
        } else {
            this.packetPreprocessorClassName = IssPacketPreprocessor.class.getName();
        }
        if (config.containsKey("packetPreprocessorArgs")) {
            this.packetPreprocessorArgs = config.getConfig("packetPreprocessorArgs");
        }

        try {
            if (packetPreprocessorArgs != null) {
                packetPreprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance,
                        packetPreprocessorArgs);
            } else {
                packetPreprocessor = YObjectLoader.loadObject(packetPreprocessorClassName, instance);
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packet preprocessor", e);
            throw e;
        }

        updateSimulationTime = config.getBoolean("updateSimulationTime", false);
        if (updateSimulationTime) {
            if (timeService instanceof SimulationTimeService) {
                SimulationTimeService sts = (SimulationTimeService) timeService;
                sts.setTime0(0);
            } else {
                throw new ConfigurationException(
                        "updateSimulationTime can only be used together with SimulationTimeService "
                                + "(add 'timeService: org.yamcs.time.SimulationTimeService' in yamcs.<instance>.yaml)");
            }
        }

		tmStreamName = config.getString("stream", null);
    }

    @Override
    public void setupSystemParameters(SystemParametersCollector sysParamCollector) {
        super.setupSystemParameters(sysParamCollector);
        spDataRate = sysParamCollector.getNamespace() + "/" + linkName + "/dataRate";
        spPacketRate = sysParamCollector.getNamespace() + "/" + linkName + "/packetRate";
    }

    @Override
    protected void collectSystemParameters(long time, List<ParameterValue> list) {
        super.collectSystemParameters(time, list);
        list.add(SystemParametersCollector.getPV(spDataRate, time, dataRateMeter.getFiveSecondsRate()));
        list.add(SystemParametersCollector.getPV(spPacketRate, time, packetRateMeter.getFiveSecondsRate()));
    }

    @Override
    public long getDataInCount() {
        return packetCount.get();
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }
	
	@Override
	public String getTmStreamName() {
		return tmStreamName;
	}

    /**
     * Sends the packet downstream for processing.
     * <p>
     * Starting in Yamcs 5.2, if the updateSimulationTime option is set on the link configuration,
     * <ul>
     * <li>the timeService is expected to be SimulationTimeService</li>
     * <li>at initialization, the time0 is set to 0</li>
     * <li>upon each packet received, the generationTime (as set by the pre-processor) is used to update the simulation
     * elapsed time</li>
     * </ul>
     * <p>
     * Should be called by all sub-classes (instead of directly calling {@link TmSink#processPacket(TmPacket)}
     * 
     * @param tmpkt
     */
    protected void processPacket(TmPacket tmpkt) {
        tmSink.processPacket(tmpkt);
        if (updateSimulationTime) {
            SimulationTimeService sts = (SimulationTimeService) timeService;
            if(!tmpkt.isInvalid()) {
                sts.setSimElapsedTime(tmpkt.getGenerationTime());
            }
        }
    }

    /**
     * called when a new packet is received to update the statistics
     * 
     * @param packetSize
     */
    protected void updateStats(int packetSize) {
        packetCount.getAndIncrement();
        packetRateMeter.mark(1);
        dataRateMeter.mark(packetSize);
    }

    @Override
    public void resetCounters() {
        packetCount.set(0);
    }
}
