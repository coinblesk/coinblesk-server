package com.coinblesk.server.utils;


import org.bitcoinj.core.ECKey;

import com.coinblesk.json.v1.BaseTO;
import com.coinblesk.json.v1.Type;
import com.coinblesk.util.SerializeUtils;
import java.util.Calendar;
import java.util.Date;

public final class ToUtils {
	
	private ToUtils() {
		// prevent instances
	}
	
	public static <K extends BaseTO> K newInstance(K k, Type returnType, ECKey signKey) {
    	return newInstance(k.getClass(), returnType, signKey);
    }
    
    public static <K extends BaseTO> K newInstance(Class<? extends BaseTO> clazz, Type returnType, ECKey signKey) {
        K instance = newInstance(clazz, returnType);
        if (instance == null) {
        	return instance;
        }
        instance.publicKey(signKey.getPubKey());
        SerializeUtils.signJSON(instance, signKey);
        return instance;
    }
    
    public static <K extends BaseTO> K newInstance(K k, Type returnType) {
    	return newInstance(k.getClass(), returnType);
    }
    
    public static <K extends BaseTO> K newInstance(Class<? extends BaseTO> clazz, Type returnType) {
    	try {
	    	BaseTO b = clazz.newInstance();
	    	b.currentDate(System.currentTimeMillis());
	        b.type(returnType);
	        return (K) b;
    	} catch (InstantiationException | IllegalAccessException ex) {
            throw new RuntimeException("Cannot ceate instance", ex);
        }
    }

    public static <K extends BaseTO> K checkInput(final K input) {

        if (!input.isInputSet()) {
            return newInstance(input, Type.INPUT_MISMATCH);
        }

        
        //check if the client sent us a time which is way too old (1 day)
        final Calendar fromClient = Calendar.getInstance();
        fromClient.setTime(new Date(input.currentDate()));

        final Calendar fromServerDayBefore = Calendar.getInstance();
        fromServerDayBefore.add(Calendar.DAY_OF_YEAR, -1);

        if (fromClient.before(fromServerDayBefore)) {
            return newInstance(input, Type.TIME_MISMATCH);
        }

        final Calendar fromServerDayAfter = Calendar.getInstance();
        fromServerDayAfter.add(Calendar.DAY_OF_YEAR, 1);

        if (fromClient.after(fromServerDayAfter)) {
            return newInstance(input, Type.TIME_MISMATCH);
        }

        
        if (!SerializeUtils.verifyJSONSignature(input, ECKey.fromPublicOnly(input.publicKey()))) {
            return newInstance(input, Type.JSON_SIGNATURE_ERROR);

        }
        return null;
    }
	
}
