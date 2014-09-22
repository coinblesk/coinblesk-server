package ch.uzh.csg.mbps.server.web.customserialize;

import ch.uzh.csg.mbps.customserialization.DecoderFactory;
import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.PrimitiveTypeSerializer;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.customserialization.SignedSerializableObject;
import ch.uzh.csg.mbps.customserialization.exceptions.IllegalArgumentException;
import ch.uzh.csg.mbps.customserialization.exceptions.NotSignedException;
import ch.uzh.csg.mbps.customserialization.exceptions.SerializationException;

/**
 * This class represents a payment response, which is transferred between two servers
 * during a NFC connection. The byte array serialization allows keeping the payload and
 * signature as small as possible, which is important especially for the NFC.
 *
 */
public class CustomServerPaymentResponse extends SignedSerializableObject {
private static final int NOF_BYTES_FOR_PAYLOAD_LENGTH = 2; // 2 bytes for the payload length, up to 65536 bytes
	
	byte [] serverPaymentResponseRaw;
	
	protected CustomServerPaymentResponse(){
		
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
	 * @throws ch.uzh.csg.mbps.customserialization.exceptions.IllegalArgumentException
	 * @throws NotSignedException
	 */
	public CustomServerPaymentResponse(int version, PKIAlgorithm pkiAlgorithm, int keyNumber, ServerPaymentResponse serverPaymentResponse) throws ch.uzh.csg.mbps.customserialization.exceptions.IllegalArgumentException, NotSignedException{
		super(version, pkiAlgorithm, keyNumber);
		setServerPaymentResponsetRaw(serverPaymentResponse.encode());
		setPayload();
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
		outputLength = 1+1+1+NOF_BYTES_FOR_PAYLOAD_LENGTH+serverPaymentResponseRaw.length;
		
		byte[] payload = new byte[outputLength];
		
		int index = 0;
		payload[index++] = (byte) getVersion();
		payload[index++] = getPKIAlgorithm().getCode();
		payload[index++] = (byte) getKeyNumber();
		
		byte[] serverPaymentResponseRawLength = PrimitiveTypeSerializer.getShortAsBytes((short) serverPaymentResponseRaw.length);
		for (byte b : serverPaymentResponseRawLength) {
			payload[index++] = b;
		}
		for (byte b : serverPaymentResponseRaw) {
			payload[index++] = b;
		}
		
		this.payload = payload;
	}
	
	public byte[] getServerPaymentResponsetRaw() {
		return serverPaymentResponseRaw;
	}
	
	private void setServerPaymentResponsetRaw(byte[] serverPaymentResponseRaw) {
		this.serverPaymentResponseRaw = serverPaymentResponseRaw;
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
	public CustomServerPaymentResponse decode(byte[] bytes) throws IllegalArgumentException, SerializationException {
		if(bytes == null)
			throw new IllegalArgumentException("The argument can't be null.");
		
		try{
			int index = 0;
			
			int version = bytes[index++] & 0xFF;
			PKIAlgorithm pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(bytes[index++]);
			int keyNumber = bytes[index++] & 0xFF;
			
			byte[] indicatedLengthResponse = new byte[NOF_BYTES_FOR_PAYLOAD_LENGTH];
			for(int i = 0;i < NOF_BYTES_FOR_PAYLOAD_LENGTH; i++){
				indicatedLengthResponse[i] = bytes[index++];
			}
			
			int serverPaymentResponseLength =(PrimitiveTypeSerializer.getBytesAsShort(indicatedLengthResponse) & 0xFF);
			byte[] serverPaymentResponseBytes = new byte[serverPaymentResponseLength];
			for(int i = 0; i < serverPaymentResponseLength; i++) {
				serverPaymentResponseBytes[i] = bytes[index++];
			}
			ServerPaymentResponse serverPaymentResponse = DecoderFactory.decode(ServerPaymentResponse.class,serverPaymentResponseBytes);
			
			CustomServerPaymentResponse cspr = new CustomServerPaymentResponse(version, pkiAlgorithm, keyNumber, serverPaymentResponse);
			
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
		if (!(o instanceof CustomServerPaymentResponse))
			return false;
		
		CustomServerPaymentResponse spr = (CustomServerPaymentResponse) o;
		if (getVersion() != spr.getVersion())
			return false;
		if (!getServerPaymentResponsetRaw().equals(spr.getServerPaymentResponsetRaw()))
			return false;
		
		return true;
	}

}
