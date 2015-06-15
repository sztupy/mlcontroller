#include <jni.h>
#include <time.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <linux/ioctl.h>
#include <linux/fs.h>
#include <linux/hdreg.h>
#include <linux/usbdevice_fs.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <fcntl.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "usbhost.h"
#include "setjmp.h"

#include <cpu-features.h>

#ifdef BUILD_NEON
#include "jpeglib.h"
#endif

#define  LOG_TAG    "MLCTRL_native"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

/* Set to 1 to enable debug log traces. */
#define DEBUG 0

#define USB_BUFFER_SIZE 16384

static jfieldID structId = 0;
static void* usbBuffer = 0;
static int transId = 1;

#ifdef BUILD_NEON
extern void yuv422_2_rgb8888_neon(uint8_t *dst_ptr,const uint8_t *y_ptr,int width,int height,int yuv_pitch,int rgb_pitch);
extern void yuv422_2_rgb8888_neon_grayscale(uint8_t *dst_ptr,const uint8_t *y_ptr,int width,int height,int yuv_pitch,int rgb_pitch);
#endif

struct ptp_transaction_result {
	int32_t result;
	uint32_t params[5];
	uint32_t paramCount;
	uint32_t need_dealloc;
	uint32_t dataPtrSize;
	uint32_t dataSize;
	uint8_t* data;
};

struct usb_device* get_device(JNIEnv * env, jobject thiz) {
	if (structId)
		return (struct usb_device*) ((*env)->GetIntField(env, thiz, structId));
	else
		return 0;
}

int find_device_rec(char* retval, char* dirname, int maj, int min) {
	DIR *dir;
	struct dirent * dent;
	struct stat st;
	int rval;
	char ndir[FILENAME_MAX];
	dir = opendir(dirname);
	if (dir) {
		while ((dent = readdir(dir)) != NULL) {
			if (dent->d_name[0] == '.')
				continue;
			sprintf(ndir, "%s/%s", dirname, dent->d_name);
			stat(ndir, &st);
			if (S_ISDIR(st.st_mode)) {
				rval = find_device_rec(retval, ndir, maj, min);
				if (!rval)
					return rval;
			}
			if (major(st.st_rdev) == maj && minor(st.st_rdev) == min) {
				strcpy(retval, ndir);
				return 0;
			}
		}
	}
	return -1;
}

JNIEXPORT jboolean JNICALL Java_uk_esoxy_android_mlcontroller_driver_NativeDevice_nativeOpen(
		JNIEnv * env, jobject thiz, jboolean reset) {
	jclass cls = (*env)->GetObjectClass(env, thiz);
	jfieldID devId = (*env)->GetFieldID(env, cls, "dev", "Ljava/lang/String;");
	jstring devStr = (*env)->GetObjectField(env, thiz, devId);
	const char* dev = (*env)->GetStringUTFChars(env, devStr, NULL);
	char devname[FILENAME_MAX];
	int major;
	int minor;
	int retval = 0;
	struct usb_device* device;

	if (!structId) {
		structId = (*env)->GetFieldID(env, cls, "nativeStruct", "I");
	}

	sscanf(dev, "%d:%d", &major, &minor);

	LOGI("Device major/minor: %d/%d", major, minor);

	if (!find_device_rec(devname, "/dev/bus/usb", major, minor)) {
		LOGI("Device location: %s", devname);
		device = usb_device_open(devname);
		if (device) {
			if (reset) {
				ioctl(device->fd, USBDEVFS_RESET, 0);
			}
			int config = 1;
			LOGI("Device opened");
			// this resets the device usually
			if (reset) {
				ioctl(device->fd, USBDEVFS_SETCONFIGURATION, &config);
			}
			(*env)->SetIntField(env, thiz, structId, (jint) device);
			retval = 1;
		}
	}

	if (!retval)
		LOGE("Device could not be opened");

	(*env)->ReleaseStringUTFChars(env, devStr, dev);
	return retval;
}

JNIEXPORT void JNICALL Java_uk_esoxy_android_mlcontroller_driver_NativeDevice_nativeClose(
		JNIEnv * env, jobject thiz) {
	struct usb_device* device = get_device(env, thiz);
	usb_device_close(device);
}

