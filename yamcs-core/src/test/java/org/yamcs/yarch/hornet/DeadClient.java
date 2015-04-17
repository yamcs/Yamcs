package org.yamcs.yarch.hornet;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;


import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.client.impl.ClientSessionFactoryImpl;
import org.hornetq.core.client.impl.DelegatingSession;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory;
import org.hornetq.core.remoting.impl.netty.NettyConnection;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.JournalType;
import org.hornetq.core.server.impl.HornetQServerImpl;
import org.hornetq.core.settings.impl.AddressFullMessagePolicy;
import org.hornetq.core.settings.impl.AddressSettings;


public class DeadClient {
    static int n=1000000;
    static ClientProducer producer;

    static class NotificationConsumer implements Runnable{
        @Override
        public void run() {
            try {
                ServerLocator locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(InVMConnectorFactory.class.getName()));
                ClientSessionFactory factory =  locator.createSessionFactory();
                ClientSession session = factory.createSession(false, true, true, true);

                //          session.createQueue("example", "example", true);
                session.createTemporaryQueue("hornetq.notifications","cucubau");
                ClientConsumer nconsumer=session.createConsumer("cucubau");
                session.start();
                while(true) {
                    ClientMessage nmsg=nconsumer.receive();
                    String hq_notifType=nmsg.getStringProperty("_HQ_NotifType");
                    String hq_address=nmsg.getStringProperty("_HQ_Address");
                    if("CONSUMER_CLOSED".equals(hq_notifType) && "tempAddress".equals(hq_address)) {
                       // System.out.println("closing producer");
                        producer.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
    static class HornetConsumer implements Runnable{
        @Override
        public void run() {
            try {
                ServerLocator locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(InVMConnectorFactory.class.getName()));
                ClientSessionFactory factory =  locator.createSessionFactory();
                ClientSession session = factory.createSession(false, true, true, true);
                NettyConnection nc=(NettyConnection)((DelegatingSession)session).getConnection().getTransportConnection();

                session.createTemporaryQueue("tempAddress","tempQueue");
                ClientConsumer consumer = session.createConsumer("tempQueue",false);
                session.start();
                nc.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }
    static class HornetProducer implements Runnable{

        @Override
        public void run() {
            SimpleDateFormat sdf=new SimpleDateFormat("hh:mm:ss.SSS");
            try {
                Thread.sleep(2000);
                ServerLocator locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(InVMConnectorFactory.class.getName()));
                ClientSessionFactory factory =  locator.createSessionFactory();
                ClientSession session = factory.createSession(false, true, true, true);
                producer=session.createProducer("tempAddress");

                session.start();
                long t0=System.currentTimeMillis();
                for(int i=0;i<n;i++) {
                    ClientMessage message = session.createMessage(false);
                    message.getBodyBuffer().writeString("Hello "+i+" "+sdf.format(new Date()));
                    //if(i%1000==0)  System.out.println("sending message "+i);
                    producer.send(message);
                    //   System.out.println(sdf.format(new Date())+" answer: "+resp.getBodyBuffer().readString());
                    //  Thread.sleep(10);
                }
                long t1=System.currentTimeMillis();
                double d=t1-t0;
                //System.out.println(n+" messages received in "+d+" ms, speed: "+1000*d/n+" micros/message");
                //System.out.println("still "+session.queueQuery(qname).getMessageCount()+" mesages in the queue");
                session.close();

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }   


    public static void main(String[] args) throws Exception {
        Configuration config = new ConfigurationImpl();
        HashSet<TransportConfiguration> transports = new HashSet<TransportConfiguration>();
        
        Map<String,Object> params=new HashMap<String,Object>();
        params.put("use-nio", true);
        TransportConfiguration nconfig=new TransportConfiguration(NettyAcceptorFactory.class.getName(),params);
        transports.add(nconfig);
        transports.add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
        config.setAcceptorConfigurations(transports);
        config.setJournalType(JournalType.NIO);
        config.setSecurityEnabled(false);
        config.setPersistenceEnabled(false);
    //    config.setManagementNotificationAddress(new SimpleString("hornetq.notifications"));
        //   System.out.println("config="+config.);
        HornetQServer server = new HornetQServerImpl(config);

        // new Thread(new SlowClient.ServerControl(server)).start();



        server.start();

        AddressSettings as=new AddressSettings();
        as.setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK);
        as.setMaxSizeBytes(1000);
        server.getAddressSettingsRepository().addMatch("tempAddress", as);

        new Thread(new DeadClient.NotificationConsumer()).start();
        new Thread(new DeadClient.HornetConsumer()).start();
        new Thread(new DeadClient.HornetProducer()).start();

    }


}
