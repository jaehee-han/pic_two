package com.oasyss.picturebook.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.oasyss.picturebook.R;
import com.oasyss.picturebook.util.BitmapSaver;
import com.oasyss.picturebook.util.BitmapSharer;
import com.oasyss.picturebook.util.Extention;
import com.oasyss.picturebook.util.errors.UIErrorReporter;
import com.oasyss.picturebook.util.images.BitmapHash;
import com.oasyss.picturebook.util.images.ImageDB;
import com.oasyss.picturebook.util.images.ResourceImageDB;
import com.oasyss.picturebook.util.imports.ImagePreview;
import com.oasyss.picturebook.widget.ColorButton;
import com.oasyss.picturebook.widget.LoadImageProgress;
import com.oasyss.picturebook.widget.PaintArea;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static com.oasyss.picturebook.activity.AbstractColoringActivity.INTENT_ABOUT;

public class PaintActivity extends AbstractColoringActivity {
    private static final int REQUEST_CHOOSE_PICTURE = 0;
    private static final int REQUEST_PICK_COLOR = 1;
    public static final String ARG_IMAGE = "bitmap";
    private PaintArea paintArea;
    private ProgressDialog _progressDialog;

    //컬러버튼 상태 표시
    private ColorButtonManager colorButtonManager;
    boolean doubleBackToExitPressedOnce = false;
    private BitmapSaver bitmapSaver = null;
    //마지막으로 저장된 비트맵
    private int lastSavedHash;
    private ImageView paintView;

    private ImageView eraserBtn;
    private ColorButton eraserColorBtn;

    private LinearLayout eraserLayout, backLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paint);
        paintView = (ImageView) findViewById(R.id.paint_view);
        paintArea = new PaintArea(paintView);

        eraserLayout = (LinearLayout)findViewById(R.id.eraser_layout);
        backLayout = (LinearLayout)findViewById(R.id.back_layout);

        colorButtonManager = new ColorButtonManager();
        //지우개버튼클릭
        eraserColorBtn = (ColorButton)findViewById(R.id.eraser_color_btn);
        eraserBtn = (ImageView)findViewById(R.id.eraser_img_view);
        eraserBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                eraserColorBtn.callOnClick();

                eraserLayout.setBackground(ContextCompat.getDrawable(view.getContext(), R.drawable.border_line_all_red));
            }
        });
        //컬러팔넷 호출
//        View pickColorsButton = findViewById(R.id.pick_color_button);
//        pickColorsButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Intent intent = new Intent(PaintActivity.this, PickColorActivity.class);
//                startActivityForResult(intent, REQUEST_PICK_COLOR);
//            }
//        });

        // 배경까지 색칠하게
        final LinearLayout bg = findViewById(R.id.paint_view_background);