JNIEXPORT jint JNICALL Java_uk_esoxy_android_mlcontroller_driver_NativeDevice_nativeClaimInterface(
		JNIEnv * env, jobject thiz, jint interfaceID) {
	struct usb_device* device = get_device(env, thiz);
	if (!device) {
		LOGE("device is closed in native_claim_interface");
		return -1;
	}

	int ret = usb_device_claim_interface(device, interfaceID);
	if (ret && errno == EBUSY) {
		usb_device_connect_kernel_driver(device, interfaceID, 0);
		ret = usb_device_claim_interface(device, interfaceID);
	}
	return ret;
}

JNIEXPORT jint JNICALL Java_uk_esoxy_android_mlcontroller_driver_NativeDevice_nativeReleaseInterface(
		JNIEnv * env, jobject thiz, jint interfaceID) {
	struct usb_device* device = get_device(env, thiz);
	if (!device) {
		LOGE("device is closed in native_release_interface");
		return -1;
	}
	int ret = usb_device_release_interface(device, interfaceID);
	if (ret == 0) {
		// allow kernel to reconnect its driver
		usb_device_connect_kernel_driver(device, interfaceID, 1);
	}
	return ret;
}

JNIEXPORT jboolean JNICALL Java_uk_esoxy_android_mlcontroller_driver_NativeDevice_nativeCheckPtp(
		JNIEnv * env, jobject thiz, jint interfaceID) {
	struct usb_device* device = get_device(env, thiz);
	struct usb_descriptor_header * desc;
	struct usb_descriptor_iter iter;
	int inEndpoint = 0;
	int outEndpoint = 0;
	usb_descriptor_iter_init(device, &iter);
	while ((desc = usb_descriptor_iter_next(&iter)) != NULL) {
		if (desc->bDescriptorType == USB_DT_INTERFACE) {
		} else if (desc->bDescriptorType == USB_DT_ENDPOINT) {
			struct usb_endpoint_descriptor *endpoint =
					(struct usb_endpoint_descriptor *) desc;
			LOGI("endpoint found: %02x %02x", endpoint->bmAttributes, endpoint->bEndpointAddress);
			if (endpoint->bmAttributes == 0x02) { // BULK
				if (endpoint->bEndpointAddress & 0x80)
					inEndpoint = endpoint->bEndpointAddress;
				else
					outEndpoint = endpoint->bEndpointAddress;
			}
		}
	}
	{
		jclass cls = (*env)->GetObjectClass(env, thiz);
		jfieldID devId = (*env)->GetFieldID(env, cls, "inEndpoint", "I");
		(*env)->SetIntField(env, thiz, devId, inEndpoint);
		devId = (*env)->GetFieldID(env, cls, "outEndpoint", "I");
		(*env)->SetIntField(env, thiz, devId, outEndpoint);
		device->inEndpoint = inEndpoint;
		device->outEndpoint = outEndpoint;
	}
	return inEndpoint != 0 && outEndpoint != 0;
}

void JNU_ThrowByName(JNIEnv *env, const char *name, const char *msg) {
	jclass cls = (*env)->FindClass(env, name);
	/* if cls is NULL, an exception has already been thrown */
	if (cls != NULL) {
		(*env)->ThrowNew(env, cls, msg);
	}
	/* free the local ref */
	(*env)->DeleteLocalRef(env, cls);
}

JNIEXPORT jint JNICALL Java_uk_esoxy_android_mlcontroller_driver_NativeDevice_nativeBulkTransfer(
		JNIEnv * env, jobject thiz, jint endpoint, jbyteArray buffer,
		jint length, jint timeout) {
	struct usb_device* device = get_device(env, thiz);
	jint result;
	jbyte* bufferBytes = NULL;

	if (!device) {
		LOGE("device is closed in native_control_request");
		return -1;
	}

	if (buffer) {
		if ((*env)->GetArrayLength(env, buffer) < length) {
			JNU_ThrowByName(env, "java/lang/ArrayIndexOutOfBoundsException",
					NULL);
			return -1;
		}
		bufferBytes = (*env)->GetByteArrayElements(env, buffer, 0);
	}

	result = usb_device_bulk_transfer(device, endpoint, bufferBytes, length,
			timeout);

	if (bufferBytes)
		(*env)->ReleaseByteArrayElements(env, buffer, bufferBytes, 0);

	return result;
}

