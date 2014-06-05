package ch.uzh.csg.mbps.server.util;

import java.math.BigDecimal;

public class Converter {
	
	public static BigDecimal getTransactionLongInBigDecimal(Long longValue){
		BigDecimal bigDecimalValue = new BigDecimal(longValue);
		bigDecimalValue.divide(new BigDecimal(100000000));
		return bigDecimalValue;
	}

}
