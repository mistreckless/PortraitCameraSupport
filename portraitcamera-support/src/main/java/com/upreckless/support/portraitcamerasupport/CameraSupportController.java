package com.upreckless.support.portraitcamerasupport;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.view.OrientationEventListener;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public abstract class CameraSupportController extends OrientationEventListener {

    OnSupportCameraTakePictureListener onSupportCameraTakePictureListener;

    CameraSupportController(Context context) {
        super(context);
    }

    @Override
    public void onOrientationChanged(int orientation) {
        int currentOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
        if (orientation >= 330 || orientation < 30) {
            currentOrientation = Surface.ROTATION_0;
        } else if (orientation >= 60 && orientation < 120) {
            currentOrientation = Surface.ROTATION_90;
        } else if (orientation >= 150 && orientation < 210) {
            currentOrientation = Surface.ROTATION_180;
        } else if (orientation >= 240 && orientation < 300) {
            currentOrientation = Surface.ROTATION_270;
        }

        if (orientation != OrientationEventListener.ORIENTATION_UNKNOWN && currentOrientation != OrientationEventListener.ORIENTATION_UNKNOWN)
            onSupportOrientationChanged(currentOrientation);
        else onSupportOrientationChanged(Surface.ROTATION_0);
    }

    public File savePicture(byte[] bytes, String fileName, String filePath) {

        File file = new File(filePath, fileName);
        if (!file.getParentFile().exists())
            if (!file.mkdirs())
                if (onSupportCameraTakePictureListener != null) {
                    onSupportCameraTakePictureListener.onError("Cannot save file! Check manifest permission");
                    return null;
                } else throw new RuntimeException("Cannot save file! Check manifest permission");
        FileOutputStream outputStream = null;
        if (getCameraType() == CameraType.FRONT) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            Matrix matrix = new Matrix();
            matrix.setRotate(getOrientation(bytes));
            matrix.postScale(-1, 1);
            Bitmap scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
            bitmap.recycle();
            try {
                outputStream = new FileOutputStream(file);
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                outputStream.flush();
                outputStream.getFD().sync();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (outputStream != null)
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        } else {
            try {
                outputStream = new FileOutputStream(file);
                outputStream.write(bytes);
                outputStream.flush();
                outputStream.getFD().sync();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (outputStream != null)
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        }
        return file;
    }

    public abstract void onSupportOrientationChanged(int orientation);

    public abstract CameraType getCameraType();

    private static int getOrientation(byte[] jpeg) {
        if (jpeg == null) {
            return 0;
        }

        int offset = 0;
        int length = 0;

        while (offset + 3 < jpeg.length && (jpeg[offset++] & 0xFF) == 0xFF) {
            int marker = jpeg[offset] & 0xFF;

            if (marker == 0xFF) {
                continue;
            }
            offset++;

            if (marker == 0xD8 || marker == 0x01) {
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }

            length = pack(jpeg, offset, 2, false);
            if (length < 2 || offset + length > jpeg.length) {
                return 0;
            }

            if (marker == 0xE1 && length >= 8 &&
                    pack(jpeg, offset + 2, 4, false) == 0x45786966 &&
                    pack(jpeg, offset + 6, 2, false) == 0) {
                offset += 8;
                length -= 8;
                break;
            }

            offset += length;
            length = 0;
        }

        if (length > 8) {
            int tag = pack(jpeg, offset, 4, false);
            if (tag != 0x49492A00 && tag != 0x4D4D002A) {
                return 0;
            }
            boolean littleEndian = (tag == 0x49492A00);

            int count = pack(jpeg, offset + 4, 4, littleEndian) + 2;
            if (count < 10 || count > length) {
                return 0;
            }
            offset += count;
            length -= count;

            count = pack(jpeg, offset - 2, 2, littleEndian);
            while (count-- > 0 && length >= 12) {
                tag = pack(jpeg, offset, 2, littleEndian);
                if (tag == 0x0112) {
                    int orientation = pack(jpeg, offset + 8, 2, littleEndian);
                    switch (orientation) {
                        case 1:
                            return 0;
                        case 3:
                            return 180;
                        case 6:
                            return 90;
                        case 8:
                            return 270;
                    }
                    return 0;
                }
                offset += 12;
                length -= 12;
            }
        }
        return 0;
    }

    private static int pack(byte[] bytes, int offset, int length, boolean littleEndian) {
        int step = 1;
        if (littleEndian) {
            offset += length - 1;
            step = -1;
        }

        int value = 0;
        while (length-- > 0) {
            value = (value << 8) | (bytes[offset] & 0xFF);
            offset += step;
        }
        return value;
    }

    public enum CameraType {
        BACK, FRONT
    }

    public enum CameraApi {
        NEW, OLD
    }
}
