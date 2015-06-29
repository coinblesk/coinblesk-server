package ch.uzh.csg.coinblesk.server.bitcoin;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;

public class DummyPeerDiscovery implements PeerDiscovery {

    @Override
    public InetSocketAddress[] getPeers(long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
        return new InetSocketAddress[0];
    }

    @Override
    public void shutdown() {
        // do nothing
    }

}