struct ptp_transaction_result do_ptp_transaction(struct usb_device* device,
		uint16_t mOpcode, uint32_t* mParams, uint8_t mParamCount,
		uint8_t mDirection, uint8_t* mData, uint32_t mDataLength) {
	struct ptp_transaction_result result;
	memset(&result, 0, sizeof(struct ptp_transaction_result));
	int32_t size = 12 + mParamCount * 4;
	uint16_t type = 0;
	uint16_t opcode = 0;
	uint32_t trid = 0;
	uint32_t done = 0;
	uint32_t mTrId = transId++;
	uint32_t i;
	int32_t data = 0;
	uint8_t* b;
	if (usbBuffer == NULL)
		usbBuffer = malloc(USB_BUFFER_SIZE);
	b = usbBuffer;
	memset(b, 0, size);
	*((uint32_t*) (b)) = size;
	b[4] = 1;
	b[5] = 0;
	b[6] = mOpcode & 0xFF;
	b[7] = (mOpcode >> 8) & 0xFF;
	*((uint32_t*) (b + 8)) = mTrId;
	memcpy(b + 12, mParams, mParamCount * 4);
	data = usb_device_bulk_transfer(device, device->outEndpoint, b, size, 1000);

	if (data < size)
		goto usb_invalid;
	if (mDirection == 1) {
		b = usbBuffer;
		memset(b, 0, 12);
		*((unsigned int*) b) = mDataLength + 12;
		b[4] = 2;
		b[5] = 0;
		b[6] = mOpcode & 0xFF;
		b[7] = (mOpcode >> 8) & 0xFF;
		*((unsigned int*) (b + 8)) = mTrId;
		data = usb_device_bulk_transfer(device, device->outEndpoint, b, 12,
				1000);
		if (data != 12)
			goto usb_invalid;
		data = usb_device_bulk_transfer(device, device->outEndpoint, mData,
				mDataLength, 1000);
		if (data < 0)
			goto usb_invalid;
	} else if (mDirection == 2) {
		b = usbBuffer;
		type = 0;
		opcode = 0;
		trid = 0;
		int remaining = 0;
		while (!((type == 2 || type == 3) && (opcode == mOpcode || type == 3)
				&& trid == mTrId)) {
			data = usb_device_bulk_transfer(device, device->inEndpoint, b,
					USB_BUFFER_SIZE, 1000);
			if (data < 12)
				goto usb_invalid;
			size = ((uint32_t*) b)[0];
			type = ((uint16_t*) b)[2];
			opcode = ((uint16_t*) b)[3];
			trid = ((uint32_t*) b)[2];
			remaining = size - data;
			done = data - 12;
		}
		// we might have got a result dataset already, skip reading the input
		if (type == 2) {
			// we need to read more data
			if (mData != NULL && mDataLength >= size - 12) {
				result.data = mData;
				result.dataPtrSize = mDataLength;
			} else {
				result.data = malloc(size - 12);
				result.need_dealloc = 1;
				result.dataPtrSize = size - 12;
			}
			result.dataSize = size - 12;
			memcpy(result.data, b + 12, data - 12);
			while (remaining > 0) {
				b = usbBuffer;
				data = usb_device_bulk_transfer(device, device->inEndpoint, b,
						USB_BUFFER_SIZE, 1000);
				if (data < 0)
					goto usb_invalid;
				memcpy(result.data + done, b, data);
				remaining -= data;
				done += data;
			}
		}
	}

	if (type != 3) {
		b = usbBuffer;
		type = 0;
		opcode = 0;
		trid = 0;
		while (type != 3 || trid != mTrId) {
			data = usb_device_bulk_transfer(device, device->inEndpoint, b,
					USB_BUFFER_SIZE, 1000);
			if (data < 12)
				goto usb_invalid;
			size = ((uint32_t*) b)[0];
			type = ((uint16_t*) b)[2];
			opcode = ((uint16_t*) b)[3];
			trid = ((uint32_t*) b)[2];
		}
	}

	result.result = opcode;
	result.paramCount = (size - 12) / 4;
	for (i = 0; i < result.paramCount; i++) {
		result.params[i] = ((uint32_t*) b)[3 + i];
	}
	return result;

usb_invalid:
	if (result.need_dealloc)
		free(result.data);
	memset(&result, 0, sizeof(result));
	result.result = data;
	return result;
}

