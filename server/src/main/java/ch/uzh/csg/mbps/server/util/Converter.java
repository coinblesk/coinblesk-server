package ch.uzh.csg.mbps.server.util;

import java.math.BigDecimal;

//TODO: javadoc
public class Converter {
	
	//TODO jeton: move to shared resources?
	
	//TODO jeton: bigdecimal to long
	
	public static BigDecimal getTransactionLongInBigDecimal(long longValue){
		BigDecimal bigDecimalValue = new BigDecimal(longValue);
		bigDecimalValue.divide(new BigDecimal(100000000));
		return bigDecimalValue;
	}

}
