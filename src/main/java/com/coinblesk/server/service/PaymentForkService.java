package com.coinblesk.server.service;

import static com.coinblesk.dto.PaymentDecisionDTO.AddressType.BITCOIN;
import static com.coinblesk.dto.PaymentDecisionDTO.AddressType.EMAIL;
import static com.coinblesk.dto.PaymentDecisionDTO.PaymentInterface.DIRECT_PAYMENT;
import static com.coinblesk.dto.PaymentDecisionDTO.PaymentInterface.MICRO_PAYMENT;
import static com.coinblesk.dto.PaymentDecisionDTO.PaymentInterface.VIRTUAL_PAYMENT;
import static com.coinblesk.server.service.UserAccountService.EMAIL_PATTERN;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.coinblesk.dto.PaymentDecisionDTO;
import com.coinblesk.dto.PaymentDecisionDTO.AddressType;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.server.dao.TimeLockedAddressRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.exceptions.AccountNotFoundException;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.InvalidAddressException;
import com.coinblesk.server.exceptions.TooLittleFundsException;

@Service
public class PaymentForkService {
	private final static Logger LOG = LoggerFactory.getLogger(PaymentForkService.class);
	private final AccountRepository accountRepository;
	private final UserAccountService userAccountService;
	private final AppConfig appConfig;
	private final TimeLockedAddressRepository timeLockedAddressRepository;
	private final WalletService walletService;
	private final AccountService accountService;
	private final FeeService feeService;
	private final ForexBitcoinService forexService;

	@Autowired
	public PaymentForkService(AccountRepository accountRepository, UserAccountService userAccountService,
			AppConfig appConfig, TimeLockedAddressRepository timeLockedAddressRepository, WalletService walletService,
			AccountService accountService, FeeService feeService, ForexBitcoinService forexService) {
		this.accountRepository = accountRepository;
		this.userAccountService = userAccountService;
		this.appConfig = appConfig;
		this.timeLockedAddressRepository = timeLockedAddressRepository;
		this.walletService = walletService;
		this.accountService = accountService;
		this.feeService = feeService;
		this.forexService = forexService;
	}

	/*
	 * Decides on how an account can make a payment within the Coinblesk
	 * network: The user sends an e-mail or bitcoin address together with
	 * an amount. This algorithm decides on what payment interface should
	 * be used.
	 */
	public PaymentDecisionDTO getPaymentDecision(Account fromAccount, String receiver, Long amount)
			throws BusinessException {

		AddressType addressType = evaluateReceiverAddress(receiver);
		PaymentDecisionDTO dto = new PaymentDecisionDTO();
		dto.setAddressType(addressType);

		// TODO
		if (false /* not enough funds available */) {
			throw new TooLittleFundsException();
		}

		if (EMAIL.equals(addressType)) {
			if (userAccountService.userExists(receiver)) {
				UserAccount userAccount = userAccountService.getByEmail(receiver);
				if (userAccount.getAccount() == null) {
					throw new AccountNotFoundException();
				}
			}

			// if the receiving user exists, he must have a valid account to
			// transfer the virtual balance to if he does not exist, all the
			// required addresses will be created on a payment,

			if (evaluateVirtualPayment(fromAccount, amount)) {
				// virtual payment
				dto.setPaymentInterface(VIRTUAL_PAYMENT);
				dto.setAmount(amount);
				dto.setBitcoinAddress(null);

			} else {
				// micro payment
				// TODO
				long microChannelAmount = 0L;
				String bitcoinAddressOfServer = "dummyAddress";

				dto.setPaymentInterface(MICRO_PAYMENT);
				dto.setAmount(amount + microChannelAmount);
				dto.setBitcoinAddress(bitcoinAddressOfServer);
			}
		}

		else if (BITCOIN.equals(addressType)) {
			Address address = parseBitcoinAddress(receiver);
			TimeLockedAddressEntity tla = timeLockedAddressRepository.findByAddressHash(address.getHash160());

			// if tla is null, the address does not exist in the coinblesk
			// database of time locked addresses, therefore a transaction is
			// done directly.
			if (tla == null) {
				dto.setPaymentInterface(DIRECT_PAYMENT);
				dto.setAmount(amount);
				dto.setBitcoinAddress(address.toBase58());

			} else {
				// the address exists in the database, so a transaction can be done

				if (evaluateVirtualPayment(fromAccount, amount)) {
					// virtual payment
					dto.setPaymentInterface(VIRTUAL_PAYMENT);
					dto.setAmount(amount);
					dto.setBitcoinAddress(null);

				} else {
					// micro payment
					// TODO
					long microChannelAmount = 0L;
					String bitcoinAddressOfServer = "dummyAddress";

					dto.setPaymentInterface(MICRO_PAYMENT);
					dto.setAmount(amount + microChannelAmount);
					dto.setBitcoinAddress(bitcoinAddressOfServer);
				}
			}
		}

		return dto;
	}

	private PaymentDecisionDTO.AddressType evaluateReceiverAddress(String receiver) throws BusinessException {
		if (receiver.matches(EMAIL_PATTERN)) {
			return EMAIL;
		} else {
			try {
				parseBitcoinAddress(receiver);
			} catch (AddressFormatException e) {
				throw new InvalidAddressException();
			}
			return BITCOIN;
		}
	}

	private Address parseBitcoinAddress(String addressString) throws BusinessException {
		return Address.fromBase58(appConfig.getNetworkParameters(), addressString);
	}

	private boolean evaluateVirtualPayment(Account fromAccount, long amount) {
		return fromAccount.virtualBalance() >= amount;
	}

}
