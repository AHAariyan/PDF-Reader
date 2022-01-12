package com.rokomari.pdf_reader;

import static android.content.ContentValues.TAG;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.PackageManagerCompat;
import androidx.fragment.app.DialogFragment;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.jaredrummler.cyanea.Cyanea;
import com.rokomari.pdf_reader.Utils.Utils;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfPasswordException;

import java.io.FileNotFoundException;
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

    private SharedPreferences prefManager;

    private String pdfPassword;

    private Intent requestFileIntent;

    private TextView pageNo, title;

    View dialogView;
    private EditText passwordField;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Cyanea.init(getApplication(), getResources());

        //Instantiate the context reference variable with the MainActivity.this:
        context = MainActivity.this;

        requestFileIntent = new Intent(Intent.ACTION_PICK);
        requestFileIntent.setType("application/pdf");

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        prefManager = PreferenceManager.getDefaultSharedPreferences(this);
        onFirstUpdate();

        //Instantiate the UI variable:
        initUI();

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            uri = getIntent().getData();
            if (uri == null)
                pickFile();
        }
        displayFromUri(uri);
    }

    void shareFile() {
        Intent sharingIntent;
        if (uri.getScheme() != null && uri.getScheme().startsWith("http")) {
            sharingIntent = Utils.plainTextShareIntent("Share File", uri.toString());
        } else {
            sharingIntent = Utils.fileShareIntent("Share File", pdfFileName, uri);
        }
        startActivity(sharingIntent);
    }

    private void handleFileOpeningError(Throwable exception) {
        if (exception instanceof PdfPasswordException) {
            if (pdfPassword != null) {
                Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show();
                pdfPassword = null;  // prevent the toast from being shown again if the user rotates the screen
            }
            askForPdfPassword();
        } else if (couldNotOpenFileDueToMissingPermission(exception)) {
            readFileErrorPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            Toast.makeText(this, "An error occurred while opening the file!", Toast.LENGTH_LONG).show();
            //Log.e(TAG, "Error when opening file", exception);
        }
    }

    void askForPdfPassword() {
        //PasswordDialogBinding dialogBinding = PasswordDialogBinding.inflate(getLayoutInflater());
        dialogView = LayoutInflater.from(MainActivity.this).inflate(R.layout.password_dialog, null);
        passwordField = dialogView.findViewById(R.id.passwordInput);
        AlertDialog alert = new AlertDialog.Builder(this)
                .setTitle("Password required")
                .setMessage("This document is password protected. Please enter a password.")
                .setView(dialogView)
                .setPositiveButton("ok", (dialog, which) -> {
                    pdfPassword = passwordField.getText().toString();
                    displayFromUri(uri);
                })
                .setIcon(R.drawable.lock_icon)
                .create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
    }

    void showPdfMetaDialog() {
        PdfDocument.Meta meta = pdfView.getDocumentMeta();
        if (meta != null) {
            Bundle dialogArgs = new Bundle();
            dialogArgs.putString(PdfMetaDialog.TITLE_ARGUMENT, meta.getTitle());
            dialogArgs.putString(PdfMetaDialog.AUTHOR_ARGUMENT, meta.getAuthor());
            dialogArgs.putString(PdfMetaDialog.CREATION_DATE_ARGUMENT, meta.getCreationDate());
            DialogFragment dialog = new PdfMetaDialog();
            dialog.setArguments(dialogArgs);
            dialog.show(getSupportFragmentManager(), "meta_dialog");
        }
    }

    private boolean couldNotOpenFileDueToMissingPermission(Throwable e) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED)
            return false;

        String exceptionMessage = e.getMessage();
        return e instanceof FileNotFoundException &&
                exceptionMessage != null && exceptionMessage.contains("Permission denied");
    }

    private void restartAppIfGranted(boolean isPermissionGranted) {
        if (isPermissionGranted) {
            // This is a quick and dirty way to make the system restart the current activity *and the current app process*.
            // This is needed because on Android 6 storage permission grants do not take effect until
            // the app process is restarted.
            System.exit(0);
        } else {
            Toast.makeText(this, "File Opening Error!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putParcelable("uri", uri);
        outState.putInt("pageNumber", pageNumber);
        outState.putString("pdfPassword", pdfPassword);
        super.onSaveInstanceState(outState);
    }

    private void restoreInstanceState(Bundle savedState) {
        uri = savedState.getParcelable("uri");
        pageNumber = savedState.getInt("pageNumber");
        pdfPassword = savedState.getString("pdfPassword");
    }

    @Override
    public void onResume() {
        super.onResume();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (prefManager.getBoolean("screen_on_pref", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void onFirstUpdate() {
        boolean isFirstRun = prefManager.getBoolean(Utils.getAppVersion(), true);
        if (isFirstRun) {
            //Utils.showLog(this);
            SharedPreferences.Editor editor = prefManager.edit();
            editor.putBoolean(Utils.getAppVersion(), false);
            editor.apply();
        }
    }

    //Instantiate the UI variable:
    private void initUI() {
        //instantiating the load btn:
        loadBtn = findViewById(R.id.loadBtn);
        //instantiate the OnClick Listener for creating action event from outside of the onCreate():
        loadBtn.setOnClickListener(this);

        //Instantiate the PDF view:
        pdfView = findViewById(R.id.pdfViewer);

        //
        pageNo = findViewById(R.id.pageNumber);
        title = findViewById(R.id.title);

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

    private final ActivityResultLauncher<String> readFileErrorPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            this::restartAppIfGranted
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
        title.setText(String.format("%s", pdfFileName));
        pageNo.setText(String.format("Page %s / %s", page + 1, pageCount));
        //setTitle(String.format("%s %s / %s", pdfFileName + " ", page + 1, pageCount));
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
        pdfView.setMinZoom(1.0f);
        //mid zoom
        pdfView.setMidZoom(2.0f);
        //setting the maximum zoom limitation:
        pdfView.setMaxZoom(3.0f);
        viewConfigurator
                //setting the page number:
                .defaultPage(pageNumber)
                //call back page change listener:
                .onPageChange(this::setCurrentPage)
                .enableAnnotationRendering(true)
                .enableAntialiasing(prefManager.getBoolean("alias_pref", true))
                //.onTap(this::toggleBottomNavigationVisibility)
                //.onPageScroll(this::toggleBottomNavigationAccordingToPosition)
                .scrollHandle(new DefaultScrollHandle(this))
                .spacing(10) // in dp
                .onError(this::handleFileOpeningError)
                .onPageError((page, err) -> Log.e("MAIN_ACTIVITY", "Cannot load page " + page, err))
                .pageFitPolicy(FitPolicy.WIDTH)
                .password(pdfPassword)
                .swipeHorizontal(prefManager.getBoolean("scroll_pref", false))
                .autoSpacing(prefManager.getBoolean("scroll_pref", false))
                //.pageSnap(prefManager.getBoolean("snap_pref", false))
                .pageFling(prefManager.getBoolean("fling_pref", false))
                .nightMode(prefManager.getBoolean("pdftheme_pref", false))
                //loaded the PDF file:
                .load();
    }

    public static class PdfMetaDialog extends DialogFragment {

        public static final String TITLE_ARGUMENT = "title";
        public static final String AUTHOR_ARGUMENT = "author";
        public static final String CREATION_DATE_ARGUMENT = "creation_date";

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            return builder.setTitle("")
                    //.setMessage(getString(R.string.pdf_title, getArguments().getString(TITLE_ARGUMENT)) + "\n" +
                    //getString(R.string.pdf_author, getArguments().getString(AUTHOR_ARGUMENT)) + "\n" +
                    //getString(R.string.pdf_creation_date, getArguments().getString(CREATION_DATE_ARGUMENT)))
                    .setPositiveButton("ok", (dialog, which) -> {
                    })
                    .setIcon(R.drawable.info_icon)
                    .create();
        }
    }
}