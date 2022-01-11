package com.rokomari.pdf_reader;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.PackageManagerCompat;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.github.barteksc.pdfviewer.util.FitPolicy;

import java.security.Permission;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    //Instance variable for opening the storage for selecting PDF file:
    private Button loadBtn;

    //Making a context reference variable for using the 'context' word very easily:
    private Context context;

    // URI instance variable to track the file selected:
    private Uri uri;

    //Variable for storing the PDF title or name:
    private String pdfFileName = "";

    //page number of pdf file, initially it's 0, but it will increment after scrolling to next page:
    private int pageNumber = 0;

    //Instance variable of PDF view. It's from the library:
    private PDFView pdfView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Instantiate the context reference variable with the MainActivity.this:
        context = MainActivity.this;

        //Instantiate the UI variable:
        initUI();
    }

    //Instantiate the UI variable:
    private void initUI() {
        //instantiating the load btn:
        loadBtn = findViewById(R.id.loadBtn);
        //instantiate the OnClick Listener for creating action event from outside of the onCreate():
        loadBtn.setOnClickListener(this);

        //Instantiate the PDF view:
        pdfView = findViewById(R.id.pdfViewer);

    }

    @Override
    public void onClick(View view) {
        // getting the id of specific click:
        int id = view.getId();
        switch (id) {
            //if it's load button clicked for opening the pdf file from the storage:
            case R.id.loadBtn:
                //Checking the user run-time permission is already enable or not:
                checkingRuntimePermission();
                break;
        }
    }

    //Opening the pdf file after clicking on the Load Btn:
    private void openingPDFFile() {
        // storing the selected pdf file int URI:
        uri = getIntent().getData();
        // checking. whether the URI is null or not:
        //If it's null the ask for picking a file:
        if (uri == null) {
            // Asking for the picking or selecting a file from storage:
            pickFile();
        }
    }

    //selecting the file or picking up the file from storage:
    private void pickFile() {
        try {
            //check with the ActivityResult to know we are getting the correct URI for showing as PDF:
            // also setting our file type as only PDF:
            // NOTE: we can also initiate more file types like docs,txt, etc.
            documentPickerLauncher.launch(new String[]{"application/pdf"});
        } catch (ActivityNotFoundException e) {
            //alert user that file manager not working
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
    }

    //check the run-time permission is already enabled or not:
    private void checkingRuntimePermission() {
        //if the permission is granted already the proceed for the next work:
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            // open the file from storage to the app:
            openingPDFFile();
        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            requestPermissionLauncher.launch(
                    Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher, as an instance variable.
    private ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted) {
            // Permission is granted. Continue the action or workflow in your
            // app.
            openingPDFFile();
        } else {
            // Explain to the user that the feature is unavailable because the
            // features requires a permission that the user has denied. At the
            // same time, respect the user's decision. Don't link to system
            // settings in an effort to convince the user to change their
            // decision.
            Toast.makeText(context, " You might not be able to use this app properly!!", Toast.LENGTH_SHORT).show();
        }
    });

    private final ActivityResultLauncher<String[]> documentPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            //this::openSelectedDocument
            // referring method reference using double colon by replacing the lambda:(Basically it's a callback)
            this::openSelectedDocument
    );

    //when user will select the document:
    private void openSelectedDocument(Uri selectedDocumentUri) {
        //checking the document is elected or not:
        //means if the user doesn't select the pdf file:
        //then it will return null:
        if (selectedDocumentUri == null) {
            return;
        }

        if (uri == null || selectedDocumentUri.equals(uri)) {
            uri = selectedDocumentUri;
            //displaying the data from the URI:
            displayFromUri(uri);
        } else {
            Intent intent = new Intent(this, getClass());
            intent.setData(selectedDocumentUri);
            startActivity(intent);
        }
    }

    //Trying to display the URI from selected PDF file on the PDF view:
    void displayFromUri(Uri uri) {
        //Checking the URI is null or not:
        // if the URI is null the setting the title as null, because we don't know the title of that PDF file:
        if (uri == null) {
            //setting up the title:
            setTitle("");
            //returning then without doing the further steps:
            return;
        }
        //getting the file name of the selected PDF file:
        pdfFileName = getFileName(uri);
        //pdfFileName = getFileName(uri);
        setTitle(pdfFileName);
        //This is only validate for API level > 21:
        setTaskDescription(new ActivityManager.TaskDescription(pdfFileName));


        //If found anything related to the downloadable on the PDF file, It will do that:
        String scheme = uri.getScheme();
        if (scheme != null && scheme.contains("http")) {
            //downloadOrShowDownloadedFile(uri);
        } else {
            //setting the URI on the View:
            pdfView.fromUri(uri);
            //Configuring the PDF view as per we needed:
            configurePdfViewAndLoad(pdfView.fromUri(uri));
        }
    }

    //Extracting the file name from the selected PDF file:
    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int indexDisplayName = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (indexDisplayName != -1) {
                        result = cursor.getString(indexDisplayName);
                    }
                }
            } catch (Exception e) {
                //Log.w(TAG, "Couldn't retrieve file name", e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    // Setting the Page number and the title at once:
    private void setCurrentPage(int page, int pageCount) {
        pageNumber = page;
        setTitle(String.format("%s %s / %s", pdfFileName + " ", page + 1, pageCount));
    }

    // Configuring the PDF view to make it more flexible and easier to use:
    void configurePdfViewAndLoad(PDFView.Configurator viewConfigurator) {
//        if (!prefManager.getBoolean("pdftheme_pref", false)) {
//            viewBinding.pdfView.setBackgroundColor(Color.LTGRAY);
//        } else {
//            viewBinding.pdfView.setBackgroundColor(0xFF212121);
//        }
//        viewBinding.pdfView.useBestQuality(prefManager.getBoolean("quality_pref", false));

        //setting the minimum limitation of being zoomed
        pdfView.setMinZoom(0.5f);
        //mid zoom
        pdfView.setMidZoom(2.0f);
        //setting the maximum zoom limitation:
        pdfView.setMaxZoom(5.0f);
        viewConfigurator
                //setting the page number:
                .defaultPage(pageNumber)
                //call back page change listener:
                .onPageChange(this::setCurrentPage)
                .enableAnnotationRendering(true)
                //.enableAntialiasing(prefManager.getBoolean("alias_pref", true))
                //.onTap(this::toggleBottomNavigationVisibility)
                //.onPageScroll(this::toggleBottomNavigationAccordingToPosition)
                .scrollHandle(new DefaultScrollHandle(this))
                .spacing(10) // in dp
                //.onError(this::handleFileOpeningError)
                //.onPageError((page, err) -> Log.e(TAG, "Cannot load page " + page, err))
                .pageFitPolicy(FitPolicy.WIDTH)
                //.password(pdfPassword)
                //.swipeHorizontal(prefManager.getBoolean("scroll_pref", false))
                // .autoSpacing(prefManager.getBoolean("scroll_pref", false))
                //.pageSnap(prefManager.getBoolean("snap_pref", false))
                //.pageFling(prefManager.getBoolean("fling_pref", false))
                //.nightMode(prefManager.getBoolean("pdftheme_pref", false))
                //loaded the PDF file:
                .load();
    }
}