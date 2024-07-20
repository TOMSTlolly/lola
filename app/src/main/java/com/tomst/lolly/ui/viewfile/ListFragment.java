package com.tomst.lolly.ui.viewfile;


import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_CANCELED;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.tomst.lolly.R;
import com.tomst.lolly.core.CSVFile;
import com.tomst.lolly.core.CSVReader;
import com.tomst.lolly.core.Constants;
import com.tomst.lolly.core.DmdViewModel;
import com.tomst.lolly.core.FileOpener;
import com.tomst.lolly.core.FileOperation;
import com.tomst.lolly.core.ZipFiles;
import com.tomst.lolly.databinding.FragmentViewerBinding;
import com.tomst.lolly.fileview.FileDetail;
import com.tomst.lolly.fileview.FileViewerAdapter;
import com.tomst.lolly.core.PermissionManager;

import com.tomst.lolly.BuildConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ListFragment extends Fragment
{
    private FragmentViewerBinding binding;
    private View rootView = null;
    private int mywidth;
    private Bitmap
            fileImage,
            pictureImage,
            audioImage,
            videoImage,
            unknownImage,
            archiveImage,
            folderImage;
    private PermissionManager permissionManager;
    private String filePath;
    private File parent;
    private final String TAG = "TOMST";
    public FileOpener fopen;
    private  DmdViewModel dmd;

    FirebaseFirestore db;

    List<FileDetail> fFriends = null;

    public ListFragment()
    {
        //executor = new ScheduledThreadPoolExecutor(1);
        //fopen = new FileOpener(this);
    }


    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        ListViewModel listViewModel =
                new ViewModelProvider(this).get(ListViewModel.class);

        binding = FragmentViewerBinding.inflate(
                inflater, container, false
        );
        rootView = binding.getRoot();

        Button zip_btn = binding.buttonZipall;
        zip_btn.setText("Upload Zip");
        zip_btn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                ZipFiles zipFiles = new ZipFiles();

                File dir = new File(Constants.FILEDIR);
                String zipDirName = Constants.FILEDIR+"//tmp.zip";
                zipFiles.zipDirectory(dir, zipDirName);

                // Assume you have created a zip file called "my_project.zip" in your app's cache directory
                File zipFile = new File(zipDirName);

                // Get a content URI for the zip file using FileProvider
                Uri zipUri = FileProvider.getUriForFile(
                    getContext(),
                    BuildConfig.APPLICATION_ID + ".provider",
                    zipFile
                );

                // Create an intent with the action ACTION_SEND and the type "application/zip"
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("application/zip");

                // Put the content URI of the zip file as an extra
                sendIntent.putExtra(Intent.EXTRA_STREAM, zipUri);

                // Optionally, you can also add a subject and a text message for the intent
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Zip file");
                sendIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    "Here is content of my download from lolly phone app."
                );

                // Start the intent using startActivity() or startActivityForResult()
                startActivity(sendIntent);
            }
        });

        // database stuff
        db = FirebaseFirestore.getInstance();

        Button uploadBtn = binding.btnUploadToDB;
        Button shareBtn = binding.btnShare;

        uploadBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                uploadDataToStorage();
            }
        });

        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                shareData();
            }
        });

        Button toSerialBtn = binding.toSerial;
        Button toParallelBtn = binding.toParellel;

        toSerialBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                String convertFiles = "";
                FileViewerAdapter friendsAdapter = new FileViewerAdapter(
                        getContext(), fFriends
                );
                ArrayList<String> fileNames = friendsAdapter.collectSelected();

                int convert_res = 0;
                final String LAST_OCCURENCE = ".*/";
                for (String fileName : fileNames)
                {
                    convert_res = CSVFile.toSerial(fileName);
                    if (convert_res == 2)
                    {
                        Toast.makeText(
                                getContext(),
                                fileName.split(LAST_OCCURENCE)[1]
                                        + " already exists!",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
                Toast.makeText(
                        getContext(),
                        "Conversion complete!",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
        toParallelBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Toast.makeText(getContext(), "Converting to Parallel", Toast.LENGTH_LONG).show();
                String convertFiles = "";
                FileViewerAdapter friendsAdapter = new FileViewerAdapter(
                        getContext(), fFriends
                );
                ArrayList<String> fileNames = friendsAdapter.collectSelected();

                int convert_res = 0;
                final String LAST_OCCURENCE = ".*/";
                for (String fileName : fileNames)
                {
                    convert_res = CSVFile.toParallel(fileName);
                    if (convert_res == 2)
                    {
                        Toast.makeText(
                                getContext(),
                                fileName.split(LAST_OCCURENCE)[1]
                                        + " already exists!",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
                Toast.makeText(
                        getContext(),
                        "Conversion complete!",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        dmd = new ViewModelProvider(getActivity()).get(DmdViewModel.class);
        dmd.sendMessageToGraph("");

        permissionManager = new PermissionManager(getActivity());
        fopen = new FileOpener(getActivity());

        fFriends = new ArrayList<>();

        Intent intent = getActivity() != null ? getActivity().getIntent() : null;
        if (intent != null)
        {
            switch (intent.getAction())
            {
                case Intent.ACTION_GET_CONTENT:
                    fopen.isRequestDocument = true;
                    getActivity().setResult(RESULT_CANCELED);
                    break;

                case Intent.ACTION_OPEN_DOCUMENT : {
                    fopen.isRequestDocument = true;
                    getActivity().setResult(RESULT_CANCELED);
                    break;
                }

                default :
                    fopen.isRequestDocument = false;
            }
        }
        setupBitmaps();

        return rootView;
    }

    private void loadAllFiles()
    {
        // will most likely not exceed number of datasets on device
        ArrayList<String> filenames = new ArrayList<String>();
        ListView mListView = (ListView) rootView.findViewById(R.id.listView);
        FileViewerAdapter friendsAdapter = new FileViewerAdapter(
                getContext(), fFriends
        );
        mListView.setAdapter(friendsAdapter);

        // add listener for loading selected datasets to graph fragment
        Button select_sets_btn = binding.selectSets;
        select_sets_btn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                String fileNameMsg = "";
                ArrayList<String> fileNames = friendsAdapter.collectSelected();
                for (String fileName : fileNames)
                {
                    fileNameMsg += fileName + ";";
                }

                if (!fileNameMsg.contains("_parallel"))
                {
                    dmd.sendMessageToGraph(fileNameMsg);
                    switchToGraphFragment();
                }
                else
                {
                    Toast.makeText(
                        getContext(),
                        "Parallel formatted files are unable to be"
                            + " visualized!",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        });

    }

    private void loadFromStorage()
    {
        // show the loading icon
        ProgressBar progressBar = rootView.findViewById(R.id.uploadProgressBar);
        progressBar.setVisibility(View.VISIBLE);

        // set adapter
        FileViewerAdapter friendsAdapter = new FileViewerAdapter(getContext(), fFriends);

        // get current user
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        String userEmail = user != null ? user.getEmail() : "unknown";

        // load files from storage
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("Files");
        storageRef.listAll()
                .addOnSuccessListener(listResult ->
                {
                    for (StorageReference fileRef : listResult.getItems())
                    {
                        fileRef.getMetadata().addOnSuccessListener(storageMetadata ->
                        {
                            String fileUsers = storageMetadata.getCustomMetadata("user");
                            if (fileUsers != null)
                            {
                                String[] users = fileUsers.split(",");
                                for (String currentUser : users)
                                {
                                    if (currentUser.equals(userEmail))
                                    {
                                        // file belongs to the current user, download it
                                        String fileName = fileRef.getName();
                                        String filePath = fileRef.getPath();
                                        downloadCSVFile(fileName, filePath);

                                        // set the icon
                                        for (FileDetail fileDetail : fFriends)
                                        {
                                            if (fileDetail.getName().equals(fileName))
                                            {
                                                fileDetail.setUploaded(true);
                                                break;
                                            }
                                        }

                                        // update list view with icons
                                        ListView mListView = rootView.findViewById(R.id.listView);
                                        mListView.setAdapter(friendsAdapter);
                                    }
                                }

                            }
                        }).addOnFailureListener(e ->
                        {
                            Log.e(TAG, "Failed to get metadata: " + e.getMessage());
                        });
                    }
                    // hide loading icon
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e ->
                {
                    // hide loading icon
                    progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Failed to list files: " + e.getMessage());
                });
    }

    private void downloadCSVFile(String fileName, String filePath)
    {
        File localFile = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), fileName);

        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child(filePath);

        storageRef.getFile(localFile)
                .addOnSuccessListener(taskSnapshot ->
                {
                    Log.d(TAG, "File downloaded to " + localFile.getAbsolutePath());
                })
                .addOnFailureListener(exception ->
                {
                    Log.e(TAG, "Failed to download file: " + exception.getMessage());
                });
    }

    private void switchToGraphFragment()
    {
        BottomNavigationView bottomNavigationView;
        bottomNavigationView = (BottomNavigationView) getActivity()
                .findViewById(R.id.nav_view);
        View view = bottomNavigationView.findViewById(R.id.navigation_graph);
        view.performClick();
    }

    private void uploadDataToStorage()
    {
        // show the loading icon
        ProgressBar progressBar = rootView.findViewById(R.id.uploadProgressBar);
        progressBar.setVisibility(View.VISIBLE);

        FileViewerAdapter friendsAdapter = new FileViewerAdapter(getContext(), fFriends);

        ArrayList<String> fileNames = friendsAdapter.collectSelected();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        AtomicInteger filesUploaded = new AtomicInteger(0);
        int totalFiles = fileNames.size();

        // check if user is logged in
        if (user != null)
        {
            // go through all selected files
            for (String fileName : fileNames)
            {
                // get info
                Uri fileUri = Uri.fromFile(new File(fileName));
                String userEmail = user != null ? user.getEmail() : "unknown";

                // check if the file has already been uploaded
                boolean isAlreadyUploaded = false;
                for (FileDetail fileDetail : friendsAdapter.getAllFiles())
                {
                    if (fileDetail.getFull().equals(fileName) && fileDetail.isUploaded())
                    {
                        isAlreadyUploaded = true;
                        break;
                    }
                }

                // file is already uploaded, skip it
                if (isAlreadyUploaded)
                {
                    Toast.makeText(rootView.getContext(), fileUri.getLastPathSegment() + " Already Uploaded", Toast.LENGTH_SHORT).show();

                    filesUploaded.incrementAndGet();
                    // check if all files uploaded
                    if (filesUploaded.get() == totalFiles)
                    {
                        progressBar.setVisibility(View.GONE);
                    }
                    continue;
                }

                StorageReference storageRef = FirebaseStorage.getInstance().getReference();
                StorageReference fileRef = storageRef.child("Files/" + fileUri.getLastPathSegment());

                fileRef.putFile(fileUri)
                        .addOnSuccessListener(taskSnapshot ->
                        {
                            // update the file's metadata to include the user Id
                            fileRef.updateMetadata(
                                    new StorageMetadata.Builder()
                                            .setCustomMetadata("user", userEmail)
                                            .build()
                            ).addOnSuccessListener(aVoid ->
                            {
                                // set cloud icon
                                for (FileDetail fileDetail : friendsAdapter.getAllFiles())
                                {
                                    if (fileDetail.getFull().equals(fileName))
                                    {
                                        fileDetail.setUploaded(true);
                                        break;
                                    }
                                }
                                filesUploaded.incrementAndGet();

                                // check if all files uploaded
                                if (filesUploaded.get() == totalFiles)
                                {
                                    // update list view with icons
                                    ListView mListView = rootView.findViewById(R.id.listView);
                                    mListView.setAdapter(friendsAdapter);

                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(rootView.getContext(), fileUri.getLastPathSegment() + " Uploaded Successfully", Toast.LENGTH_SHORT).show();
                                }
                            }).addOnFailureListener(e ->
                            {
                                Toast.makeText(rootView.getContext(), "Failed to update metadata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                filesUploaded.incrementAndGet();

                                // check if all files uploaded
                                if (filesUploaded.get() == totalFiles)
                                {
                                    progressBar.setVisibility(View.GONE);
                                }
                            });
                        })
                        .addOnFailureListener(e ->
                        {
                            Toast.makeText(rootView.getContext(), "Data Upload Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                            filesUploaded.incrementAndGet();

                            // check if all files uploaded
                            if (filesUploaded.get() == totalFiles)
                            {
                                progressBar.setVisibility(View.GONE);
                            }
                        });
            }
        }
        else
        {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(rootView.getContext(), "Must login to upload files", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareData()
    {
        FileViewerAdapter friendsAdapter = new FileViewerAdapter(getContext(), fFriends);
        ArrayList<String> selectedFiles = friendsAdapter.collectSelected();

        boolean allUploaded = true;
        for (String selectedFile : selectedFiles)
        {
            for (FileDetail fileDetail : fFriends)
            {
                if (fileDetail.getFull().equals(selectedFile) && !fileDetail.isUploaded())
                {
                    allUploaded = false;
                    break;
                }
            }
        }

        if (!allUploaded)
        {
            Toast.makeText(getContext(), "Please upload all files before sharing", Toast.LENGTH_SHORT).show();
            return;
        }

        // create an alert dialog with an edit text field
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Enter Email Addresse(s)");
        builder.setMessage("User a comma(,) to seperate emails");

        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // set up the buttons
        builder.setPositiveButton("Share", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                String emails = input.getText().toString();
                if (!TextUtils.isEmpty(emails))
                {
                    String[] emailArray = emails.split(",");
                    updateMetadata(emailArray);
                }
                else
                {
                    Toast.makeText(getContext(), "Please enter at least one email", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void updateMetadata(String[] emails) {
        FileViewerAdapter friendsAdapter = new FileViewerAdapter(getContext(), fFriends);
        ArrayList<String> selectedFiles = friendsAdapter.collectSelected();

        for (String selectedFile : selectedFiles)
        {
            Uri fileUri = Uri.fromFile(new File(selectedFile));
            StorageReference storageRef = FirebaseStorage.getInstance().getReference();
            StorageReference fileRef = storageRef.child("Files/" + fileUri.getLastPathSegment());

            // get current metadata
            fileRef.getMetadata().addOnSuccessListener(metadata ->
            {
                String currentUser = metadata.getCustomMetadata("user");
                if (currentUser != null)
                {
                    // append new emails to the existing user metadata
                    StringBuilder newUserMetadata = new StringBuilder(currentUser);
                    for (String email : emails)
                    {
                        if (!newUserMetadata.toString().contains(email))
                        {
                            newUserMetadata.append(",").append(email);
                            Toast.makeText(getContext(), "Shared with " + email, Toast.LENGTH_SHORT).show();
                        }
                        else
                        {
                            Toast.makeText(getContext(), "Already shared with " + email, Toast.LENGTH_SHORT).show();
                        }
                    }

                    // update metadata
                    fileRef.updateMetadata(new StorageMetadata.Builder()
                                    .setCustomMetadata("user", newUserMetadata.toString())
                                    .build())
                            .addOnSuccessListener(aVoid ->
                            {
                                Toast.makeText(getContext(), "Updated metadata successfully", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                            {
                                Toast.makeText(getContext(), "Failed to update metadata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                }
            }).addOnFailureListener(e ->
            {
                Toast.makeText(getContext(), "Failed to get metadata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }



    private void setupDriveList(final File[] rootDirectories)
    {
        //final LinearLayout list = rootView.findViewById(R.id.drive_list);
        final LinearLayout list = null;

        if (list == null)
        {
            return;
        }

        list.removeAllViews();

        for (File file : rootDirectories)
        {
            final Button entry = new Button(getContext());
            entry.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            );
            entry.setText(file.getPath());
            entry.setOnClickListener(v ->
            {
                //executor.execute(() -> listItem(file));
                //setViewVisibility(R.id.drive_list, View.GONE);
            });
            list.addView(entry);
        }
        list.setVisibility(View.GONE);
    }


    private boolean checkPermission()
    {
        permissionManager.getPermission(
                READ_EXTERNAL_STORAGE,
                "Storage access is required",
                false
        );
        permissionManager.getPermission(
                WRITE_EXTERNAL_STORAGE,
                "Storage access is required",
                false
        );

        return permissionManager
                .havePermission(new String[] {
                        READ_EXTERNAL_STORAGE,
                        WRITE_EXTERNAL_STORAGE
                });
    }


    private boolean folderAccessible(final File folder)
    {
        try
        {
            return folder.canRead();
        }
        catch (SecurityException e)
        {
            return false;
        }
    }


    private void sort(final File[] items)
    {
        // for every item
        for (int i = 0; i < items.length; i++)
        {
            // j = for every next item
            for (int j = i + 1; j < items.length; j++)
            {
                // if larger than next
                if (
                    items[i].toString()
                            .compareToIgnoreCase(items[j].toString()) > 0
                ) {
                    File temp = items[i];
                    items[i] = items[j];
                    items[j] = temp;
                }
            }
        }
    }


    private void addDialog(final String dialog, final int textSize)
    {
        //addIdDialog(dialog, textSize, View.NO_ID);
        Log.d(TAG,dialog);
    }


    private void addDirectory(final File folder)
    {
       // addItem(getImageView(folderImage), folder);
        Log.d(TAG,folder.getName());

    }


    private void addItem(int iconID, File file)
    {
       // new Item(imageView, file);
        String fName= file.getName();
        if (fName.contains(".txf"))
        {
            iconID = 0;
        }
        fFriends.add(new FileDetail(
            file.getName(),
            file.getAbsolutePath(),
            iconID
        ));

        Log.d(TAG, file.getName());
    }


    private void AddDirName(String DirName)
    {
        fFriends.add(new FileDetail(DirName,R.drawable.folder));
    }


    private void AddFileName(String FileName)
    {
        fFriends.add(new FileDetail(FileName,R.drawable.file));
    }


    private ImageView getImageView(final Bitmap bitmap)
    {
        final ImageView imageView = new ImageView(getContext());
        imageView.setImageBitmap(bitmap);
        final int width10 = mywidth / 8;
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setMinimumWidth(width10);
        imageView.setMinimumHeight(width10);
        imageView.setMaxWidth(width10);
        imageView.setMaxHeight(width10);
        return imageView;
    }


    public static String getFileType(File file)
    {
        final int lastDot = file.getName().lastIndexOf('.');
        if (lastDot >= 0)
        {
            final String extension =
                    file.getName().substring(lastDot + 1);
            final String mime = MimeTypeMap
                    .getSingleton()
                    .getMimeTypeFromExtension(extension);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }


    private void listItem(final File folder)
    {
        Log.d(TAG, "ListItem");
        String info = "Name: " + folder.getName() + "\n";
        if (Build.VERSION.SDK_INT >= 9)
        {
            StatFs stat = new StatFs(folder.getPath());
            long bytesAvailable = Build.VERSION.SDK_INT >= 18 ?
                    stat.getBlockSizeLong() * stat.getAvailableBlocksLong() :
                    (long) stat.getBlockSize() * stat.getAvailableBlocks();
            info += "Available size: "
                    + FileOperation.getReadableMemorySize(bytesAvailable)
                    + "\n";
            if (Build.VERSION.SDK_INT >= 18)
            {
                bytesAvailable = stat.getTotalBytes();
                info += "Capacity size: "
                        + FileOperation.getReadableMemorySize(bytesAvailable)
                        + "\n";
            }
        }

        parent = folder.getParentFile();
        filePath = folder.getPath();

        if (folderAccessible(folder))
        {
            final File[] items = folder.listFiles();
            assert items != null;

            sort(items);

            if (items.length == 0)
            {
                addDialog("Empty folder!", 16);
            }
            else
            {
                String lastLetter = "";
                boolean hasFolders = false;

                for (File item : items)
                {
                    if (item.isDirectory())
                    {
                        if (!hasFolders)
                        {
                            addDialog("Folders:", 18);
                            hasFolders = true;
                        }
                        if (
                            item.getName()
                                    .substring(0, 1)
                                    .compareToIgnoreCase(lastLetter) > 0
                        ) {
                            lastLetter = item.getName()
                                    .substring(0, 1).toUpperCase();
                            addDialog(lastLetter, 16);
                        }
                        addDirectory(item);
                        AddDirName(item.getName());
                    }
                }

                lastLetter = "";
                boolean hasFiles = false;
                boolean showFile = false;
                for (File item : items)
                {
                    if (item.isFile())
                    {
                        if (!hasFiles)
                        {
                            addDialog("Files:", 18);
                            hasFiles = true;
                        }
                        if (item.getName()
                                .substring(0, 1)
                                .compareToIgnoreCase(lastLetter) > 0)
                        {
                            lastLetter = item.getName()
                                    .substring(0, 1)
                                    .toUpperCase();
                            addDialog(lastLetter, 16);
                        }

                        showFile = item.getName()
                                .contains(".csv"); //|| item.getName().contains(".zip");
                        if (!showFile)
                        {
                            continue;
                        }

                        switch (getFileType(item).split("/")[0])
                        {
                            case "image":
                                addItem(R.drawable.picture, item);
                                break;//addItem(getImageView(pictureImage), item);
                            case "video":
                                addItem(R.drawable.video, item);
                                break;   //addItem(getImageView(videoImage), item);
                            case "audio":
                                addItem(R.drawable.audio, item);
                                break;   //addItem(getImageView(audioImage), item);
                            case "application":
                            {
                                if (getFileType(item).contains("application/octet-stream"))
                                    addItem(R.drawable.unknown, item);//addItem(getImageView(unknownImage), item);
                                else
                                    addItem(R.drawable.archive, item);//addItem(getImageView(archiveImage), item);
                                break;
                            }
                            case "text":
                            {
                                // sem pujdou jenom csv soubory
                                addItem(R.drawable.file, item);
                                break;//addItem(getImageView(fileImage), item);
                            }
                            default:
                                addItem(R.drawable.unknown, item);
                                break; //addItem(getImageView(unknownImage), item);
                        }
                    }
                }
            }
        }
        else
        {
            if (
                filePath.contains("Android/data")
                || filePath.contains("Android/obb")
            ) {
                addDialog(
                    "For android 11 or higher, Android/data and"
                        + " Android/obb is refused access.\n",
                    16
                );
            }
            else
            {
                addDialog("Access Denied", 16);
            }
        }
    }

    @Override
    public void onStart()
    {
        Log.d("LIST", "Started...");
        super.onStart();

        fFriends.clear();

        File[] rootDirectories = FileOperation.getAllStorages(getContext());
        filePath = Constants.FILEDIR; //"/storage/emulated/0/Documents/";

        //File directory = new File("/storage/emulated/0/");
        //File[] rootDirectories = directory.listFiles();
        File rootDir = new File(filePath);
        if (rootDir.isFile())
        {
            rootDir = rootDir.getParentFile();
        }

        setupDriveList(rootDirectories);

        if (filePath != null)
        {
            if (checkPermission())
            {
                listItem(rootDir);
            }
        }
        else
        {
            for (File folder : rootDirectories)
            {
                if (checkPermission())
                {
                    listItem(folder);
                }

                break;
            }
        };

        loadFromStorage();
        loadAllFiles();

        /*
        executor.execute(() -> {
            
            if (filePath != null) {
                if (checkPermission())
                    listItem(new File(filePath));
            } else {
                for (File folder : rootDirectories) {
                    if (checkPermission()) listItem(folder);
                    break;
                }
            }
        });
        */
    }


    private List<FileDetail> setFiles(String path)
    {
        List<FileDetail> fil = new ArrayList<>();

        File directory = new File(path);
        File[] files = directory.listFiles();

        for (File pathname : files)
        {
            System.out.println(pathname);
        }

        return fil;
    };


    private List<FileDetail> setFriends()
    {
        String[] names = getResources().getStringArray(R.array.friends);
        int[] iconID = {
                R.drawable.ic_mood_white_24dp,
                R.drawable.ic_mood_bad_white_24dp,
                R.drawable.ic_sentiment_neutral_white_24dp,
                R.drawable.ic_sentiment_dissatisfied_white_24dp,
                R.drawable.ic_sentiment_satisfied_white_24dp,
                R.drawable.ic_sentiment_very_dissatisfied_white_24dp,
                R.drawable.ic_sentiment_very_satisfied_white_24dp,
        };
        List<FileDetail> friends = new ArrayList<>();

        for (int i = 0; i < names.length; i++)
        {
            friends.add(new FileDetail(names[i], iconID[i]));
        }

        return friends;
    }


    private void setupBitmaps()
    {
        mywidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        folderImage = BitmapFactory.decodeResource(
                getResources(), R.drawable.folder
        );
        fileImage = BitmapFactory.decodeResource(
                getResources(), R.drawable.file
        );
        archiveImage = BitmapFactory.decodeResource(
                getResources(), R.drawable.archive
        );
        audioImage = BitmapFactory.decodeResource(
                getResources(), R.drawable.audio
        );
        videoImage = BitmapFactory.decodeResource(
                getResources(), R.drawable.video
        );
        pictureImage = BitmapFactory.decodeResource(
                getResources(), R.drawable.picture
        );
        unknownImage = BitmapFactory.decodeResource(
                getResources(), R.drawable.unknown
        );
    }


    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        binding = null;
    }
}