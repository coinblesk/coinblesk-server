package ch.uzh.csg.coinblesk.server.bitcoin;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

public class P2SHScript extends Script {

    public P2SHScript(byte[] programBytes) {
        super(programBytes);
    }
    
    @Override
    public boolean isPayToScriptHash() {
        return true;
    }
    
    public static P2SHScript dummy() {
        Script dummyScript = new ScriptBuilder().build();
        return new P2SHScript(dummyScript.getProgram());
    }

}
