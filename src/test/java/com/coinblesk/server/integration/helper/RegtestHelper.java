package com.coinblesk.server.integration.helper;

import com.coinblesk.util.DTOUtils;
import org.bitcoinj.core.Coin;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by Sebastian Stephan on 26.04.17.
 */
public class RegtestHelper {
	private final static String BITCOIN_CLI_URL = "http://127.0.0.1:18332";

	public static void generateBlock(int count) {
		try {
			sendRegtestCommand("generate", String.valueOf(count), BitcoinCLIMultipleResults.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String sendToAddress(String address, Coin amount) {
		try {
			return sendRegtestCommand("sendtoaddress", "\"" + address + "\"," + amount.toPlainString(), BitcoinCLIResult.class).result;
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
	}

	private static <T> T sendRegtestCommand(String method, String params, Class<T> resultClass) throws IOException {
		String cookie = new String(Files.readAllBytes(Paths.get("/tmp/regtest/regtest/.cookie")));
		String base64Auth = DTOUtils.toBase64(cookie);
		URL obj = new URL(BITCOIN_CLI_URL);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Authorization", "Basic " + base64Auth);
		String postJsonData = "{\"method\":\"" + method + "\",\"params\":[" + params + "],\"id\":1}\n";
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(postJsonData);
		wr.flush();
		wr.close();
		int responseCode = con.getResponseCode();
		BufferedReader in = new BufferedReader(
			new InputStreamReader(con.getInputStream()));
		String output;
		StringBuilder response = new StringBuilder();
		while ((output = in.readLine()) != null) {
			response.append(output);
		}
		in.close();
		return DTOUtils.fromJSON(response.toString(), resultClass);
	}

	private static class BitcoinCLIResult {
		public String result;
	}
	private static class BitcoinCLIMultipleResults {
		public List<String> result;
	}
}