JNIEXPORT jobject JNICALL Java_uk_esoxy_android_mlcontroller_driver_NativeDevice_nativePtpTransaction(
		JNIEnv * env, jobject thiz, jint mOpcode, jintArray mParams,
		jint mDirection, jbyteArray mData) {

	struct usb_device* device = get_device(env, thiz);
	struct ptp_transaction_result result;
	int paramCount = (*env)->GetArrayLength(env, mParams);
	jint* params = (*env)->GetIntArrayElements(env, mParams, NULL);
	int dataCount = 0;
	jbyte* data = NULL;
	jbyteArray resArray = NULL;

	if (mData) {
		dataCount = (*env)->GetArrayLength(env, mData);
		data = (*env)->GetByteArrayElements(env, mData, NULL);
	}

	result = do_ptp_transaction(device, mOpcode, params, paramCount, mDirection,
			data, dataCount);
	if (result.need_dealloc) {
		resArray = (*env)->NewByteArray(env, result.dataPtrSize);
		(*env)->SetByteArrayRegion(env, resArray, 0, result.dataPtrSize,
				result.data);
		free(result.data);
	} else if (result.data) {
		resArray = mData;
	}

	jintArray resParams = (*env)->NewIntArray(env, result.paramCount);
	(*env)->SetIntArrayRegion(env, resParams, 0, result.paramCount,
			result.params);

	jclass resClass = (*env)->FindClass(env,
			"uk/esoxy/android/mlcontroller/ptp/PtpResult");
	jmethodID constructor = (*env)->GetMethodID(env, resClass, "<init>",
			"(I[I[BI)V");
	jobject r = (*env)->NewObject(env, resClass, constructor, result.result,
			resParams, resArray, result.dataSize);

	if (data)
		(*env)->ReleaseByteArrayElements(env, mData, data, 0);

	if (params)
		(*env)->ReleaseIntArrayElements(env, mParams, params, 0);

	return r;
}

static unsigned char* readData = 0;
static uint32_t readDataSize = 0;

int getNeon() {
	return (android_getCpuFamily() == ANDROID_CPU_FAMILY_ARM && (android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_NEON) != 0);
}

#ifdef BUILD_NEON
struct my_error_mgr {
  struct jpeg_error_mgr pub;	/* "public" fields */
  jmp_buf setjmp_buffer;	/* for return to caller */
};

METHODDEF(void)
my_error_exit (j_common_ptr cinfo)
{
	struct my_error_mgr * myerr = (struct my_error_mgr *) cinfo->err;
	(*cinfo->err->output_message) (cinfo);
    longjmp(myerr->setjmp_buffer, 1);
}

struct jpeg_decompress_struct * cinfo = 0;

