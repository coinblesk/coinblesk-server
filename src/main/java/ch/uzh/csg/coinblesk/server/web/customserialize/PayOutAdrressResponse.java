package ch.uzh.csg.coinblesk.server.web.customserialize;

import java.nio.charset.Charset;

import ch.uzh.csg.coinblesk.customserialization.PKIAlgorithm;
import ch.uzh.csg.coinblesk.customserialization.SignedSerializableObject;
import ch.uzh.csg.coinblesk.customserialization.exceptions.IllegalArgumentException;
import ch.uzh.csg.coinblesk.customserialization.exceptions.NotSignedException;
import ch.uzh.csg.coinblesk.customserialization.exceptions.SerializationException;

/**
 * This class represents a payout address response, which is transferred between two servers. 
 * The byte array serialization allows keeping the payload and
 * signature as as small as possible.
 * 
 * 
 */
public class PayOutAdrressResponse extends SignedSerializableObject{

	private String payOutAddressResponse;
	
	protected PayOutAdrressResponse(){}
	
	public String getPayOutAddressResponse() {
		return payOutAddressResponse;
	}

	public void setPayOutAddressResponse(String payOutAddressResponse) {
		this.payOutAddressResponse = payOutAddressResponse;
	}

	/**
	 * This constructor instantiates a new object.
	 * 
	 * @param pkiAlgorithm
	 *            the {@link PKIAlgorithm} to be used for
	 *            {@link SignedSerializableObject} super class
	 * @param keyNumber
	 *            the key number to be used for the
	 *            {@link SignedSerializableObject} super class
	 * @param payOutAddress
	 *            the payout address
	 * @throws IllegalArgumentException
	 *             if any argument is null or does not fit into the foreseen
	 *             primitive type
	 */
	public PayOutAdrressResponse(int version, PKIAlgorithm pkiAlgorithm, int keyNumber, String payOutAddress) throws IllegalArgumentException{		
		super(version, pkiAlgorithm,keyNumber);
		payOutAddressResponse = payOutAddress;
		setPayload();
	}
	
	public void setPayload(){
		byte[] payOutAddressResponseRaw = payOutAddressResponse.getBytes(Charset.forName("UTF-8"));
		int length;
		/*
		 * version
		 * + signatureAlgorithm.getCode()
		 * + keyNumber
		 * + payOutAddressResponseRaw.length
		 * + payOutAddressResponseRaw
		 */
		length = 1+1+1+1+payOutAddressResponseRaw.length;
		
		byte[] payload = new byte[length];
		
		int index = 0;
		payload[index++] = (byte) getVersion();
		payload[index++] = getPKIAlgorithm().getCode();
		payload[index++] = (byte) getKeyNumber();
		payload[index++] = (byte) payOutAddressResponseRaw.length;
		for(byte b: payOutAddressResponseRaw) {
			payload[index++] = b;
		}
		
		this.payload = payload;
		
	}
	
	@Override
	public PayOutAdrressResponse decode(byte[] bytes) throws IllegalArgumentException, SerializationException {
		if (bytes == null)
			throw new IllegalArgumentException("The argument can't be null.");
		
		try {
			int index = 0;
			
			int version = bytes[index++] & 0xFF;
			PKIAlgorithm pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(bytes[index++]);
			int keyNumber = bytes[index++] & 0xFF;
			int payOutAddressResponseLength = bytes[index++] & 0xFF;
			byte[] payOutAddressResponseBytes = new byte[payOutAddressResponseLength];
			for(int i = 0; i < payOutAddressResponseLength; i++){
				payOutAddressResponseBytes[i] = bytes[index++];
			}
			String payOutAddressResponse = new String(payOutAddressResponseBytes);
			
			PayOutAdrressResponse par = new PayOutAdrressResponse(version, pkiAlgorithm, keyNumber, payOutAddressResponse);
			
			int signatureLength = bytes.length - index;
			if (signatureLength == 0) {
				throw new NotSignedException();
			} else {
				byte[] signature = new byte[signatureLength];
				for (int i=0; i<signature.length; i++) {
					signature[i] = bytes[index++];
				}
				par.signature = signature;
			}
			
			return par;
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("The given byte array is corrupt (not long enough).");
		}
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof PayOutAdrressResponse))
			return false;
		
		
		PayOutAdrressResponse other = (PayOutAdrressResponse) o;
		if (getVersion() != other.getVersion())
			return false;
		if (getPKIAlgorithm().getCode() != other.getPKIAlgorithm().getCode())
			return false;
		if (getKeyNumber() != other.getKeyNumber())
			return false;
		if (!this.payOutAddressResponse.equals(other.payOutAddressResponse))
			return false;
		
		return true;
	}

}
