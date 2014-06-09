package ch.uzh.csg.mbps.server.util;

import java.math.BigDecimal;

//TODO: javadoc
public class Converter {
	
	//TODO jeton: move to shared resources?
	
	//	TODO simon: check conversion long/bigDecimal	
	
	/**
	 * Converts a long value back to its corresponding BigDecimal value by dividing.
	 * 
	 * @param longValue
	 * @return BigDecimal
	 */
	public static BigDecimal getBigDecimalFromLong(long longValue){
		BigDecimal bigDecimalValue = new BigDecimal(longValue);
		bigDecimalValue.divide(new BigDecimal(100000000));
		return bigDecimalValue;
	}
	
	/**
	 * Converts a BigDecimal value with 8 decimal places into a long value by multiplying.
	 *  
	 * @param bigDecimalValue
	 * @return bigDecimal as long
	 */
	public static long getLongFromBigDecimal(BigDecimal bigDecimalValue){
		bigDecimalValue.multiply(new BigDecimal(100000000));
		return bigDecimalValue.longValue();
	}

}