jobject create_bitmap_from_data(JNIEnv* env, jobject bitmap, uint8_t* data, uint32_t dataSize, jboolean grayscale) {
	jobject bmp = bitmap;
	AndroidBitmapInfo info;
	int ret;
	int locked;
	int image_height;
	int image_width;
	int image_type;
	void* pixels;


	if (!getNeon())
	{
		// no NEON
		return bitmap;
	}

	if (!cinfo) {
		cinfo = malloc(sizeof(struct jpeg_decompress_struct));
		jpeg_create_decompress(cinfo);
	}

	struct my_error_mgr jerr;

	cinfo->err = jpeg_std_error(&jerr.pub);
	jerr.pub.error_exit = my_error_exit;
	if (setjmp(jerr.setjmp_buffer)) {
		LOGE("Error in libjpeg: %s",jerr.pub.jpeg_message_table[jerr.pub.msg_code]);
		if (locked)
			AndroidBitmap_unlockPixels(env, bmp);
		jpeg_abort_decompress(cinfo);
		return bmp;
	}


	if (dataSize < 20) {
		return bitmap;
	}

	if (data[8]!=0xFF || data[9]!=0xD8) {
		// probably not jpeg
		image_height = ((uint32_t*)data)[4];
		image_width = ((uint32_t*)data)[3];
		image_type = 0;
		// return if got invalid data
		if (image_height * image_width * 2 > dataSize) return bmp;
		if (image_height * image_width * 2 <= 0) return bmp;
	} else {
		jpeg_mem_src_tj(cinfo, data+8, dataSize-8);
		jpeg_read_header(cinfo, 1);
		image_height = cinfo->image_height;
		image_width = cinfo->image_width;
		image_type = 1;
	}

	if (!bitmap) {
		bmp = 0;
	} else if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		bmp = 0;
	} else if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		bmp = 0;
	} else if (image_height != info.height) {
		bmp = 0;
	} else if (image_width != info.width) {
		bmp = 0;
	}
	if (bmp==0) {
		jclass bmpclass = (*env)->FindClass(env, "android/graphics/Bitmap");
		jclass bmpcfgclass = (*env)->FindClass(env, "android/graphics/Bitmap$Config");
		jmethodID bmpcreate = (*env)->GetStaticMethodID(env, bmpclass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
		jfieldID argbfield = (*env)->GetStaticFieldID(env, bmpcfgclass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
		jobject argb8888 = (*env)->GetStaticObjectField(env, bmpcfgclass, argbfield);
		bmp = (*env)->CallStaticObjectMethod(env, bmpclass, bmpcreate, image_width, image_height, argb8888);
	}

	if ((ret = AndroidBitmap_lockPixels(env, bmp, &pixels)) < 0) {
		return bitmap;
	}
	locked = 1;

	if (image_type) {
		JSAMPROW ptr = pixels;
		cinfo->out_color_space = JCS_EXT_RGBX;
		jpeg_start_decompress(cinfo);
		while (cinfo->output_scanline < cinfo->output_height) {
			jpeg_read_scanlines(cinfo, &ptr, 1);
			ptr += image_width*4;
		}
		jpeg_finish_decompress(cinfo);
	} else {
		if (grayscale) {
			yuv422_2_rgb8888_neon_grayscale(pixels,data+0x28,image_width,image_height,image_width*2,image_width*4);
		} else {
			yuv422_2_rgb8888_neon(pixels,data+0x28,image_width,image_height,image_width*2,image_width*4);
		}
	}

	AndroidBitmap_unlockPixels(env, bmp);
	return bmp;
}
#endif

JNIEXPORT jobject JNICALL Java_uk_esoxy_android_mlcontroller_driver_NativeDevice_nativeGetLvImage(
		JNIEnv * env, jobject thiz, jobject bitmap, jboolean grayscale) {

	struct ptp_transaction_result result;
	int params[] = { 0x100000 };

	struct usb_device* device = get_device(env, thiz);

	if (!readData) {
		readData = malloc(1024*256);
		readDataSize = 1024*256;
	}

	result = do_ptp_transaction(device, 0x9153, params, 1, 2, readData, readDataSize);
	if (result.need_dealloc) {
		LOGI("Resizing buffers old: %d new: %d", readDataSize, result.dataPtrSize);
		free(readData);
		readData = result.data;
		readDataSize = result.dataPtrSize;
		LOGI("Resizing buffers done");
	}

#ifdef BUILD_NEON
	return create_bitmap_from_data(env, bitmap, result.data, result.dataSize, grayscale);
#else
	return bitmap;
#endif
}

JNIEXPORT jboolean JNICALL Java_uk_esoxy_android_mlcontroller_driver_NativeDevice_nativeGetNeon(
		JNIEnv * env, jclass thiz) {
	return getNeon();
}

JNIEXPORT jobject JNICALL Java_uk_esoxy_android_mlcontroller_driver_NativeDevice_nativeCreateBitmapFromData(
		JNIEnv * env, jclass thiz, jobject bitmap, jbyteArray mData, jboolean grayscale) {
	jobject bmp = bitmap;
	uint8_t* data;
	uint32_t dataSize;

#ifdef BUILD_NEON
	dataSize = (*env)->GetArrayLength(env, mData);
	data = (*env)->GetByteArrayElements(env, mData, NULL);

	if (data) {
		bmp = create_bitmap_from_data(env, bitmap, data, dataSize, grayscale);
		(*env)->ReleaseByteArrayElements(env, mData, data, 0);
	}
#endif

	return bmp;
}

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	return JNI_VERSION_1_2;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {
#ifdef BUILD_NEON
	if (cinfo) {
		jpeg_destroy_decompress(cinfo);
		cinfo = 0;
	}
#endif
}
