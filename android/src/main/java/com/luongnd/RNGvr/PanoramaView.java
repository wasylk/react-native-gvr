package com.luongnd.RNGvr;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.annotation.UiThread;
import android.util.Log;
import android.util.Pair;
import android.widget.RelativeLayout;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import com.google.vr.sdk.widgets.pano.VrPanoramaEventListener;
import com.google.vr.sdk.widgets.pano.VrPanoramaView;
import com.google.vr.sdk.widgets.pano.VrPanoramaView.Options;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.io.*;

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;

public class PanoramaView extends RelativeLayout {
    private static final String TAG = PanoramaView.class.getSimpleName();

    private android.os.Handler _handler;
    private PanoramaViewManager _manager;
    private Activity _activity;

    private VrPanoramaView panoWidgetView;
    private Map<URL, Bitmap> imageCache = new HashMap<>();
    private ImageLoaderTask imageLoaderTask;
    private Options panoOptions = new Options();

    private URL imageUrl;
    private String imagePath;
    private int imageWidth;
    private int imageHeight;

    @UiThread
    public PanoramaView(Context context, PanoramaViewManager manager, Activity activity) {
        super(context);
        _handler = new android.os.Handler();
        _manager = manager;
        _activity = activity;
    }
    public void onAfterUpdateTransaction() {
        panoWidgetView = new VrPanoramaView(_activity);
        panoWidgetView.setEventListener(new ActivityEventListener());
        panoWidgetView.setStereoModeButtonEnabled(false);
        panoWidgetView.setInfoButtonEnabled(false);
        panoWidgetView.setPureTouchTracking(true);
        panoWidgetView.setFullscreenButtonEnabled(false);
        this.addView(panoWidgetView);

        if (imageLoaderTask != null) {
            imageLoaderTask.cancel(true);
        }
        imageLoaderTask = new ImageLoaderTask();
        imageLoaderTask.execute(Pair.create(imageUrl, panoOptions));
    }
    public void setImageUrl(String value) {
        if (imageUrl != null && imageUrl.toString().equals(value)) { return; }

        try {
            imageUrl = new URL(value);
        } catch(MalformedURLException e) {}
    }

    // Set path to file instead of URL
    public void setImageFilePath(String value) {
        if (imagePath != null) { return; }
        try {
            imagePath = value.toString();
        } catch (Exception e) {
            Log.e(TAG, "Could not load file: " + e);
        }
    }

    public void setDimensions(int width, int height) {
        this.imageWidth = width;
        this.imageHeight = height;
    }

    public void setInputType(int value) {
        if (panoOptions.inputType == value) { return; }
        panoOptions.inputType = value;
    }

    class ImageLoaderTask extends AsyncTask<Pair<URL, Options>, Void, Boolean> {
        protected Boolean doInBackground(Pair<URL, Options>... fileInformation) {
            Bitmap image;

            if (imageUrl != null) {
                final URL imageUrl = fileInformation[0].first;
                Options panoOptions = fileInformation[0].second;

                InputStream istr = null;

                if (!imageCache.containsKey(imageUrl)) {
                    try {
                        HttpURLConnection connection = (HttpURLConnection) fileInformation[0].first.openConnection();
                        connection.connect();

                        istr = connection.getInputStream();

                        imageCache.put(imageUrl, decodeSampledBitmap(istr));
                    } catch (IOException e) {
                        Log.e(TAG, "Could not load file: " + e);
                        return false;
                    } finally {
                        try {
                            if (istr != null) {
                                istr.close();
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Could not close input stream: " + e);
                        }
                    }
                }

                image = imageCache.get(imageUrl);
                panoWidgetView.loadImageFromBitmap(image, panoOptions);

            } else {
                File file = new File(imagePath);
                image = BitmapFactory.decodeFile(file.getAbsolutePath());
                Options panoOptions = new Options();
                panoWidgetView.loadImageFromBitmap(image, panoOptions);
            }

            return true;
        }

        private Bitmap decodeSampledBitmap(InputStream inputStream) throws IOException {
            final byte[] bytes = getBytesFromInputStream(inputStream);
            BitmapFactory.Options options = new BitmapFactory.Options();

            if(imageWidth != 0 && imageHeight != 0) {
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

                options.inSampleSize = calculateInSampleSize(options, imageWidth, imageHeight);
                options.inJustDecodeBounds = false;
            }

            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        }

        private byte[] getBytesFromInputStream(InputStream inputStream) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            IOUtils.copy(inputStream, baos);

            return baos.toByteArray();
        }

        private int calculateInSampleSize(
                BitmapFactory.Options options, int reqWidth, int reqHeight) {
            // Raw height and width of image
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {

                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while ((halfHeight / inSampleSize) > reqHeight
                        && (halfWidth / inSampleSize) > reqWidth) {
                    inSampleSize *= 2;
                }
            }

            return inSampleSize;
        }
    }

    private class ActivityEventListener extends VrPanoramaEventListener {
        @Override
        public void onLoadSuccess() {
            emitEvent("onImageLoaded", null);
        }

        @Override
        public void onLoadError(String errorMessage) {
            Log.e(TAG, "Error loading pano: " + errorMessage);

            emitEvent("onImageLoadingFailed", null);
        }
    }

    void emitEvent(String name, @Nullable WritableMap event) {
        if (event == null) {
            event = Arguments.createMap();
        }
        ((ReactContext)getContext())
                .getJSModule(RCTEventEmitter.class)
                .receiveEvent(getId(), name, event);
    }
}
