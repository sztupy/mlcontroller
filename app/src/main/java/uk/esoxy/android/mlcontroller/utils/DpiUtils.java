package uk.esoxy.android.mlcontroller.utils;

import android.content.Context;
import android.util.TypedValue;

public class DpiUtils {
	static public int getDpi(Context c, int num) {
		return (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, num, c.getResources().getDisplayMetrics());
	}
}
