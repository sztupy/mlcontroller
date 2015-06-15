package uk.esoxy.android.mlcontroller.ptp.ml;

import uk.esoxy.android.mlcontroller.driver.IDeviceDriver;
import uk.esoxy.android.mlcontroller.ptp.PtpResult;
import uk.esoxy.android.mlcontroller.ptp.PtpTransaction;

public class MlMenu {
	private String name;
	private int id;
	private int groupId;
	private int data;
	private int min;
	private int max;
	private int flags;
	private String[] choices;

	public MlMenu(int groupId, String name, int id, int min, int max, int flags, int data, String[] choices) {
		this.groupId = groupId;
		this.name = name;
		this.id = id;
		this.data = data;
		this.min = min;
		this.max = max;
		this.flags = flags;
		this.choices = choices;
	}
	
	public MlMenu(int groupId, String name, int id) {
		this.groupId = groupId;
		this.name = name;
		this.id = id;
	}

	@Override
	public String toString() {
		return name.toString();
	}

	public int getGroupId() {
		return groupId;
	}

	public String getName() {
		return name;
	}

	public int getId() {
		return id;
	}

	public int getData() {
		return data;
	}

	public int getMin() {
		return min;
	}

	public int getMax() {
		return max;
	}

	public int getFlags() {
		return flags;
	}

	public int pressSelect(IDeviceDriver d) {
		PtpResult r = d.doTransaction(-24088, new int[] { 7, id, 0 } , PtpTransaction.DATA_NONE, null);
		if (r.getResult() == 0x2001) {
			data = r.getParams()[0];
		}
		return data;
	}
	
	public String[] getChoices() {
		return choices;
	}
}
