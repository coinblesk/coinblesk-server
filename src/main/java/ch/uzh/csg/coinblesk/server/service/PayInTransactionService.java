package ch.uzh.csg.coinblesk.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.coinblesk.model.HistoryPayInTransaction;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayInTransaction;
import ch.uzh.csg.coinblesk.server.dao.PayInTransactionDAO;
import ch.uzh.csg.coinblesk.server.dao.PayInTransactionUnverifiedDAO;
import ch.uzh.csg.coinblesk.server.dao.UserAccountDAO;
import ch.uzh.csg.coinblesk.server.domain.PayInTransaction;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.util.Emailer;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;

/**
 * Service class for {@link PayInTransaction}.
 * 
 */
@Service
public class PayInTransactionService implements IPayInTransaction{
	@Autowired
	private PayInTransactionDAO payInTransactionDAO;
	@Autowired
	private PayInTransactionUnverifiedDAO payInTransactionUnverifiedDAO;
	@Autowired
	private UserAccountDAO userAccountDAO;

	@Override
	@Transactional
	public void create(Transaction transaction) throws UserAccountNotFoundException {
		long userID = userAccountDAO.getByBTCAddress(transaction.address()).getId();
		PayInTransaction pit = new PayInTransaction(userID, transaction);
		if (payInTransactionDAO.isNew(pit)) {
			payInTransactionDAO.createPayInTransaction(pit);
			payInTransactionUnverifiedDAO.remove(pit);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<HistoryPayInTransaction> getHistory(String username, int page) throws UserAccountNotFoundException {
		return payInTransactionDAO.getHistory(username, page);
	}

	@Override
	@Transactional(readOnly = true)
	public long getHistoryCount(String username) throws UserAccountNotFoundException {
		UserAccount userAccount = userAccountDAO.getByUsername(username);
		return payInTransactionDAO.getHistoryCount(userAccount);
	}

	@Override
	@Transactional(readOnly = true)
	public List<HistoryPayInTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException {
		UserAccount userAccount = userAccountDAO.getByUsername(username);
		return payInTransactionDAO.getLast5Transactions(userAccount);
	}
	
	//TODO: does this need to be here?
	@Override
	public TransferObject sendPayInAddressByEmail(String username, String email, String payInAddress) {	
		Emailer.sendPayInAddressAsEmail(username, email, payInAddress);
		TransferObject transferObject = new TransferObject();
		transferObject.setSuccessful(true);
		transferObject.setMessage("Pay in address is send to your email address.");
		return transferObject;
	}
	
	@Override
	@Transactional
	public void createPayInTransaction(PayInTransaction tx) throws UserAccountNotFoundException {
	    payInTransactionDAO.createPayInTransaction(tx);
	    
    }

}
