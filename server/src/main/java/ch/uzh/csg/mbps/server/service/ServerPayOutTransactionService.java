package ch.uzh.csg.mbps.server.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerPayOutTransaction;
import ch.uzh.csg.mbps.server.dao.ServerPayOutTransactionDAO;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerPayOutTransaction;
import ch.uzh.csg.mbps.server.util.BitcoindController;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerPayOutTransaction;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;
import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

public class ServerPayOutTransactionService implements IServerPayOutTransaction {

	@Autowired 
	private ServerPayOutTransactionDAO serverPayOutTransactionDAO;
	@Autowired
	private IServerAccount serverAccountService;
	
	//TODO:mehmet Tests
	
	@Override
	@Transactional
	public TransferObject createPayOutTransaction(String url, BigDecimal amount, String address) throws BitcoinException, ServerAccountNotFoundException {
		TransferObject transferObject = new TransferObject();
		ServerAccount serverAccount = serverAccountService.getByUrl(url);
		ServerPayOutTransaction spot = new ServerPayOutTransaction();
		spot.setAmount(amount);
		spot.setPayoutAddress(address);
		spot.setServerAccountID(serverAccount.getId());
		
		BigDecimal accountBalance = serverAccount.getActiveBalance();
		
		//check if server account wants to pay out complete amount
		if(accountBalance.compareTo(spot.getAmount()) == 0){
			BigDecimal payOutAmount = spot.getAmount().subtract(Config.TRANSACTION_FEE);
			if (payOutAmount.compareTo(BigDecimal.ZERO) > 0){
				spot.setAmount(payOutAmount);
			} else {
				transferObject.setSuccessful(false);
				//"Couldn't pay out the desired amount. Your balance is too low."
				transferObject.setMessage("PAYOUT_ERROR_BALANCE");
				return transferObject;
			}
		}
		
		if(accountBalance.compareTo(spot.getAmount().add(Config.TRANSACTION_FEE)) >= 0){
			if (BitcoindController.validateAddress(spot.getPayoutAddress())) {
				spot.setTimestamp(new Date());
				//do payOut in BitcoindController
				String transactionID = BitcoindController.sendCoins(spot.getPayoutAddress(), spot.getAmount());
				spot.setTransactionID(transactionID);
				
				amount = spot.getAmount();
				spot.setAmount(spot.getAmount().add(Config.TRANSACTION_FEE));

				//write payOut to DB
				serverPayOutTransactionDAO.createPayOutTransaction(spot);
				transferObject.setSuccessful(true);
				transferObject.setMessage(amount  + "BTC " + "(+" + Config.TRANSACTION_FEE + "BTC TxFee)");
				return transferObject;
			} else {
				transferObject.setSuccessful(false);
				//"Couldn't pay out the desired amount. The BTC Address is invalid."
				transferObject.setMessage("PAYOUT_ERROR_ADDRESS");
				return transferObject;
				
			}
		} else{
			transferObject.setSuccessful(false);
			//"Couldn't pay out the desired amount. Your balance is lower than your specified PayOut amount."
			transferObject.setMessage("PAYOUT_ERROR_BALANCE");
			return transferObject;
		}
	}
	
	@Override
	@Transactional(readOnly=true)
	public List<HistoryServerPayOutTransaction> getHistory(int page) {
		return serverPayOutTransactionDAO.getHistory(page);
	}
	
	@Override
	@Transactional(readOnly=true)
	public long getHistoryCount(String url) throws ServerAccountNotFoundException {
		return serverPayOutTransactionDAO.getHistoryCount(url);
	}
	
	@Override
	@Transactional
	public void check(Transaction transaction) {
		ServerPayOutTransaction spot = new ServerPayOutTransaction(transaction);
		try {
			serverPayOutTransactionDAO.verify(spot);
		} catch (TransactionException e) {
		}
	}
	
	@Override
	@Transactional(readOnly=true)
	public List<HistoryServerPayOutTransaction> getLast5Transactions() {
		return serverPayOutTransactionDAO.getLast5Transactions();
	}

	@Override
	@Transactional(readOnly=true)
	public List<HistoryServerPayOutTransaction> getLast5ServerAccountTransactions(String url) throws ServerAccountNotFoundException {
		serverAccountService.getByUrl(url);
		return serverPayOutTransactionDAO.getLast5ServerAccountTransactions(url);
	}
}
