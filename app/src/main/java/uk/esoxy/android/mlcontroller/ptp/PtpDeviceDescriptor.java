package uk.esoxy.android.mlcontroller.ptp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PtpDeviceDescriptor {
	private int standardVersion;
	private int vendorExtension;
	private int mtpVersion;
	private String mtpExtensions;
	private int functionalMode;
	private int[] operations;
	private int[] events;
	private int[] deviceProperties;
	private int[] captureFormats;
	private int[] playbackFormats;
	private String manufacturer;
	private String model;
	private String deviceVersion;
	private String serialNumber;

	public PtpDeviceDescriptor(byte[] data) {
		ByteBuffer b = ByteBuffer.wrap(data);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.position(0);
		standardVersion = b.getShort();
		vendorExtension = b.getInt();
		mtpVersion = b.getShort();
        mtpExtensions = PtpDataUtils.getString(b);
        functionalMode = b.getShort();
        operations = PtpDataUtils.getArray(b, 2);
        events = PtpDataUtils.getArray(b, 2);
        deviceProperties = PtpDataUtils.getArray(b, 2);
        captureFormats =  PtpDataUtils.getArray(b, 2);
        playbackFormats =  PtpDataUtils.getArray(b, 2);
        manufacturer = PtpDataUtils.getString(b);
        model = PtpDataUtils.getString(b);
        deviceVersion = PtpDataUtils.getString(b);
        serialNumber =  PtpDataUtils.getString(b);
	}

	public int getStandardVersion() {
		return standardVersion;
	}

	public int getVendorExtension() {
		return vendorExtension;
	}

	public int getMtpVersion() {
		return mtpVersion;
	}

	public String getMtpExtensions() {
		return mtpExtensions;
	}

	public int getFunctionalMode() {
		return functionalMode;
	}

	public int[] getOperations() {
		return operations;
	}

	public int[] getEvents() {
		return events;
	}

	public int[] getDeviceProperties() {
		return deviceProperties;
	}

	public int[] getCaptureFormats() {
		return captureFormats;
	}

	public int[] getPlaybackFormats() {
		return playbackFormats;
	}

	public String getManufacturer() {
		return manufacturer;
	}

	public String getModel() {
		return model;
	}

	public String getDeviceVersion() {
		return deviceVersion;
	}

	public String getSerialNumber() {
		return serialNumber;
	}}
