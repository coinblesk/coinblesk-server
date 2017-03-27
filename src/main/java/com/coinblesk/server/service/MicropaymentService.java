package com.coinblesk.server.service;

import com.coinblesk.server.dao.KeyRepository;
import com.coinblesk.server.entity.Keys;
import lombok.NonNull;
import org.bitcoinj.core.ECKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;

@Service
public class MicropaymentService {
	@Autowired KeyRepository keyRepository;

	public Keys getKeysEntityByPublicKey(@NonNull ECKey publicKey) throws EntityNotFoundException
	{
		Keys result = keyRepository.findByClientPublicKey(publicKey.getPubKey());
		if (result == null)
			throw new EntityNotFoundException("User with public key " + publicKey.getPublicKeyAsHex() + "not in system");

		return result;
	}
}
