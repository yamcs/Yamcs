package org.yamcs.tctm.pus.services.tm.seventeen;

import java.util.ArrayList;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;

public class SubServiceFour implements PusSubService {

    String yamcsInstance;
    YConfiguration config;

    public SubServiceFour(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket);

        return pPkts;
    }
}
