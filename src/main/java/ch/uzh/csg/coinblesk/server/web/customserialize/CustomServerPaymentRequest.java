package ch.uzh.csg.coinblesk.server.web.customserialize;

import ch.uzh.csg.coinblesk.customserialization.DecoderFactory;
import ch.uzh.csg.coinblesk.customserialization.PKIAlgorithm;
import ch.uzh.csg.coinblesk.customserialization.PrimitiveTypeSerializer;
import ch.uzh.csg.coinblesk.customserialization.ServerPaymentRequest;
import ch.uzh.csg.coinblesk.customserialization.SignedSerializableObject;
import ch.uzh.csg.coinblesk.customserialization.exceptions.IllegalArgumentException;
import ch.uzh.csg.coinblesk.customserialization.exceptions.NotSignedException;
import ch.uzh.csg.coinblesk.customserialization.exceptions.SerializationException;

/**
 * This class represents a payment request, which is transferred between two servers
 * during a NFC connection. The byte array serialization allows keeping the payload and
 * signature as small as possible, which is important especially for the NFC.
 *
 */
public class CustomServerPaymentRequest extends SignedSerializableObject {
	private static final int NOF_BYTES_FOR_PAYLOAD_LENGTH = 2; // 2 bytes for the payload length, up to 65536 bytes
	
	byte [] serverPaymentRequestRaw;
	
	protected CustomServerPaymentRequest(){
		
	}

	/**
	 * Instantiates a new object.
	 * 
	 * @param version
	 * @param pkiAlgorithm
	 *            	the {@link PKIAlgorithm} to be used for
	 *            	{@link SignedSerializableObject} super class
	 * @param keyNumber
	 * 				the key number to be used for the
	 *            	{@link SignedSerializableObject} super class
	 * @param serverPaymentRequest
	 * 				the requested payment data
	 * @throws ch.uzh.csg.coinblesk.customserialization.exceptions.IllegalArgumentException
	 * @throws NotSignedException
	 */
	public CustomServerPaymentRequest(int version, PKIAlgorithm pkiAlgorithm, int keyNumber, ServerPaymentRequest serverPaymentRequest) throws ch.uzh.csg.coinblesk.customserialization.exceptions.IllegalArgumentException, NotSignedException{
		super(version, pkiAlgorithm, keyNumber);
		setServerPaymentRequestRaw(serverPaymentRequest.encode());
		setPayload();
	}
	
	public byte[] getServerPaymentRequestRaw() {
		return serverPaymentRequestRaw;
	}
	
	private void setServerPaymentRequestRaw(byte[] serverPaymentRequestRaw) {
		this.serverPaymentRequestRaw = serverPaymentRequestRaw;
	}
	
	private void setPayload(){
		int outputLength;
		/*
		 * version
		 * + signatureAlgorithm.getCode()
		 * + keyNumber
		 * + serverPaymentRequestRaw.lenth
		 * + serverPaymentRequestRaw
		 * + signature
		 */
		outputLength = 1+1+1+1+NOF_BYTES_FOR_PAYLOAD_LENGTH+serverPaymentRequestRaw.length;
		
		byte[] payload = new byte[outputLength];
		
		int index = 0;
		payload[index++] = (byte) getVersion();
		payload[index++] = getPKIAlgorithm().getCode();
		payload[index++] = (byte) getKeyNumber();
		
		byte[] serverPaymentRequestRawLength = PrimitiveTypeSerializer.getShortAsBytes((short) serverPaymentRequestRaw.length);
		for (byte b : serverPaymentRequestRawLength) {
			payload[index++] = b;
		}
		for (byte b : serverPaymentRequestRaw) {
			payload[index++] = b;
		}
		
		this.payload = payload;
	}
	
	@Override
	public byte[] encode() throws NotSignedException {
		if (signature == null)
			throw new NotSignedException();
		
		int index = 0;
		byte[] result = new byte[payload.length+signature.length];
		for (byte b : payload) {
			result[index++] = b;
		}
		for (byte b : signature) {
			result[index++] = b;
		}
		
		return result;
	}
	
	
	@Override
	public CustomServerPaymentRequest decode(byte[] bytes) throws IllegalArgumentException, SerializationException {
		if(bytes == null)
			throw new IllegalArgumentException("The argument can't be null.");
		
		try{
			int index = 0;
			
			int version = bytes[index++] & 0xFF;
			PKIAlgorithm pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(bytes[index++]);
			int keyNumber = bytes[index++] & 0xFF;
			
			byte[] indicatedLengthRequest = new byte[NOF_BYTES_FOR_PAYLOAD_LENGTH];
			for(int i = 0;i < NOF_BYTES_FOR_PAYLOAD_LENGTH; i++){
				indicatedLengthRequest[i] = bytes[index++];
			}
			
			int serverPaymentRequestLength =(PrimitiveTypeSerializer.getBytesAsShort(indicatedLengthRequest) & 0xFF);
			byte[] serverPaymentRequestBytes = new byte[serverPaymentRequestLength];
			for(int i = 0; i < serverPaymentRequestLength; i++) {
				serverPaymentRequestBytes[i] = bytes[index++];
			}
			ServerPaymentRequest serverPaymentRequest = DecoderFactory.decode(ServerPaymentRequest.class,serverPaymentRequestBytes);
			
			CustomServerPaymentRequest cspr = new CustomServerPaymentRequest(version, pkiAlgorithm, keyNumber, serverPaymentRequest);
			
			int signatureLength = bytes.length - index;
			if (signatureLength == 0) {
				throw new NotSignedException();
			} else {
				byte[] signature = new byte[signatureLength];
				for (int i=0; i<signature.length; i++) {
					signature[i] = bytes[index++];
				}
				cspr.signature = signature;
			}
			
			return cspr;
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("The given byte array is corrupt (not long enough).");
		}
	}

	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (!(o instanceof CustomServerPaymentRequest))
			return false;
		
		CustomServerPaymentRequest spr = (CustomServerPaymentRequest) o;
		if (getVersion() != spr.getVersion())
			return false;
		if (!getServerPaymentRequestRaw().equals(spr.getServerPaymentRequestRaw()))
			return false;
		
		return true;
	}

}
