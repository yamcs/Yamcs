package org.yamcs.tctm.pus.services.tm.two;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmModifier;

public class ServiceTwo implements PusService {
    Log log;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();

    String yamcsInstance;
    YConfiguration serviceTwoConfig;

    public ServiceTwo(String yamcsInstance, YConfiguration serviceTwoConfig) {
        this.yamcsInstance = yamcsInstance;
        this.serviceTwoConfig = serviceTwoConfig;

        initializeSubServices();    
    }

    public void initializeSubServices() {
        pusSubServices.put(6, new SubServiceSix(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("six")));
        pusSubServices.put(9, new SubServiceNine(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("nine")));
        pusSubServices.put(12, new SubServiceNine(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("twelve")));
    }

    @Override
    public TmPacket extractPusModifiers(TmPacket tmPacket) {
        return pusSubServices.get(PusTmModifier.getMessageSubType(tmPacket)).process(tmPacket);
    }


    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }
}
