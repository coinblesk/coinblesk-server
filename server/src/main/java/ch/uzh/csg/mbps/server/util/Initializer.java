package ch.uzh.csg.mbps.server.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ch.uzh.csg.mbps.util.KeyHandler;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * {@link Initializer} is taking care of starting necessary tasks after successfully starting up Tomcat server application.
 *
 */
public class Initializer implements InitializingBean{
	private static Logger LOGGER = Logger.getLogger(Initializer.class);

	@SuppressWarnings("resource")
	public void afterPropertiesSet(){
		try {
			BitcoindController.backupWallet();
			//activates receivePayIn/Out Listener
			BitcoindController.listenIncomingTransactions();
			BitcoindController.listenOutgoingTransactions();
			//activates Task for checking PayOutRules
			new ClassPathXmlApplicationContext("HourlyQuartz.xml");
			
			//if ServerKeys.txt == null create new Private/Public Key pair
			String keyFilePath = "ServerKeys.txt";

			File serverKeys = null;
			URI uri = null;
			try {
				uri = getClass().getResource("/" + keyFilePath).toURI();
			} catch (URISyntaxException e) {
				LOGGER.info("Couldnt create server keys", e);
			}

			serverKeys = new File(uri);
			try {
				BufferedReader serverKeysFile = new BufferedReader(new FileReader(serverKeys));
				String line = serverKeysFile.readLine();
				if(line == null){
					createServerKeys(serverKeys);
				} else{
					String privateKeyEncoded = line;
					String publicKeyEncoded = serverKeysFile.readLine();
					serverKeysFile.close();
					Constants.PRIVATEKEY = KeyHandler.decodePrivateKey(privateKeyEncoded);
					Constants.PUBLICKEY = KeyHandler.decodePublicKey(publicKeyEncoded);						
				}
			} catch (Exception e) {
				LOGGER.error("Problem reading Serverkeys from Input File", e);
			}
		} catch (BitcoinException e) {
			LOGGER.error("Bitcoind Exception: Couldn't initialize receivment of Bitcoin PayIN Transactions");
		}

		try {
			ExchangeRates.updateExchangeRateUsdChf();
		} catch (ParseException | IOException e) {
			LOGGER.error("Problem updating USD/CHF exchange rate.");
		}

		updateExchangeRateTask();
		
	}

	private void createServerKeys(File serverKeys){
		try {
			KeyPair keypair = KeyHandler.generateKeys();
			FileWriter fileWriter = new FileWriter(serverKeys);
			fileWriter.write(KeyHandler.encodePrivateKey(keypair.getPrivate()) + "\n");
			fileWriter.write(KeyHandler.encodePublicKey(keypair.getPublic()));
			fileWriter.close();

			Constants.PRIVATEKEY = keypair.getPrivate();
			Constants.PUBLICKEY = keypair.getPublic();
		} catch (Exception e) {
			LOGGER.error("Problem creating Serverkeys.");
		}
	}
	
	private static void updateExchangeRateTask(){
		try {
			//update ExchangeRate every 5 seconds
			ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
			exec.scheduleAtFixedRate(new Runnable() {
			  @Override
			  public void run() {
			   	try {
					ExchangeRates.update();
				} catch (Throwable t) {
					LOGGER.error("Problem updating exchangerate. " + t.getMessage());
				}
			  }
			}, 30, Config.EXCHANGE_RATE_UPDATE_TIME, TimeUnit.SECONDS);
		} catch (Exception e) {
			LOGGER.error("Problem updating exchangerate. " + e.getMessage());
		}
	}
}