//        bg.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                bg.setBackgroundColor(paintArea.getPaintColor());
//            }
//        });
        loadImageFromIntent(getIntent());
    }

    private void loadImageFromIntent(final Intent intent) {
        paintView.post(new Runnable() {
            @Override
            public void run() {
                Bundle extras = intent.getExtras();
                ImageDB.Image image;
                Extention.Log("ARG_IMAGE : " + ARG_IMAGE);
                Extention.Log("extras : " + extras);
                if (extras != null && extras.containsKey(ARG_IMAGE)) {
                    image = extras.getParcelable(ARG_IMAGE);
                } else {
                    image = new ResourceImageDB(PaintActivity.this).randomImage();
                }
                // TODO: 이미지 호출될동안 로딩프로그레스
                image.asPaintableImage(new Preview(), new LoadImageProgress(null, null));
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // capture the new intent
        // see https://developer.android.com/guide/components/activities/tasks-and-back-stack
        // see https://developer.android.com/reference/android/app/Activity#onNewIntent(android.content.Intent)
        super.onNewIntent(intent);
        saveBitmap();
        loadImageFromIntent(intent);
    }

    @Override
    public void onBackPressed() {
        // code for double-clicking the back button to exit the activity
        // see https://stackoverflow.com/a/13578600/1320237
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, R.string.toast_double_click_back_button, Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce=false;
            }
        }, 2000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.paint_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.open_new:
                openPictureChoice();
                return true;
            case R.id.save:
                saveBitmap();
                return true;
            case R.id.about:
                startActivity(new Intent(INTENT_ABOUT));
                return true;
            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.gallery:
                openGallery();
                return true;
            case R.id.share:
                saveBitmap(new BitmapSharer(this, paintArea.getBitmap()));
                return true;
        }
        return false;
    }

    private void saveBitmap() {
        saveBitmap(new BitmapSaver(this, paintArea.getBitmap()));
    }

    private void saveBitmap(BitmapSaver newBitmapSaver) {
        int duration = Toast.LENGTH_SHORT;
        int message;
        String path;
        int newHash = BitmapHash.hash(newBitmapSaver.getBitmap());
        if (bitmapSaver != null && bitmapSaver.isRunning()) {
            // pressing save while in progress
            message = R.string.toast_save_file_running;
            path = bitmapSaver.getFile().getPath();
        } else if (lastSavedHash == newHash) {
            // image is already saved
            message = R.string.toast_save_file_again;
            path = bitmapSaver.getFile().getPath();
            newBitmapSaver.alreadySaved(bitmapSaver);
        } else {
            // image is not saved
            bitmapSaver = newBitmapSaver;
            bitmapSaver.start();
            message = R.string.toast_save_file;
            path = bitmapSaver.getFile().getName();
            lastSavedHash = BitmapHash.hash(newBitmapSaver.getBitmap());
        }
        // create a toast
        // see https://developer.android.com/guide/topics/ui/notifiers/toasts#java
        String text = getString(message, path);
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();

    }

    private void openPictureChoice() {
        // how to start a new activity
        // see https://stackoverflow.com/a/4186097
        Intent intent = new Intent(this, ChoosePictureActivity.class);
        ImageDB.Image image = paintArea.getImage();
        intent.putExtra(ChoosePictureActivity.ARG_IMAGE, image);
        startActivityForResult(intent, REQUEST_CHOOSE_PICTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            case REQUEST_CHOOSE_PICTURE:
                if (resultCode == RESULT_OK)
                {
                    ImageDB.Image image;
                    try {
                        image = data.getParcelableExtra(ChoosePictureActivity.RESULT_IMAGE);
                    } catch (BadParcelableException e) {
                        UIErrorReporter.of(this).report(e);
                        return;
                    }
                    image.asPaintableImage(new Preview(), new LoadImageProgress(null, null));
                }
                break;
            case REQUEST_PICK_COLOR:
                if (resultCode != RESULT_CANCELED)
                {
                    colorButtonManager.selectColor(resultCode);
                }
                break;
        }
    }

    private class ColorButtonManager implements View.OnClickListener
    {

        public ColorButtonManager()
        {
            findAllColorButtons(_colorButtons);
            _usedColorButtons.addAll(_colorButtons);
            _selectedColorButton = _usedColorButtons.getFirst();
            _selectedColorButton.setSelected(true);
            Iterator<ColorButton> i = _usedColorButtons.iterator();
            while (i.hasNext())
            {
                i.next().setOnClickListener(ColorButtonManager.this);
            }
            setPaintViewColor();
        }

        public void onClick(View view)
        {
            if (view instanceof ColorButton)
            {
                selectButton((ColorButton) view);
            }
        }

        // Select the button that has the given color, or if there is no such
        // button then set the least recently used button to have that color.
        public void selectColor(int color)
        {
            _selectedColorButton = selectAndRemove(color);
            if (_selectedColorButton == null)
            {
                // Recycle the last used button to hold the new color.
                _selectedColorButton = _usedColorButtons.removeLast();
                _selectedColorButton.setColor(color);
                _selectedColorButton.setSelected(true);
            }
            _usedColorButtons.addFirst(_selectedColorButton);
            setPaintViewColor();
        }

        // Select the given button.
        private void selectButton(ColorButton button)
        {
            _selectedColorButton = selectAndRemove(button.getColor());
            _usedColorButtons.addFirst(_selectedColorButton);
            setPaintViewColor();
        }

        // Set the currently selected color in the paint view.
        private void setPaintViewColor()
        {
            int selectedColor = _selectedColorButton.getColor();
            paintArea.setPaintColor(selectedColor);
            setBackgroundColorOfButtons(selectedColor);
        }
        //팔넷 터치하면 주변 색상바꾸기
        private void setBackgroundColorOfButtons(int color) {
//            int backgroundColor = (color & 0xffffff) | 0x70000000;
//            View buttonHolder = findViewById(R.id.color_buttons_container);
//            buttonHolder.setBackgroundColor(backgroundColor);
        }

        // Finds the button with the color. If found, sets it to selected,
        // removes it and returns it. If not found, it returns null. All
        // other buttons are unselected.
        private ColorButton selectAndRemove(int color)
        {
            Extention.Log("adsfasdfasdfasdf");
            ColorButton result = null;
            Iterator<ColorButton> i = _usedColorButtons.iterator();
            while (i.hasNext())
            {
                ColorButton b = i.next();
                if (b.getColor() == color)
                {
                    result = b;
                    b.setSelected(true);
                    i.remove();
                    eraserLayout.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.border_line_all_black));
                }
                else
                {
                    b.setSelected(false);
                }
            }
            return result;
        }
        // A list of pointers to all buttons in the order
        // in which they have been used.
        private List<ColorButton> _colorButtons = new ArrayList<ColorButton>();
        private LinkedList<ColorButton> _usedColorButtons = new LinkedList<ColorButton>();
        private ColorButton _selectedColorButton;
    }

    private static final String GALLERY_URL = "https://gallery.quelltext.eu/";
    private void openGallery() {
        // open url in browser, see https://stackoverflow.com/a/2201999/1320237
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(GALLERY_URL));
        startActivity(browserIntent);
    }

    private class Preview implements ImagePreview {
        @Override
        public void setImage(final Bitmap image) {
            paintView.post(new Runnable() {
                @Override
                public void run() {
                    paintArea.setImageBitmap(image);
                }
            });
        }

        @Override
        public int getWidth() {
            return paintArea.getWidth();
        }

        @Override
        public int getHeight() {
            return paintArea.getHeight();
        }

        @Override
        public InputStream openInputStream(Uri uri) throws FileNotFoundException {
            return getContentResolver().openInputStream(uri);
        }

        @Override
        public void done(final Bitmap bitmap) {
            setImage(bitmap);
        }
    }
}