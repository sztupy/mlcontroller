package uk.esoxy.android.mlcontroller.commands;

import java.util.ArrayList;

public class ChangeUSBPermissionsCommand extends ExecuteAsRootBase {

	public ChangeUSBPermissionsCommand() {
	}

	@Override
	protected ArrayList<String> getCommandsToExecute() {
		ArrayList<String> s = new ArrayList<String>();
		s.add("chmod 666 /dev/bus/usb/*/*"); 
		return s;
	}

}
