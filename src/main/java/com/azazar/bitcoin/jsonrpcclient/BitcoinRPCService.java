/*
 * Bitcoin-JSON-RPC-Client License
 * 
 * Copyright (c) 2013, Mikhail Yevchenko.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the 
 * Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject
 * to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.azazar.bitcoin.jsonrpcclient;

import static com.azazar.bitcoin.jsonrpcclient.MapWrapper.mapCTime;
import static com.azazar.bitcoin.jsonrpcclient.MapWrapper.mapDouble;
import static com.azazar.bitcoin.jsonrpcclient.MapWrapper.mapInt;
import static com.azazar.bitcoin.jsonrpcclient.MapWrapper.mapStr;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import org.springframework.stereotype.Service;

import ch.uzh.csg.coinblesk.server.util.Credentials;
import ch.uzh.csg.coinblesk.server.util.ServerProperties;

import com.azazar.biz.source_code.base64Coder.Base64Coder;
import com.azazar.krotjson.JSON;

@Service
public class BitcoinRPCService implements IBitcoinRPC {

    private class BlockMapWrapper extends MapWrapper implements Block {

        public BlockMapWrapper(Map m) {
            super(m);
        }

        public String bits() {
            return mapStr("bits");
        }

        public int confirmations() {
            return mapInt("confirmations");
        }

        public double difficulty() {
            return mapDouble("difficulty");
        }

        public String hash() {
            return mapStr("hash");
        }

        public int height() {
            return mapInt("height");
        }

        public String merkleRoot() {
            return mapStr("");
        }

        public Block next() throws BitcoinException {
            if (!m.containsKey("nextblockhash"))
                return null;
            return getBlock(nextHash());
        }

        public String nextHash() {
            return mapStr("nextblockhash");
        }

        public long nonce() {
            return mapLong("nonce");
        }

        public Block previous() throws BitcoinException {
            if (!m.containsKey("previousblockhash"))
                return null;
            return getBlock(previousHash());
        }

        public String previousHash() {
            return mapStr("previousblockhash");
        }

        public int size() {
            return mapInt("size");
        }

        public Date time() {
            return mapCTime("time");
        }

        public List<String> tx() {
            return (List<String>) m.get("tx");
        }

        public int version() {
            return mapInt("version");
        }

    }

    private class RawTransactionImpl extends MapWrapper implements RawTransaction {

        private class InImpl extends MapWrapper implements In {

            public InImpl(Map m) {
                super(m);
            }

            public RawTransaction getTransaction() {
                try {
                    return getRawTransaction(mapStr("txid"));
                } catch (BitcoinException ex) {
                    throw new RuntimeException(ex);
                }
            }

            public Out getTransactionOutput() {
                return getTransaction().vOut().get(mapInt("vout"));
            }

            public Map<String, Object> scriptSig() {
                return (Map) m.get("scriptSig");
            }

            public long sequence() {
                return mapLong("sequence");
            }

            public String txid() {
                return mapStr("txid");
            }

            public int vout() {
                return mapInt("vout");
            }

        }

        private class OutImpl extends MapWrapper implements Out {

            private class ScriptPubKeyImpl extends MapWrapper implements ScriptPubKey {

                public ScriptPubKeyImpl(Map m) {
                    super(m);
                }

                public List<String> addresses() {
                    return (List) m.get("addresses");
                }

                public String asm() {
                    return mapStr("asm");
                }

                public String hex() {
                    return mapStr("hex");
                }

                public int reqSigs() {
                    return mapInt("reqSigs");
                }

                public String type() {
                    return mapStr(type());
                }

            }

            public OutImpl(Map m) {
                super(m);
            }

            public int n() {
                return mapInt("n");
            }

            public ScriptPubKey scriptPubKey() {
                return new ScriptPubKeyImpl((Map) m.get("scriptPubKey"));
            }

            public TxInput toInput() {
                return new BasicTxInput(transaction().txId(), n());
            }

            public RawTransaction transaction() {
                return RawTransactionImpl.this;
            }

            public double value() {
                return mapDouble("value");
            }

        }

        public RawTransactionImpl(Map<String, Object> tx) {
            super(tx);
        }

        public String blockHash() {
            return mapStr("blockhash");
        }

        public Date blocktime() {
            return mapCTime("blocktime");
        }

        public int confirmations() {
            return mapInt("confirmations");
        }

        public String hex() {
            return mapStr("hex");
        }

        public long lockTime() {
            return mapLong("locktime");
        }

        public Date time() {
            return mapCTime("time");
        }

        public String txId() {
            return mapStr("txid");
        }

        public int version() {
            return mapInt("version");
        }

        public List<In> vIn() {
            final List<Map<String, Object>> vIn = (List<Map<String, Object>>) m.get("vin");
            return new AbstractList<In>() {

                @Override
                public In get(int index) {
                    return new InImpl(vIn.get(index));
                }

                @Override
                public int size() {
                    return vIn.size();
                }
            };
        }

        public List<Out> vOut() {
            final List<Map<String, Object>> vOut = (List<Map<String, Object>>) m.get("vout");
            return new AbstractList<Out>() {

                @Override
                public Out get(int index) {
                    return new OutImpl(vOut.get(index));
                }

                @Override
                public int size() {
                    return vOut.size();
                }
            };
        }

    }

    private static class ReceivedAddressListWrapper extends AbstractList<ReceviedAddress> {
        private final List<Map<String, Object>> wrappedList;

        public ReceivedAddressListWrapper(List<Map<String, Object>> wrappedList) {
            this.wrappedList = wrappedList;
        }

        @Override
        public ReceviedAddress get(int index) {
            final Map<String, Object> e = wrappedList.get(index);
            return new ReceviedAddress() {

                public String account() {
                    return (String) e.get("account");
                }

                public String address() {
                    return (String) e.get("address");
                }

                public double amount() {
                    return ((Number) e.get("amount")).doubleValue();
                }

                public int confirmations() {
                    return ((Number) e.get("confirmations")).intValue();
                }

                @Override
                public String toString() {
                    return e.toString();
                }

            };
        }

        @Override
        public int size() {
            return wrappedList.size();
        }
    }

    private class TransactionListMapWrapper extends ListMapWrapper<Transaction> {

        public TransactionListMapWrapper(List<Map> list) {
            super(list);
        }

        @Override
        protected Transaction wrap(final Map m) {
            return new Transaction() {

                private RawTransaction raw = null;

                public String account() {
                    return mapStr(m, "account");
                }

                public String address() {
                    return mapStr(m, "address");
                }

                public double amount() {
                    return mapDouble(m, "amount");
                }

                public String blockHash() {
                    return mapStr(m, "blockhash");
                }

                public int blockIndex() {
                    return mapInt(m, "blockindex");
                }

                public Date blockTime() {
                    return mapCTime(m, "blocktime");
                }

                public String category() {
                    return mapStr(m, "category");
                }

                public String comment() {
                    return mapStr(m, "comment");
                }

                public String commentTo() {
                    return mapStr(m, "to");
                }

                public int confirmations() {
                    return mapInt(m, "confirmations");
                }

                public double fee() {
                    return mapDouble(m, "fee");
                }

                public RawTransaction raw() {
                    if (raw == null)
                        try {
                            raw = getRawTransaction(txId());
                        } catch (BitcoinException ex) {
                            throw new RuntimeException(ex);
                        }
                    return raw;
                }

                public Date time() {
                    return mapCTime(m, "time");
                }

                public Date timeReceived() {
                    return mapCTime(m, "timereceived");
                }

                @Override
                public String toString() {
                    return m.toString();
                }

                public String txId() {
                    return mapStr(m, "txid");
                }

            };
        }

    }

    private class TransactionsSinceBlockImpl implements TransactionsSinceBlock {

        public final String lastBlock;
        public final List<Transaction> transactions;

        public TransactionsSinceBlockImpl(Map r) {
            this.transactions = new TransactionListMapWrapper((List) r.get("transactions"));
            this.lastBlock = (String) r.get("lastblock");
        }

        public String lastBlock() {
            return lastBlock;
        }

        public List<Transaction> transactions() {
            return transactions;
        }

    }

    private class UnspentListWrapper extends ListMapWrapper<Unspent> {

        public UnspentListWrapper(List<Map> list) {
            super(list);
        }

        @Override
        protected Unspent wrap(final Map m) {
            return new Unspent() {

                public String account() {
                    return mapStr(m, "account");
                }

                public String address() {
                    return mapStr(m, "address");
                }

                public double amount() {
                    return mapDouble(m, "amount");
                }

                public int confirmations() {
                    return mapInt(m, "confirmations");
                }

                public String scriptPubKey() {
                    return mapStr(m, "scriptPubKey");
                }

                public String txid() {
                    return mapStr(m, "txid");
                }

                public int vout() {
                    return mapInt(m, "vout");
                }

            };
        }
    }

    private static final Logger logger = Logger.getLogger(BitcoinRPCService.class.getCanonicalName());

    public static final Charset QUERY_CHARSET = Charset.forName("ISO8859-1");

    private static byte[] loadStream(InputStream in, boolean close) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (;;) {
            int nr = in.read(buffer);

            if (nr == -1)
                break;
            if (nr == 0)
                throw new IOException("Read timed out");

            o.write(buffer, 0, nr);
        }
        return o.toByteArray();
    }

    private String authStr;

    private Credentials credentials;

    private HostnameVerifier hostnameVerifier = null;

    private URL rpcURL;

    private SSLSocketFactory sslSocketFactory = null;

    /**
     * Default constructor: Lazy-loads Bitcoind credentials from server context
     * and other settings from server properties file
     */
    public BitcoinRPCService() {
    }

    public BitcoinRPCService(String rpcUrl) throws MalformedURLException {
        this(new URL(rpcUrl));
    }

    public BitcoinRPCService(URL rpcURL) throws MalformedURLException {
        this.rpcURL = rpcURL;
    }

    public String backupWallet(String destination) throws BitcoinException {
        return ((String) query("backupwallet", destination));
    }

    public String createRawTransaction(List<TxInput> inputs, List<TxOutput> outputs) throws BitcoinException {
        List<Map> pInputs = new ArrayList<Map>();

        for (final TxInput txInput : inputs) {
            pInputs.add(new LinkedHashMap() {
                {
                    put("txid", txInput.txid());
                    put("vout", txInput.vout());
                }
            });
        }

        Map<String, Double> pOutputs = new LinkedHashMap();

        Double oldValue;
        for (TxOutput txOutput : outputs) {
            if ((oldValue = pOutputs.put(txOutput.address(), txOutput.amount())) != null)
                pOutputs.put(txOutput.address(), BitcoinUtil.normalizeAmount(oldValue.doubleValue() + txOutput.amount()));
            // throw new BitcoinException("Duplicate output");
        }

        return (String) query("createrawtransaction", pInputs, pOutputs);
    }

    public String dumpPrivKey(String address) throws BitcoinException {
        return (String) query("dumpprivkey", address);
    }

    public String encryptWallet(String password) throws BitcoinException {
        return ((String) query("encryptwallet", password));
    }

    public String getAccount(String address) throws BitcoinException {
        return (String) query("getaccount", address);
    }

    public List<String> getAddressesByAccount(String account) throws BitcoinException {
        return (List<String>) query("getaddressesbyaccount", account);
    }

    private String getAuthStr() {

        if (authStr != null) {
            return authStr;
        }

        URL rpcURL = getRpcUrl();

        return rpcURL.getUserInfo() == null ? null : String.valueOf(Base64Coder.encode(rpcURL.getUserInfo().getBytes(Charset.forName("ISO8859-1"))));
    }

    public double getBalance() throws BitcoinException {
        return ((Number) query("getbalance")).doubleValue();
    }

    public double getBalance(String account) throws BitcoinException {
        return ((Number) query("getbalance", account)).doubleValue();
    }

    public double getBalance(String account, int minConf) throws BitcoinException {
        return ((Number) query("getbalance", account, minConf)).doubleValue();
    }

    public Block getBlock(String blockHash) throws BitcoinException {
        return new BlockMapWrapper((Map) query("getblock", blockHash));
    }

    public int getBlockCount() throws BitcoinException {
        return ((Number) query("getblockcount")).intValue();
    }

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public String getNewAddress() throws BitcoinException {
        return (String) query("getnewaddress");
    }

    public String getNewAddress(String account) throws BitcoinException {
        return (String) query("getnewaddress", account);
    }

    private URL getNoAuthURL() {
        URL rpc = getRpcUrl();
        URL noAuthURL = null;
        try {
            noAuthURL = new URI(rpc.getProtocol(), null, rpc.getHost(), rpc.getPort(), rpc.getPath(), rpc.getQuery(), null).toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(rpc.toString(), ex);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(rpc.toString(), ex);
        }
        return noAuthURL;
    }

    public RawTransaction getRawTransaction(String txId) throws BitcoinException {
        return new RawTransactionImpl((Map) query("getrawtransaction", txId, 1));
    }

    public String getRawTransactionHex(String txId) throws BitcoinException {
        return (String) query("getrawtransaction", txId);
    }

    public double getReceivedByAddress(String address) throws BitcoinException {
        return ((Number) query("getreceivedbyaddress", address)).doubleValue();
    }

    public double getReceivedByAddress(String address, int minConf) throws BitcoinException {
        return ((Number) query("getreceivedbyaddress", address, minConf)).doubleValue();
    }

    /**
     * Constructs the URL for the Bitcoind RPC connection. Settings such as
     * host, port, username, etc are lazy-loaded from server context and server
     * properties.
     * 
     * @return the Bitcoind RPC URL
     */
    private URL getRpcUrl() {

        if (rpcURL != null) {
            return rpcURL;
        }

        String host = ServerProperties.getProperty("bitcoind.host");
        String port = ServerProperties.getProperty("bitcoind.port");
        boolean testnet = "true".equals(ServerProperties.getProperty("bitcoind.testnet"));

        String password = credentials.getBitcoindPassword();
        String user = credentials.getBitcoindUsername();

        try {
            File f;
            File home = new File(System.getProperty("user.home"));

            if ((f = new File(home, ".bitcoin" + File.separatorChar + "bitcoin.conf")).exists()) {
            } else if ((f = new File(home, "AppData" + File.separatorChar + "Roaming" + File.separatorChar + "Bitcoin" + File.separatorChar + "bitcoin.conf")).exists()) {
            } else {
                f = null;
            }

            if (f != null) {
                logger.fine("Bitcoin configuration file found");

                Properties p = new Properties();
                FileInputStream i = new FileInputStream(f);
                try {
                    p.load(i);
                } finally {
                    i.close();
                }

                user = p.getProperty("rpcuser", user);
                password = p.getProperty("rpcpassword", password);
                host = p.getProperty("rpcconnect", host);
                port = p.getProperty("rpcport", port);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        URL url = null;
        try {
            if (testnet) {
                url = new URL("http://" + user + ':' + password + "@" + host + ":" + (port == null ? "18332" : port) + "/");
            } else {
                url = new URL("http://" + user + ':' + password + "@" + host + ":" + (port == null ? "8332" : port) + "/");
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }

        this.rpcURL = url;

        return url;
    }

    public SSLSocketFactory getSslSocketFactory() {
        return sslSocketFactory;
    }

    public void importPrivKey(String bitcoinPrivKey) throws BitcoinException {
        query("importprivkey", bitcoinPrivKey);
    }

    public void importPrivKey(String bitcoinPrivKey, String label) throws BitcoinException {
        query("importprivkey", bitcoinPrivKey, label);
    }

    public void importPrivKey(String bitcoinPrivKey, String label, boolean rescan) throws BitcoinException {
        query("importprivkey", bitcoinPrivKey, label, rescan);
    }

    @Override
    public String keyPoolRefill() throws BitcoinException {
        System.err.println("KeyPoolRefill is executed!");
        return ((String) query("keypoolrefill"));
    }

    public Map<String, Number> listAccounts() throws BitcoinException {
        return (Map) query("listaccounts");
    }

    public Map<String, Number> listAccounts(int minConf) throws BitcoinException {
        return (Map) query("listaccounts", minConf);
    }

    public List<ReceviedAddress> listReceivedByAddress() throws BitcoinException {
        return new ReceivedAddressListWrapper((List) query("listreceivedbyaddress"));
    }

    public List<ReceviedAddress> listReceivedByAddress(int minConf) throws BitcoinException {
        return new ReceivedAddressListWrapper((List) query("listreceivedbyaddress", minConf));
    }

    public List<ReceviedAddress> listReceivedByAddress(int minConf, boolean includeEmpty) throws BitcoinException {
        return new ReceivedAddressListWrapper((List) query("listreceivedbyaddress", minConf, includeEmpty));
    }

    public TransactionsSinceBlock listSinceBlock() throws BitcoinException {
        return new TransactionsSinceBlockImpl((Map) query("listsinceblock"));
    }

    public TransactionsSinceBlock listSinceBlock(String blockHash) throws BitcoinException {
        return new TransactionsSinceBlockImpl((Map) query("listsinceblock", blockHash));
    }

    public TransactionsSinceBlock listSinceBlock(String blockHash, int targetConfirmations) throws BitcoinException {
        return new TransactionsSinceBlockImpl((Map) query("listsinceblock", blockHash, targetConfirmations));
    }

    public List<Transaction> listTransactions() throws BitcoinException {
        return new TransactionListMapWrapper((List) query("listtransactions"));
    }

    public List<Transaction> listTransactions(String account) throws BitcoinException {
        return new TransactionListMapWrapper((List) query("listtransactions", account));
    }

    public List<Transaction> listTransactions(String account, int count) throws BitcoinException {
        return new TransactionListMapWrapper((List) query("listtransactions", account, count));
    }

    public List<Transaction> listTransactions(String account, int count, int from) throws BitcoinException {
        return new TransactionListMapWrapper((List) query("listtransactions", account, count, from));
    }

    public List<Unspent> listUnspent() throws BitcoinException {
        return new UnspentListWrapper((List) query("listunspent"));
    }

    public List<Unspent> listUnspent(int minConf) throws BitcoinException {
        return new UnspentListWrapper((List) query("listunspent", minConf));
    }

    public List<Unspent> listUnspent(int minConf, int maxConf) throws BitcoinException {
        return new UnspentListWrapper((List) query("listunspent", minConf, maxConf));
    }

    public List<Unspent> listUnspent(int minConf, int maxConf, String... addresses) throws BitcoinException {
        return new UnspentListWrapper((List) query("listunspent", minConf, maxConf, addresses));
    }

    public Object loadResponse(InputStream in, Object expectedID, boolean close) throws IOException, BitcoinException {
        try {
            String r = new String(loadStream(in, close), QUERY_CHARSET);
            logger.log(Level.FINE, "Bitcoin JSON-RPC response:\n{0}", r);
            try {
                Map response = (Map) JSON.parse(r);

                if (!expectedID.equals(response.get("id")))
                    throw new BitcoinRPCException("Wrong response ID (expected: " + String.valueOf(expectedID) + ", response: " + response.get("id") + ")");

                if (response.get("error") != null)
                    throw new BitcoinException(JSON.stringify(response.get("error")));

                return response.get("result");
            } catch (ClassCastException ex) {
                throw new BitcoinRPCException("Invalid server response format (data: \"" + r + "\")");
            }
        } finally {
            if (close)
                in.close();
        }
    }

    public byte[] prepareRequest(final String method, final Object... params) {
        return JSON.stringify(new LinkedHashMap() {
            {
                put("method", method);
                put("params", params);
                put("id", "1");
            }
        }).getBytes(QUERY_CHARSET);
    }

    public Object query(String method, Object... o) throws BitcoinException {
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) getNoAuthURL().openConnection();

            conn.setDoOutput(true);
            conn.setDoInput(true);

            if (conn instanceof HttpsURLConnection) {
                if (hostnameVerifier != null)
                    ((HttpsURLConnection) conn).setHostnameVerifier(hostnameVerifier);
                if (sslSocketFactory != null)
                    ((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory);
            }

            // conn.connect();

            ((HttpURLConnection) conn).setRequestProperty("Authorization", "Basic " + getAuthStr());
            byte[] r = prepareRequest(method, o);
            logger.log(Level.FINE, "Bitcoin JSON-RPC request:\n{0}", new String(r, QUERY_CHARSET));
            conn.getOutputStream().write(r);
            conn.getOutputStream().close();
            int responseCode = conn.getResponseCode();
            if (responseCode != 200)
                throw new BitcoinRPCException("RPC Query Failed (method: " + method + ", params: " + Arrays.deepToString(o) + ", response header: " + responseCode + " "
                        + conn.getResponseMessage() + ", response: " + new String(loadStream(conn.getErrorStream(), true)));
            return loadResponse(conn.getInputStream(), "1", true);
        } catch (IOException ex) {
            throw new BitcoinRPCException("RPC Query Failed (method: " + method + ", params: " + Arrays.deepToString(o) + ")", ex);
        }
    }

    public String sendFrom(String fromAccount, String toBitcoinAddress, double amount) throws BitcoinException {
        return (String) query("sendfrom", fromAccount, toBitcoinAddress, amount);
    }

    public String sendFrom(String fromAccount, String toBitcoinAddress, double amount, int minConf) throws BitcoinException {
        return (String) query("sendfrom", fromAccount, toBitcoinAddress, amount, minConf);
    }

    public String sendFrom(String fromAccount, String toBitcoinAddress, double amount, int minConf, String comment) throws BitcoinException {
        return (String) query("sendfrom", fromAccount, toBitcoinAddress, amount, minConf, comment);
    }

    public String sendFrom(String fromAccount, String toBitcoinAddress, double amount, int minConf, String comment, String commentTo) throws BitcoinException {
        return (String) query("sendfrom", fromAccount, toBitcoinAddress, amount, minConf, comment, commentTo);
    }

    public String sendRawTransaction(String hex) throws BitcoinException {
        return (String) query("sendrawtransaction", hex);
    }

    public String sendToAddress(String toAddress, double amount) throws BitcoinException {
        return (String) query("sendtoaddress", toAddress, amount);
    }

    public String sendToAddress(String toAddress, double amount, String comment) throws BitcoinException {
        return (String) query("sendtoaddress", toAddress, amount, comment);
    }

    public String sendToAddress(String toAddress, double amount, String comment, String commentTo) throws BitcoinException {
        return (String) query("sendtoaddress", toAddress, amount, comment, commentTo);
    }

    public void setCredentials(Credentials credentials) {
        System.out.println("lolrofl");
        System.out.println(credentials);
        this.credentials = credentials;
    }

    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    public void setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    public String signRawTransaction(String hex) throws BitcoinException {
        Map result = (Map) query("signrawtransaction", hex);

        if ((Boolean) result.get("complete"))
            return (String) result.get("hex");
        else
            throw new BitcoinException("Incomplete");
    }

    public String unlockWallet(String password, int duration) throws BitcoinException {
        return ((String) query("walletpassphrase", password, duration));
    }

    public AddressValidationResult validateAddress(String address) throws BitcoinException {
        final Map validationResult = (Map) query("validateaddress", address);
        return new AddressValidationResult() {

            public String account() {
                return (String) validationResult.get("account");
            }

            public String address() {
                return (String) validationResult.get("address");
            }

            public boolean isCompressed() {
                return ((Boolean) validationResult.get("iscompressed"));
            }

            public boolean isMine() {
                return ((Boolean) validationResult.get("ismine"));
            }

            public boolean isScript() {
                return ((Boolean) validationResult.get("isscript"));
            }

            public boolean isValid() {
                return ((Boolean) validationResult.get("isvalid"));
            }

            public String pubKey() {
                return (String) validationResult.get("pubkey");
            }

            @Override
            public String toString() {
                return validationResult.toString();
            }

        };
    }
}
