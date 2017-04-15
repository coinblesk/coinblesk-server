package com.coinblesk.server.utils;

import java.util.Calendar;
import java.util.Date;

import org.bitcoinj.core.ECKey;

import com.coinblesk.json.v1.BaseTO;
import com.coinblesk.json.v1.Type;
import com.coinblesk.util.SerializeUtils;

public final class ToUtils {

	private ToUtils() {
		// prevent instances
	}

	private static <K extends BaseTO> K newInstance(K k, Type returnType) {
		return newInstance(k.getClass(), returnType);
	}

	private static <K extends BaseTO> K newInstance(Class<? extends BaseTO> clazz, Type returnType) {
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
		return checkInput(input, true);
	}

	private static <K extends BaseTO> K checkInput(final K input, boolean checkDate) {

		if (!input.isInputSet()) {
			return newInstance(input, Type.INPUT_MISMATCH);
		}

		if (checkDate) {
			// check if the client sent us a time which is way too old (1 day)
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
		}

		if (!SerializeUtils.verifyJSONSignature(input, ECKey.fromPublicOnly(input.publicKey()))) {
			return newInstance(input, Type.JSON_SIGNATURE_ERROR);

		}
		return null;
	}

}
